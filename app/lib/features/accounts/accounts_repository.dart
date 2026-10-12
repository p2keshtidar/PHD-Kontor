import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'account.dart';

class AccountsRepository {
  AccountsRepository(this._api);

  final ApiClient _api;

  Future<List<Account>> fetchAll() async {
    final res = await _api.dio.get<List<dynamic>>('/api/accounts');
    return res.data!
        .map((e) => Account.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}

final accountsRepositoryProvider =
    Provider<AccountsRepository>((ref) => AccountsRepository(ref.watch(apiClientProvider)));

/// Konten des angemeldeten Nutzers. Neu laden mit ref.invalidate(accountsProvider).
final accountsProvider = FutureProvider.autoDispose<List<Account>>(
  (ref) => ref.watch(accountsRepositoryProvider).fetchAll(),
);
