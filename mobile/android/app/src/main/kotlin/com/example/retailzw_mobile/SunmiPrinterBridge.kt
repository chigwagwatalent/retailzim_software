package zw.co.retailzw.pos

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** SUNMI's in-device service is independent of Bluetooth pairing and permissions. */
class SunmiPrinterBridge(private val context: Context, messenger: BinaryMessenger) :
    MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "retailzw/sunmi_printer")
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val printing = AtomicBoolean(false)
    private var service: SunmiPrinterService? = null
    private var binding = false
    private var bound = false
    private var closed = false
    private val waiting = mutableListOf<(SunmiPrinterService?) -> Unit>()
    private val connectionTimeout = Runnable { finishBinding(null) }

    private val connection = object : InnerPrinterCallback() {
        override fun onConnected(printer: SunmiPrinterService) {
            main.post { if (!closed) { service = printer; finishBinding(printer) } }
        }
        override fun onDisconnected() {
            main.post { service = null; finishBinding(null) }
        }
    }

    init { channel.setMethodCallHandler(this) }

    private fun finishBinding(printer: SunmiPrinterService?) {
        main.removeCallbacks(connectionTimeout)
        binding = false
        val callbacks = waiting.toList()
        waiting.clear()
        callbacks.forEach { it(printer) }
    }

    private fun withPrinter(callback: (SunmiPrinterService?) -> Unit) {
        if (closed) { callback(null); return }
        service?.let { callback(it); return }
        waiting.add(callback)
        if (binding) return
        binding = true
        try {
            if (bound) {
                InnerPrinterManager.getInstance().unBindService(context, connection)
                bound = false
            }
            bound = InnerPrinterManager.getInstance().bindService(context, connection)
            if (!bound) finishBinding(null)
            else main.postDelayed(connectionTimeout, 4000)
        } catch (_: Exception) { finishBinding(null) }
    }

    private fun statusMessage(state: Int): String = when (state) {
        1, 8 -> "Ready · 58 mm receipt paper"
        2 -> "Built-in printer is initializing. Try again shortly."
        3 -> "Built-in printer communication error. Check the SUNMI printer service."
        4 -> "Built-in printer is out of paper. Load the receipt roll."
        5 -> "Built-in printer is too hot. Allow it to cool."
        6 -> "Close the built-in printer paper cover."
        7 -> "Built-in printer cutter fault. Check the printer."
        9 -> "Check the printer paper/black-mark setting."
        505 -> "No built-in printer is available."
        else -> "Built-in printer is not ready (status $state)."
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getInfo" -> withPrinter { printer ->
                if (printer == null) {
                    result.success(mapOf("available" to false, "detected" to (Build.MANUFACTURER.contains("sunmi", true) || Build.BRAND.contains("sunmi", true)), "model" to Build.MODEL, "message" to "SUNMI printer service is unavailable. Tap Refresh or restart the terminal.")); return@withPrinter
                }
                worker.execute {
                    try {
                        val available = InnerPrinterManager.getInstance().hasPrinter(printer)
                        val state = if (available) printer.updatePrinterState() else 505
                        val model = try { printer.getPrinterModal() } catch (_: Exception) { Build.MODEL }
                        main.post { result.success(mapOf("available" to available, "detected" to true, "ready" to (state == 1 || state == 8), "model" to model, "status" to state, "message" to statusMessage(state))) }
                    } catch (error: Exception) {
                        main.post { result.success(mapOf("available" to false, "detected" to (Build.MANUFACTURER.contains("sunmi", true) || Build.BRAND.contains("sunmi", true)), "model" to Build.MODEL, "message" to "Could not read the SUNMI printer service: ${error.message}. Tap Refresh.")) }
                    }
                }
            }
            "printRaw" -> {
                val bytes = call.argument<ByteArray>("bytes")
                if (bytes == null || bytes.isEmpty() || bytes.size > 131072) {
                    result.error("SUNMI_DATA", "Receipt is empty or too large.", null); return
                }
                if (!printing.compareAndSet(false, true)) {
                    result.error("SUNMI_BUSY", "A receipt is already being sent. Please wait.", null); return
                }
                val completed = AtomicBoolean(false)
                var timeout: Runnable? = null
                fun finish(error: String? = null) {
                    main.post {
                        if (completed.compareAndSet(false, true)) {
                            timeout?.let { main.removeCallbacks(it) }
                            printing.set(false)
                            if (error == null) result.success(true)
                            else result.error("SUNMI_PRINT", error, null)
                        }
                    }
                }
                timeout = Runnable { finish("Printer response timed out. Check whether the receipt printed before retrying.") }
                main.postDelayed(timeout!!, 30000)
                withPrinter { printer ->
                    if (printer == null) { finish("SUNMI built-in printer service is unavailable. Tap Refresh in Receipt Printer settings."); return@withPrinter }
                    worker.execute {
                        try {
                            if (completed.get()) return@execute
                            val state = printer.updatePrinterState()
                            if (state != 1 && state != 8) { finish(statusMessage(state)); return@execute }
                            printer.printerInit(null)
                            // Send once. Never retry or fall back after a possibly accepted receipt.
                            printer.sendRAWData(bytes, object : InnerResultCallback() {
                                override fun onRunResult(ok: Boolean) { finish(if (ok) null else "Built-in printer rejected the receipt. Check its paper and cover.") }
                                override fun onReturnString(value: String?) {}
                                override fun onRaiseException(code: Int, message: String?) { finish(message ?: "Built-in printer error $code") }
                                override fun onPrintResult(code: Int, message: String?) { finish(if (code == 0) null else message ?: "Built-in printer error $code") }
                            })
                        } catch (error: Exception) { finish("Built-in printer error: ${error.message}. Check the receipt before retrying.") }
                    }
                }
            }
            else -> result.notImplemented()
        }
    }

    fun close() {
        closed = true
        channel.setMethodCallHandler(null)
        service = null
        finishBinding(null)
        try { if (bound) InnerPrinterManager.getInstance().unBindService(context, connection) } catch (_: Exception) {}
        worker.shutdown()
    }
}
