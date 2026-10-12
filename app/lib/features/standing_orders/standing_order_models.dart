import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';

/// /K030/ Dauerauftrag
class StandingOrder {
  const StandingOrder({
    required this.id,
    required this.recipientName,
    required this.recipientIban,
    required this.amountCents,
    required this.reference,
    required this.executionDay,
    required this.intervalMonths,
    required this.intervalLabel,
    required this.status,
    this.endDate,
    this.nextExecutionDate,
    this.lastExecutedMonth,
  });

  final int id;
  final String recipientName;
  final String recipientIban;
  final int amountCents;
  final String reference;
  final int executionDay;
  final int intervalMonths;
  final String intervalLabel;

  /// ACTIVE, PAUSED oder ENDED
  final String status;
  final DateTime? endDate;
  final DateTime? nextExecutionDate;
  final String? lastExecutedMonth;

  bool get isPaused => status == 'PAUSED';

  factory StandingOrder.fromJson(Map<String, dynamic> j) => StandingOrder(
        id: (j['id'] as num).toInt(),
        recipientName: j['recipientName'] as String,
        recipientIban: j['recipientIban'] as String,
        amountCents: (j['amountCents'] as num).toInt(),
        reference: j['reference'] as String,
        executionDay: (j['executionDay'] as num).toInt(),
        intervalMonths: (j['intervalMonths'] as num).toInt(),
        intervalLabel: j['intervalLabel'] as String? ?? intervalLabels[(j['intervalMonths'] as num).toInt()] ?? '',
        status: j['status'] as String,
        endDate: j['endDate'] == null ? null : DateTime.parse(j['endDate'] as String),
        nextExecutionDate: j['nextExecutionDate'] == null ? null : DateTime.parse(j['nextExecutionDate'] as String),
        lastExecutedMonth: j['lastExecutedMonth'] as String?,
      );
}

/// Intervall in Monaten → Text
const intervalLabels = <int, String>{
  1: 'monatlich',
  3: 'vierteljährlich',
  6: 'halbjährlich',
  12: 'jährlich',
};

class StandingOrdersRepository {
  StandingOrdersRepository(this._api);

  final ApiClient _api;

  Future<List<StandingOrder>> list() async {
    final res = await _api.dio.get<List<dynamic>>('/api/standing-orders');
    return res.data!.map((e) => StandingOrder.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<StandingOrder> create({
    required String recipient,
    required int amountCents,
    required String reference,
    required int executionDay,
    required int intervalMonths,
    DateTime? endDate,
  }) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/standing-orders', data: {
      'recipient': recipient,
      'amountCents': amountCents,
      'reference': reference,
      'executionDay': executionDay,
      'intervalMonths': intervalMonths,
      if (endDate != null) 'endDate': _iso(endDate),
    });
    return StandingOrder.fromJson(res.data!);
  }

  Future<StandingOrder> update(int id, Map<String, dynamic> changes) async {
    final res = await _api.dio.put<Map<String, dynamic>>('/api/standing-orders/$id', data: changes);
    return StandingOrder.fromJson(res.data!);
  }

  Future<void> delete(int id) => _api.dio.delete<void>('/api/standing-orders/$id');
}

String _iso(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

final standingOrdersRepositoryProvider =
    Provider<StandingOrdersRepository>((ref) => StandingOrdersRepository(ref.watch(apiClientProvider)));

final standingOrdersProvider = FutureProvider.autoDispose<List<StandingOrder>>(
    (ref) => ref.watch(standingOrdersRepositoryProvider).list());
