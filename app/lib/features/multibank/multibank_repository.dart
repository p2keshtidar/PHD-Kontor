import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../accounts/account.dart';
import 'multibank_models.dart';

class MultiBankRepository {
  MultiBankRepository(this._api);

  final ApiClient _api;

  Future<BankProducts> products() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/accounts/products');
    return BankProducts.fromJson(res.data!);
  }

  Future<Account> open(String type, String? name) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/accounts', data: {
      'type': type,
      if (name != null && name.trim().isNotEmpty) 'name': name.trim(),
    });
    return Account.fromJson(res.data!);
  }

  Future<Account> rename(int id, String name) async {
    final res = await _api.dio.put<Map<String, dynamic>>('/api/accounts/$id', data: {'name': name.trim()});
    return Account.fromJson(res.data!);
  }

  Future<CloseResult> close(int id) async {
    final res = await _api.dio.delete<Map<String, dynamic>>('/api/accounts/$id');
    return CloseResult.fromJson(res.data!);
  }

  /// Umbuchen zwischen eigenen Konten der Projektbank (Girokonten, Tagesgeld, digitale Konten).
  Future<void> move(int fromId, int toId, int cents) => _api.dio.post<void>('/api/accounts/move',
      data: {'fromAccountId': fromId, 'toAccountId': toId, 'amountCents': cents});

  Future<TxPage> transactions({
    String source = 'ALL',
    String? query,
    bool internal = true,
    int page = 0,
    int size = 30,
  }) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/transactions', queryParameters: {
      'source': source,
      if (query != null && query.trim().isNotEmpty) 'q': query.trim(),
      'internal': internal,
      'page': page,
      'size': size,
    });
    return TxPage.fromJson(res.data!);
  }
}

final multiBankRepositoryProvider =
    Provider<MultiBankRepository>((ref) => MultiBankRepository(ref.watch(apiClientProvider)));

final bankProductsProvider =
    FutureProvider.autoDispose<BankProducts>((ref) => ref.watch(multiBankRepositoryProvider).products());
