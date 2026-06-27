#include "flutter_window.h"

#include <optional>
#include <string>
#include <vector>

#include <windows.h>
#include <winspool.h>

#include "flutter/generated_plugin_registrant.h"

namespace {

std::wstring Utf8ToWide(const std::string& value) {
  if (value.empty()) {
    return std::wstring();
  }
  const int length = MultiByteToWideChar(CP_UTF8, 0, value.data(),
                                         static_cast<int>(value.size()),
                                         nullptr, 0);
  if (length <= 0) {
    return std::wstring();
  }
  std::wstring result(length, L'\0');
  MultiByteToWideChar(CP_UTF8, 0, value.data(),
                      static_cast<int>(value.size()), result.data(), length);
  return result;
}

std::string WindowsErrorMessage(const char* operation, DWORD error) {
  return std::string(operation) + " failed (Windows error " +
         std::to_string(error) + ")";
}

}  // namespace

FlutterWindow::FlutterWindow(const flutter::DartProject& project)
    : project_(project) {}

FlutterWindow::~FlutterWindow() {}

bool FlutterWindow::OnCreate() {
  if (!Win32Window::OnCreate()) {
    return false;
  }

  RECT frame = GetClientArea();

  // The size here must match the window dimensions to avoid unnecessary surface
  // creation / destruction in the startup path.
  flutter_controller_ = std::make_unique<flutter::FlutterViewController>(
      frame.right - frame.left, frame.bottom - frame.top, project_);
  // Ensure that basic setup of the controller was successful.
  if (!flutter_controller_->engine() || !flutter_controller_->view()) {
    return false;
  }
  RegisterPlugins(flutter_controller_->engine());

  printer_channel_ =
      std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
          flutter_controller_->engine()->messenger(),
          "retailzw/windows_printer",
          &flutter::StandardMethodCodec::GetInstance());
  printer_channel_->SetMethodCallHandler(
      [](const flutter::MethodCall<flutter::EncodableValue>& call,
         std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>>
             result) {
        if (call.method_name() != "printRaw") {
          result->NotImplemented();
          return;
        }

        const auto* arguments =
            std::get_if<flutter::EncodableMap>(call.arguments());
        if (arguments == nullptr) {
          result->Error("INVALID_ARGUMENTS", "Missing print arguments.");
          return;
        }

        const auto printer_it =
            arguments->find(flutter::EncodableValue("printerName"));
        const auto bytes_it = arguments->find(flutter::EncodableValue("bytes"));
        if (printer_it == arguments->end() || bytes_it == arguments->end()) {
          result->Error("INVALID_ARGUMENTS",
                        "printerName and bytes are required.");
          return;
        }

        const auto* printer_name_utf8 =
            std::get_if<std::string>(&printer_it->second);
        const auto* bytes = std::get_if<std::vector<uint8_t>>(&bytes_it->second);
        if (printer_name_utf8 == nullptr || printer_name_utf8->empty() ||
            bytes == nullptr || bytes->empty()) {
          result->Error("INVALID_ARGUMENTS",
                        "Printer name or receipt data is empty.");
          return;
        }

        std::string document_name_utf8 = "RetailZW receipt";
        const auto document_it =
            arguments->find(flutter::EncodableValue("documentName"));
        if (document_it != arguments->end()) {
          if (const auto* value =
                  std::get_if<std::string>(&document_it->second)) {
            document_name_utf8 = *value;
          }
        }

        const std::wstring printer_name = Utf8ToWide(*printer_name_utf8);
        std::wstring document_name = Utf8ToWide(document_name_utf8);
        if (printer_name.empty()) {
          result->Error("INVALID_PRINTER", "Invalid Windows printer name.");
          return;
        }

        HANDLE printer = nullptr;
        if (!OpenPrinterW(const_cast<LPWSTR>(printer_name.c_str()), &printer,
                          nullptr)) {
          const DWORD error = GetLastError();
          result->Error("OPEN_PRINTER_FAILED",
                        WindowsErrorMessage("OpenPrinter", error));
          return;
        }

        DOC_INFO_1W document_info{};
        document_info.pDocName = document_name.data();
        document_info.pOutputFile = nullptr;
        document_info.pDatatype = const_cast<LPWSTR>(L"RAW");

        const DWORD job_id = StartDocPrinterW(
            printer, 1, reinterpret_cast<LPBYTE>(&document_info));
        if (job_id == 0) {
          const DWORD error = GetLastError();
          ClosePrinter(printer);
          result->Error("START_DOCUMENT_FAILED",
                        WindowsErrorMessage("StartDocPrinter", error));
          return;
        }

        bool page_started = StartPagePrinter(printer) != FALSE;
        DWORD written = 0;
        bool write_ok = false;
        DWORD write_error = ERROR_SUCCESS;
        if (page_started) {
          write_ok = WritePrinter(printer,
                                  const_cast<uint8_t*>(bytes->data()),
                                  static_cast<DWORD>(bytes->size()), &written) !=
                     FALSE;
          if (!write_ok) {
            write_error = GetLastError();
          }
          EndPagePrinter(printer);
        } else {
          write_error = GetLastError();
        }
        EndDocPrinter(printer);
        ClosePrinter(printer);

        if (!page_started || !write_ok || written != bytes->size()) {
          result->Error(
              "WRITE_PRINTER_FAILED",
              WindowsErrorMessage("WritePrinter", write_error) +
                  "; wrote " + std::to_string(written) + " of " +
                  std::to_string(bytes->size()) + " bytes");
          return;
        }

        result->Success(flutter::EncodableValue(true));
      });
  SetChildContent(flutter_controller_->view()->GetNativeWindow());

  flutter_controller_->engine()->SetNextFrameCallback([&]() {
    this->Show();
  });

  // Flutter can complete the first frame before the "show window" callback is
  // registered. The following call ensures a frame is pending to ensure the
  // window is shown. It is a no-op if the first frame hasn't completed yet.
  flutter_controller_->ForceRedraw();

  return true;
}

void FlutterWindow::OnDestroy() {
  printer_channel_.reset();
  if (flutter_controller_) {
    flutter_controller_ = nullptr;
  }

  Win32Window::OnDestroy();
}

LRESULT
FlutterWindow::MessageHandler(HWND hwnd, UINT const message,
                              WPARAM const wparam,
                              LPARAM const lparam) noexcept {
  // Give Flutter, including plugins, an opportunity to handle window messages.
  if (flutter_controller_) {
    std::optional<LRESULT> result =
        flutter_controller_->HandleTopLevelWindowProc(hwnd, message, wparam,
                                                      lparam);
    if (result) {
      return *result;
    }
  }

  switch (message) {
    case WM_FONTCHANGE:
      flutter_controller_->engine()->ReloadSystemFonts();
      break;
  }

  return Win32Window::MessageHandler(hwnd, message, wparam, lparam);
}
