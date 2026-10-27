package zw.co.retailzw.pos

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var sunmiPrinter: SunmiPrinterBridge? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        sunmiPrinter = SunmiPrinterBridge(applicationContext, flutterEngine.dartExecutor.binaryMessenger)
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        sunmiPrinter?.close()
        sunmiPrinter = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
