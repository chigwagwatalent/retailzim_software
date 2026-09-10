import 'dart:ffi';
import 'dart:io';

// Run with the full path to the SQLite DLL extracted by the installer.
void main(List<String> args) {
  if (!Platform.isWindows || args.length != 1) {
    throw ArgumentError('Provide the installed sqlite3.dll path on Windows.');
  }
  final library = DynamicLibrary.open(args.single);
  final initialize = library
      .lookupFunction<Int32 Function(), int Function()>('sqlite3_initialize');
  final shutdown = library
      .lookupFunction<Int32 Function(), int Function()>('sqlite3_shutdown');
  final version = library.lookupFunction<Int32 Function(), int Function()>(
      'sqlite3_libversion_number');
  if (initialize() != 0) throw StateError('SQLite initialization failed.');
  stdout.writeln('PASS: packaged SQLite ${version()} loads and initializes.');
  if (shutdown() != 0) throw StateError('SQLite shutdown failed.');
}
