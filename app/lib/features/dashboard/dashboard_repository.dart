import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'dashboard_models.dart';

class DashboardRepository {
  DashboardRepository(this._api);

  final ApiClient _api;

  Future<Dashboard> load() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/dashboard');
    return Dashboard.fromJson(res.data!);
  }
}

final dashboardRepositoryProvider =
    Provider<DashboardRepository>((ref) => DashboardRepository(ref.watch(apiClientProvider)));

final dashboardProvider =
    FutureProvider.autoDispose<Dashboard>((ref) => ref.watch(dashboardRepositoryProvider).load());
