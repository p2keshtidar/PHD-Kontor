/// Adresse des Backends. Standard: http://localhost:8080 (Browser, iOS-Simulator, Desktop).
///
/// Android-Emulator:  flutter run --dart-define=API_URL=http://10.0.2.2:8080
/// Echtes Handy:      flutter run --dart-define=API_URL=http://<IP-deines-PCs>:8080
class AppConfig {
  static const apiBaseUrl =
      String.fromEnvironment('API_URL', defaultValue: 'http://localhost:8080');
}
