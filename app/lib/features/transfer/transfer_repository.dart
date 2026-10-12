import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'transfer_models.dart';

class TransferRepository {
  TransferRepository(this._api);

  final ApiClient _api;

  Map<String, dynamic> _body(String recipient, int amountCents, String description) => {
        'recipient': recipient.trim(),
        'amountCents': amountCents,
        'description': description.trim(),
      };

  /// Prüft die Überweisung, ohne zu buchen.
  Future<TransferCheck> check(String recipient, int amountCents, String description) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      '/api/transfers/check',
      data: _body(recipient, amountCents, description),
    );
    return TransferCheck.fromJson(res.data!);
  }

  /// Führt die Überweisung aus. Derselbe [idempotencyKey] bucht auch bei Wiederholung nur einmal.
  Future<TransferResult> execute(
      String recipient, int amountCents, String description, String idempotencyKey) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      '/api/transfers',
      data: _body(recipient, amountCents, description),
      options: Options(headers: {'Idempotency-Key': idempotencyKey}),
    );
    return TransferResult.fromJson(res.data!);
  }
}

final transferRepositoryProvider =
    Provider<TransferRepository>((ref) => TransferRepository(ref.watch(apiClientProvider)));
