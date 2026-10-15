import 'package:dio/dio.dart';
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

  /// Umsätze im Filter als CSV-Text (UTF-8 mit BOM, höchstens 10.000 Zeilen).
  Future<String> exportCsv({String source = 'ALL', String? query, bool internal = true}) async {
    final res = await _api.dio.get<String>('/api/transactions/export',
        queryParameters: {
          'source': source,
          if (query != null && query.trim().isNotEmpty) 'q': query.trim(),
          'internal': internal,
        },
        options: Options(responseType: ResponseType.plain));
    return res.data ?? '';
  }

  Future<Duplicates> duplicates() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/transactions/duplicates');
    return Duplicates.fromJson(res.data!);
  }

  Future<void> dismissDuplicate(String key) =>
      _api.dio.post<void>('/api/transactions/duplicates/dismiss', data: {'key': key});

  /// Umsatz einer verbundenen Bank nicht mehr mitzählen (bleibt sichtbar).
  Future<void> excludeExternal(int transactionId) =>
      _api.dio.put<void>('/api/banking/transactions/$transactionId', data: {'excluded': true});
}

final duplicatesProvider =
    FutureProvider.autoDispose<Duplicates>((ref) => ref.watch(multiBankRepositoryProvider).duplicates());

final multiBankRepositoryProvider =
    Provider<MultiBankRepository>((ref) => MultiBankRepository(ref.watch(apiClientProvider)));

final bankProductsProvider =
    FutureProvider.autoDispose<BankProducts>((ref) => ref.watch(multiBankRepositoryProvider).products());
