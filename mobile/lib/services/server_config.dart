import 'dart:io';

import 'package:http/http.dart' as http;

class ServerConfig {
  static const String productionBaseUrl = 'https://admin.retailzw.co.zw';
  static const String overrideBaseUrl =
      String.fromEnvironment('RETAILZW_API_BASE_URL');

  static const String baseUrl =
      overrideBaseUrl == '' ? productionBaseUrl : overrideBaseUrl;
  static String? _activeBaseUrl;
  static List<String> _candidateCache = [baseUrl];

  static List<String> get candidates => List.unmodifiable(_candidateCache);

  static Future<String> getBaseUrl() async {
    final active = _activeBaseUrl;
    if (active != null) return active;

    final installed = await _installedBaseUrl();
    final candidates = <String>{
      if (installed != null) installed,
      if (overrideBaseUrl.trim().isNotEmpty) _normalize(overrideBaseUrl),
      productionBaseUrl,
    };
    _candidateCache = candidates.toList(growable: false);
    for (final candidate in candidates) {
      if (await _isReachable(candidate)) {
        _activeBaseUrl = candidate;
        return candidate;
      }
    }
    return baseUrl;
  }

  static Future<String> activeBaseUrl() => getBaseUrl();

  static Future<String?> _installedBaseUrl() async {
    if (!Platform.isWindows && !Platform.isLinux && !Platform.isMacOS) {
      return null;
    }
    try {
      final executable = File(Platform.resolvedExecutable);
      final config = File(
          '${executable.parent.path}${Platform.pathSeparator}retailzw-server.txt');
      if (!await config.exists()) return null;
      final value = _normalize(await config.readAsString());
      final uri = Uri.tryParse(value);
      if (uri == null ||
          (uri.scheme != 'http' && uri.scheme != 'https') ||
          uri.host.isEmpty) {
        return null;
      }
      return value;
    } catch (_) {
      return null;
    }
  }

  static String _normalize(String value) {
    var normalized = value.trim();
    while (normalized.endsWith('/')) {
      normalized = normalized.substring(0, normalized.length - 1);
    }
    return normalized;
  }

  static Future<bool> _isReachable(String base) async {
    try {
      final response = await http
          .get(Uri.parse('$base/'))
          .timeout(const Duration(seconds: 5));
      return response.statusCode >= 200 && response.statusCode < 500;
    } catch (_) {
      return false;
    }
  }
}
