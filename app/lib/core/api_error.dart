import 'package:dio/dio.dart';

/// Wandelt einen Fehler in eine verständliche deutsche Meldung um.
String apiErrorMessage(Object error) {
  if (error is DioException) {
    final data = error.response?.data;
    if (data is Map) {
      final fieldErrors = data['fieldErrors'];
      if (fieldErrors is Map && fieldErrors.isNotEmpty) {
        return fieldErrors.values.join('\n');
      }
      final message = data['message'];
      if (message is String && message.isNotEmpty) return message;
    }
    switch (error.type) {
      case DioExceptionType.connectionError:
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.sendTimeout:
        return 'Server nicht erreichbar. Läuft das Backend?';
      default:
        break;
    }
  }
  return 'Unerwarteter Fehler. Bitte erneut versuchen.';
}
