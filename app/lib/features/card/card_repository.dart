import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'card_model.dart';

class CardRepository {
  CardRepository(this._api);

  final ApiClient _api;

  Future<PaymentCard> fetch() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/cards/me');
    return PaymentCard.fromJson(res.data!);
  }

  Future<CardDetails> details(String password) async {
    final res = await _api.dio
        .post<Map<String, dynamic>>('/api/cards/me/details', data: {'password': password});
    final d = res.data!;
    return CardDetails(d['number'] as String, d['cvc'] as String, d['expiry'] as String);
  }

  Future<PaymentCard> setLocked(bool locked) async {
    final res = await _api.dio
        .post<Map<String, dynamic>>(locked ? '/api/cards/me/lock' : '/api/cards/me/unlock');
    return PaymentCard.fromJson(res.data!);
  }

  Future<PaymentCard> setLimits(int dailyLimitCents, bool onlineEnabled) async {
    final res = await _api.dio.put<Map<String, dynamic>>('/api/cards/me/limits', data: {
      'dailyLimitCents': dailyLimitCents,
      'onlineEnabled': onlineEnabled,
    });
    return PaymentCard.fromJson(res.data!);
  }

  /// Händler-Simulator (/F340/)
  Future<CardPaymentResult> simulatePayment(String merchant, int amountCents, bool online) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/simulator/card-payment', data: {
      'merchant': merchant.trim(),
      'amountCents': amountCents,
      'online': online,
    });
    return CardPaymentResult.fromJson(res.data!);
  }
}

final cardRepositoryProvider =
    Provider<CardRepository>((ref) => CardRepository(ref.watch(apiClientProvider)));

final cardProvider =
    FutureProvider.autoDispose<PaymentCard>((ref) => ref.watch(cardRepositoryProvider).fetch());
