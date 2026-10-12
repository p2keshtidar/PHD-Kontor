import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'insights_models.dart';

class InsightsRepository {
  InsightsRepository(this._api);

  final ApiClient _api;

  Future<List<MonthSummary>> months({int count = 6}) async {
    final res = await _api.dio.get<List<dynamic>>('/api/insights/months', queryParameters: {'count': count});
    return res.data!.map((e) => MonthSummary.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<FinanceOverview> overview() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/insights/overview');
    return FinanceOverview.fromJson(res.data!);
  }

  Future<NetWorth> netWorth() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/insights/net-worth');
    return NetWorth.fromJson(res.data!);
  }

  Future<int> createSampleMonths(String profile) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/demo/sample-months', data: {'profile': profile});
    return (res.data!['bookings'] as num).toInt();
  }

  /// Simulationsdatum (/F950/)
  Future<DateTime> clock() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/clock');
    return DateTime.parse(res.data!['date'] as String);
  }

  /// Nur Administrator.
  Future<DateTime> advance({int? days, int? months}) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/admin/clock/advance', data: {
      if (days != null) 'days': days,
      if (months != null) 'months': months,
    });
    return DateTime.parse(res.data!['date'] as String);
  }

  Future<void> changeCategory(int accountId, int bookingId, String category) =>
      _api.dio.put<void>('/api/accounts/$accountId/bookings/$bookingId/category', data: {'category': category});
}

final insightsRepositoryProvider =
    Provider<InsightsRepository>((ref) => InsightsRepository(ref.watch(apiClientProvider)));

final monthsProvider =
    FutureProvider.autoDispose<List<MonthSummary>>((ref) => ref.watch(insightsRepositoryProvider).months());

final overviewProvider =
    FutureProvider.autoDispose<FinanceOverview>((ref) => ref.watch(insightsRepositoryProvider).overview());

final netWorthProvider =
    FutureProvider.autoDispose<NetWorth>((ref) => ref.watch(insightsRepositoryProvider).netWorth());

final clockProvider = FutureProvider.autoDispose<DateTime>((ref) => ref.watch(insightsRepositoryProvider).clock());
