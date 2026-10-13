import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/api_error.dart';

DioException _e(DioExceptionType t, {Object? data, int? status}) {
  final o = RequestOptions(path: '/x');
  return DioException(
    requestOptions: o,
    type: t,
    response: status == null ? null : Response(requestOptions: o, statusCode: status, data: data),
  );
}

void main() {
  test('Meldung des Servers hat Vorrang', () {
    expect(apiErrorMessage(_e(DioExceptionType.badResponse, status: 400, data: {'message': 'Zu wenig Guthaben.'})),
        'Zu wenig Guthaben.');
  });

  test('Feldfehler werden zeilenweise angezeigt', () {
    expect(
        apiErrorMessage(_e(DioExceptionType.badResponse,
            status: 400, data: {'fieldErrors': {'email': 'E-Mail ist ungültig.', 'phone': 'Telefonnummer ist ungültig.'}})),
        'E-Mail ist ungültig.\nTelefonnummer ist ungültig.');
  });

  test('keine Verbindung und langsame Antwort werden unterschieden', () {
    expect(apiErrorMessage(_e(DioExceptionType.connectionError)), contains('nicht erreichbar'));
    expect(apiErrorMessage(_e(DioExceptionType.connectionTimeout)), contains('nicht erreichbar'));
    expect(apiErrorMessage(_e(DioExceptionType.receiveTimeout)), contains('antwortet gerade nicht'));
  });

  test('unbekannter Fehler', () {
    expect(apiErrorMessage(Exception('x')), startsWith('Unerwarteter Fehler'));
  });

  test('Fehlercode', () {
    expect(apiErrorCode(_e(DioExceptionType.badResponse, status: 401, data: {'code': 'TOTP_REQUIRED'})), 'TOTP_REQUIRED');
    expect(apiErrorCode(Exception('x')), isNull);
  });
}
