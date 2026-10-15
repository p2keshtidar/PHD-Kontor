import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/api_client.dart';
import '../insights/insights_repository.dart';
import 'bank_models.dart';

/// Verbundene Bankkonten (Open Banking/PSD2, nur Lesezugriff).
class BankRepository {
  BankRepository(this._api);

  final ApiClient _api;

  Future<List<BankProvider>> providers() async {
    final res = await _api.dio.get<List<dynamic>>('/api/banking/providers');
    return [for (final p in res.data!) BankProvider.fromJson(p as Map<String, dynamic>)];
  }

  Future<List<Institution>> institutions(String provider) async {
    final res = await _api.dio
        .get<List<dynamic>>('/api/banking/institutions', queryParameters: {'provider': provider, 'country': 'DE'});
    return [for (final i in res.data!) Institution.fromJson(i as Map<String, dynamic>)];
  }

  Future<List<BankConnection>> connections() async {
    final res = await _api.dio.get<List<dynamic>>('/api/banking/connections');
    return [for (final c in res.data!) BankConnection.fromJson(c as Map<String, dynamic>)];
  }

  Future<BankOverview> overview() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/banking/accounts');
    return BankOverview.fromJson(res.data!);
  }

  Future<ConnectStart> connect(String provider, String institutionId) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/banking/connections',
        data: {'provider': provider, 'institutionId': institutionId});
    return ConnectStart.fromJson(res.data!);
  }

  Future<ConnectStart> renew(int connectionId) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/banking/connections/$connectionId/renew');
    return ConnectStart.fromJson(res.data!);
  }

  /// Nach der Freigabe bei der Bank. [approve] nur bei der Sandbox (simulierte Entscheidung).
  Future<(BankConnection, String)> complete(int connectionId, {bool? approve}) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/banking/connections/$connectionId/complete',
        data: {if (approve != null) 'approve': approve});
    return (
      BankConnection.fromJson(res.data!['connection'] as Map<String, dynamic>),
      res.data!['resultMessage'] as String? ?? '',
    );
  }

  Future<SyncResult> sync(int connectionId) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/banking/connections/$connectionId/sync');
    return SyncResult.fromJson(res.data!);
  }

  Future<String> revoke(int connectionId, {required bool deleteData}) async {
    final res = await _api.dio.delete<Map<String, dynamic>>('/api/banking/connections/$connectionId',
        queryParameters: {'deleteData': deleteData});
    return res.data!['resultMessage'] as String? ?? '';
  }

  Future<BankTransactionPage> transactions(BankTxFilter filter, int page) async {
    final res = await _api.dio
        .get<Map<String, dynamic>>('/api/banking/transactions', queryParameters: filter.toQuery(page));
    return BankTransactionPage.fromJson(res.data!);
  }

  Future<BankTransaction> transaction(int id) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/banking/transactions/$id');
    return BankTransaction.fromJson(res.data!);
  }

  /// Eigene Bearbeitung; null = unverändert, '' bzw. [] = löschen.
  Future<BankTransaction> update(int id,
      {String? category, String? note, List<String>? tags, bool? excluded}) async {
    final res = await _api.dio.put<Map<String, dynamic>>('/api/banking/transactions/$id', data: {
      if (category != null) 'category': category,
      if (note != null) 'note': note,
      if (tags != null) 'tags': tags,
      if (excluded != null) 'excluded': excluded,
    });
    return BankTransaction.fromJson(res.data!);
  }
}

final bankRepositoryProvider = Provider<BankRepository>((ref) => BankRepository(ref.watch(apiClientProvider)));

final bankOverviewProvider =
    FutureProvider.autoDispose<BankOverview>((ref) => ref.watch(bankRepositoryProvider).overview());

final bankConnectionsProvider =
    FutureProvider.autoDispose<List<BankConnection>>((ref) => ref.watch(bankRepositoryProvider).connections());

final bankProvidersProvider =
    FutureProvider.autoDispose<List<BankProvider>>((ref) => ref.watch(bankRepositoryProvider).providers());

final institutionsProvider = FutureProvider.autoDispose.family<List<Institution>, String>(
    (ref, provider) => ref.watch(bankRepositoryProvider).institutions(provider));

/// Öffnet die Seite der Bank im Browser (in Tests ersetzbar).
final linkOpenerProvider = Provider<Future<bool> Function(Uri)>(
    (ref) => (uri) => launchUrl(uri, mode: LaunchMode.externalApplication));

/// Nach Verbinden, Abruf, Widerruf oder Bearbeitung: alles neu laden, was Bankdaten zeigt.
void refreshBanking(WidgetRef ref) {
  ref.invalidate(bankOverviewProvider);
  ref.invalidate(bankConnectionsProvider);
  ref.invalidate(monthsProvider);
  ref.invalidate(overviewProvider);
}
