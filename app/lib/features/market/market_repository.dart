import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'market_models.dart';

class MarketRepository {
  MarketRepository(this._api);

  final ApiClient _api;

  Future<MarketCatalog> catalog(String category, {int? amountCents}) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/market/products',
        queryParameters: {'category': category, if (amountCents != null) 'amountCents': amountCents});
    return MarketCatalog.fromJson(res.data!);
  }

  Future<Comparison> compare(List<String> ids, {int? amountCents}) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/market/compare',
        queryParameters: {'ids': ids.join(','), if (amountCents != null) 'amountCents': amountCents});
    return Comparison.fromJson(res.data!);
  }

  Future<void> favorite(String id, bool on) =>
      on ? _api.dio.put<void>('/api/market/favorites/$id') : _api.dio.delete<void>('/api/market/favorites/$id');

  Future<MarketApplication> apply(String productId) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/market/applications',
        data: {'productId': productId, 'consent': true});
    return MarketApplication.fromJson(res.data!);
  }

  Future<List<MarketApplication>> applications() async {
    final res = await _api.dio.get<List<dynamic>>('/api/market/applications');
    return [for (final a in res.data!) MarketApplication.fromJson(a as Map<String, dynamic>)];
  }

  Future<MarketApplication> provide(int id, String docKey) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/market/applications/$id/documents/$docKey');
    return MarketApplication.fromJson(res.data!);
  }

  Future<MarketApplication> withdraw(int id) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/market/applications/$id/withdraw');
    return MarketApplication.fromJson(res.data!);
  }

  Future<MarketApplication> note(int id, String note) async {
    final res = await _api.dio.put<Map<String, dynamic>>('/api/market/applications/$id/note', data: {'note': note});
    return MarketApplication.fromJson(res.data!);
  }
}

final marketRepositoryProvider = Provider<MarketRepository>((ref) => MarketRepository(ref.watch(apiClientProvider)));

final marketApplicationsProvider =
    FutureProvider.autoDispose<List<MarketApplication>>((ref) => ref.watch(marketRepositoryProvider).applications());
