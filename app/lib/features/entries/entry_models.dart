import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../accounts/accounts_repository.dart';
import '../accounts/bookings_repository.dart';
import '../insights/insights_repository.dart';
import '../notifications/notifications_repository.dart';

/// Eigene Einnahme oder Ausgabe (Gehalt, Miete, Urlaub …), einmalig oder regelmäßig.
class Entry {
  const Entry({
    required this.id,
    required this.kind,
    required this.title,
    required this.category,
    required this.categoryLabel,
    required this.amountCents,
    required this.intervalMonths,
    required this.intervalLabel,
    required this.startDate,
    required this.status,
    required this.bookedCount,
    required this.monthlyCents,
    this.endDate,
    this.nextDate,
    this.lastBookedDate,
  });

  final int id;

  /// INCOME oder EXPENSE
  final String kind;
  final String title;
  final String category;
  final String categoryLabel;
  final int amountCents;

  /// 0 = einmalig, sonst alle n Monate
  final int intervalMonths;
  final String intervalLabel;
  final DateTime startDate;
  final DateTime? endDate;

  /// ACTIVE, PAUSED, DONE
  final String status;
  final DateTime? nextDate;
  final DateTime? lastBookedDate;
  final int bookedCount;

  /// auf einen Monat umgerechnet, + Einnahme / − Ausgabe (einmalig: 0)
  final int monthlyCents;

  bool get isIncome => kind == 'INCOME';
  bool get isOnce => intervalMonths == 0;
  bool get isPaused => status == 'PAUSED';
  bool get isDone => status == 'DONE';

  /// Betrag mit Vorzeichen (+ Einnahme, − Ausgabe)
  int get signedCents => isIncome ? amountCents : -amountCents;

  factory Entry.fromJson(Map<String, dynamic> j) {
    DateTime? d(String k) => j[k] == null ? null : DateTime.parse(j[k] as String);
    final interval = (j['intervalMonths'] as num?)?.toInt() ?? 0;
    return Entry(
      id: (j['id'] as num).toInt(),
      kind: j['kind'] as String,
      title: j['title'] as String,
      category: j['category'] as String? ?? 'SONSTIGES',
      categoryLabel: j['categoryLabel'] as String? ?? '',
      amountCents: (j['amountCents'] as num).toInt(),
      intervalMonths: interval,
      intervalLabel: j['intervalLabel'] as String? ?? entryIntervals[interval] ?? '',
      startDate: DateTime.parse(j['startDate'] as String),
      endDate: d('endDate'),
      status: j['status'] as String? ?? 'ACTIVE',
      nextDate: d('nextDate'),
      lastBookedDate: d('lastBookedDate'),
      bookedCount: (j['bookedCount'] as num?)?.toInt() ?? 0,
      monthlyCents: (j['monthlyCents'] as num?)?.toInt() ?? 0,
    );
  }
}

/// Häufigkeiten: Monate → Text (0 = einmalig)
const entryIntervals = <int, String>{
  0: 'einmalig',
  1: 'monatlich',
  3: 'vierteljährlich',
  6: 'halbjährlich',
  12: 'jährlich',
};

class EntriesSummary {
  const EntriesSummary({
    required this.monthlyIncomeCents,
    required this.monthlyExpenseCents,
    required this.monthlyBalanceCents,
    required this.entries,
  });

  final int monthlyIncomeCents;
  final int monthlyExpenseCents;
  final int monthlyBalanceCents;
  final List<Entry> entries;

  List<Entry> get recurring => entries.where((e) => !e.isOnce).toList();
  List<Entry> get once => entries.where((e) => e.isOnce).toList();

  factory EntriesSummary.fromJson(Map<String, dynamic> j) => EntriesSummary(
        monthlyIncomeCents: (j['monthlyIncomeCents'] as num?)?.toInt() ?? 0,
        monthlyExpenseCents: (j['monthlyExpenseCents'] as num?)?.toInt() ?? 0,
        monthlyBalanceCents: (j['monthlyBalanceCents'] as num?)?.toInt() ?? 0,
        entries: [
          for (final e in (j['entries'] as List<dynamic>? ?? const [])) Entry.fromJson(e as Map<String, dynamic>)
        ],
      );
}

class EntryCategory {
  const EntryCategory(this.key, this.label, this.kind);

  final String key;
  final String label;

  /// INCOME oder EXPENSE
  final String kind;

  factory EntryCategory.fromJson(Map<String, dynamic> j) =>
      EntryCategory(j['key'] as String, j['label'] as String, j['kind'] as String);
}

/// Ergebnis beim Anlegen: wie viele Termine sofort gebucht wurden.
class EntryCreateResult {
  const EntryCreateResult(this.entry, this.bookedNow, this.bookedNowCents);

  final Entry entry;
  final int bookedNow;
  final int bookedNowCents;

  factory EntryCreateResult.fromJson(Map<String, dynamic> j) => EntryCreateResult(
        Entry.fromJson(j['entry'] as Map<String, dynamic>),
        (j['bookedNow'] as num?)?.toInt() ?? 0,
        (j['bookedNowCents'] as num?)?.toInt() ?? 0,
      );
}

/// Schnellauswahl im Formular: Bezeichnung und passende Kategorie
class EntrySuggestion {
  const EntrySuggestion(this.title, this.category, this.interval);

  final String title;
  final String category;
  final int interval;
}

const incomeSuggestions = [
  EntrySuggestion('Gehalt', 'GEHALT', 1),
  EntrySuggestion('Nebenjob', 'GEHALT', 1),
  EntrySuggestion('BAföG', 'SONSTIGE_EINNAHMEN', 1),
  EntrySuggestion('Kindergeld', 'SONSTIGE_EINNAHMEN', 1),
  EntrySuggestion('Geldgeschenk', 'SONSTIGE_EINNAHMEN', 0),
];

const expenseSuggestions = [
  EntrySuggestion('Miete', 'WOHNEN', 1),
  EntrySuggestion('Strom', 'WOHNEN', 1),
  EntrySuggestion('Handyvertrag', 'VERTRAEGE', 1),
  EntrySuggestion('Versicherung', 'VERTRAEGE', 12),
  EntrySuggestion('Einkauf', 'LEBENSMITTEL', 0),
  EntrySuggestion('Urlaub', 'FREIZEIT', 0),
  EntrySuggestion('Tanken', 'MOBILITAET', 0),
];

String _iso(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

class EntriesRepository {
  EntriesRepository(this._api);

  final ApiClient _api;

  Future<EntriesSummary> list() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/entries');
    return EntriesSummary.fromJson(res.data!);
  }

  Future<List<EntryCategory>> categories() async {
    final res = await _api.dio.get<List<dynamic>>('/api/entries/categories');
    return res.data!.map((e) => EntryCategory.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<EntryCreateResult> create({
    required String kind,
    required String title,
    required int amountCents,
    required int intervalMonths,
    required DateTime date,
    String? category,
    DateTime? endDate,
  }) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/entries', data: {
      'kind': kind,
      'title': title,
      'amountCents': amountCents,
      'intervalMonths': intervalMonths,
      'date': _iso(date),
      if (category != null) 'category': category,
      if (endDate != null && intervalMonths > 0) 'endDate': _iso(endDate),
    });
    return EntryCreateResult.fromJson(res.data!);
  }

  Future<Entry> update(int id, Map<String, dynamic> changes) async {
    final res = await _api.dio.put<Map<String, dynamic>>('/api/entries/$id', data: changes);
    return Entry.fromJson(res.data!);
  }

  Future<void> delete(int id) => _api.dio.delete<void>('/api/entries/$id');
}

final entriesRepositoryProvider =
    Provider<EntriesRepository>((ref) => EntriesRepository(ref.watch(apiClientProvider)));

final entriesProvider =
    FutureProvider.autoDispose<EntriesSummary>((ref) => ref.watch(entriesRepositoryProvider).list());

final entryCategoriesProvider =
    FutureProvider.autoDispose<List<EntryCategory>>((ref) => ref.watch(entriesRepositoryProvider).categories());

/// Nach dem Anlegen/Ändern: alles neu laden, was die Buchungen zeigt.
void refreshAfterEntryChange(WidgetRef ref) {
  ref.invalidate(entriesProvider);
  ref.invalidate(accountsProvider);
  ref.invalidate(recentBookingsProvider);
  ref.invalidate(netWorthProvider);
  ref.invalidate(monthsProvider);
  ref.invalidate(overviewProvider);
  ref.invalidate(unreadCountProvider);
}
