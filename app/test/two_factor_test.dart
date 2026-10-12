import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/api_error.dart';
import 'package:girokonto_app/features/auth/app_user.dart';
import 'package:girokonto_app/features/profile/two_factor_screen.dart';

/// /K050/ Zwei-Faktor-Anmeldung
void main() {
  final base = {
    'id': 1, 'email': 'e@x.de', 'firstName': 'Erika', 'lastName': 'Muster',
    'phone': '0151', 'birthDate': '1999-01-01', 'role': 'USER',
  };

  test('Nutzer: 2FA-Status, Standard aus', () {
    expect(AppUser.fromJson(base).twoFactorEnabled, isFalse);
    expect(AppUser.fromJson({...base, 'twoFactorEnabled': true}).twoFactorEnabled, isTrue);
  });

  test('Schlüssel in Vierergruppen', () {
    expect(groupSecret('ABCDEFGHIJ'), 'ABCD EFGH IJ');
  });

  test('Fehlercode TOTP_REQUIRED wird erkannt', () {
    final req = RequestOptions(path: '/api/auth/login');
    final e = DioException(
      requestOptions: req,
      response: Response(requestOptions: req, statusCode: 401, data: {
        'code': 'TOTP_REQUIRED',
        'message': 'Bitte den 6-stelligen Code aus deiner Authenticator-App eingeben.',
      }),
    );
    expect(apiErrorCode(e), 'TOTP_REQUIRED');
    expect(apiErrorMessage(e), contains('6-stelligen Code'));
    expect(apiErrorCode(Exception('x')), isNull);
  });
}
