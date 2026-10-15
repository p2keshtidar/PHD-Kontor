/// Weitere Konten der Projektbank (Simulation) und alle Umsätze an einem Ort.
library;

class BankProduct {
  const BankProduct(this.type, this.label, this.description, this.interestPercent);

  /// CURRENT oder CALL
  final String type;
  final String label;
  final String description;

  /// fiktiver Zinssatz der Simulation (nur Tagesgeld)
  final double? interestPercent;

  factory BankProduct.fromJson(Map<String, dynamic> j) => BankProduct(j['type'] as String, j['label'] as String,
      j['description'] as String, (j['interestPercent'] as num?)?.toDouble());
}

class BankProducts {
  const BankProducts(this.products, this.openCount, this.maxCount, this.canOpen, this.disclaimer);

  final List<BankProduct> products;
  final int openCount;
  final int maxCount;
  final bool canOpen;
  final String disclaimer;

  factory BankProducts.fromJson(Map<String, dynamic> j) => BankProducts(
        [for (final p in j['products'] as List<dynamic>) BankProduct.fromJson(p as Map<String, dynamic>)],
        (j['openCount'] as num).toInt(),
        (j['maxCount'] as num).toInt(),
        j['canOpen'] as bool,
        j['disclaimer'] as String? ?? '',
      );
}

class CloseResult {
  const CloseResult(this.interestCents, this.movedCents, this.message);

  final int interestCents;
  final int movedCents;
  final String message;

  factory CloseResult.fromJson(Map<String, dynamic> j) => CloseResult(
      (j['interestCents'] as num).toInt(), (j['movedCents'] as num).toInt(), j['message'] as String? ?? '');
}

/// Ein Umsatz in „Alle Umsätze“.
class TxItem {
  const TxItem({
    required this.source,
    required this.id,
    required this.accountId,
    required this.accountName,
    required this.date,
    required this.amountCents,
    required this.currency,
    required this.description,
    required this.category,
    required this.categoryLabel,
    required this.status,
    required this.internal,
    this.institution,
    this.bookedAt,
    this.counterpartyName,
    this.excluded = false,
    this.note,
  });

  /// PROJEKTBANK (Simulation) oder EXTERNAL (verbundene Bank, nur lesend)
  final String source;
  final int id;
  final int accountId;
  final String accountName;
  final String? institution;
  final DateTime date;
  final DateTime? bookedAt;
  final int amountCents;
  final String currency;
  final String? counterpartyName;
  final String? description;
  final String category;
  final String categoryLabel;

  /// BOOKED oder PENDING
  final String status;
  final bool internal;
  final bool excluded;

  /// eigene Notiz
  final String? note;

  bool get isExternal => source == 'EXTERNAL';
  bool get isImport => source == 'IMPORT';
  bool get isPending => status == 'PENDING';

  /// Was in der Liste groß steht: Gegenpartei, sonst Verwendungszweck
  String get title {
    final c = counterpartyName;
    if (c != null && c.isNotEmpty) return c;
    final d = description;
    return d == null || d.isEmpty ? categoryLabel : d;
  }

  factory TxItem.fromJson(Map<String, dynamic> j) => TxItem(
        source: j['source'] as String,
        id: (j['id'] as num).toInt(),
        accountId: (j['accountId'] as num).toInt(),
        accountName: j['accountName'] as String? ?? '',
        institution: j['institution'] as String?,
        date: DateTime.parse(j['date'] as String),
        bookedAt: j['bookedAt'] == null ? null : DateTime.parse(j['bookedAt'] as String),
        amountCents: (j['amountCents'] as num).toInt(),
        currency: j['currency'] as String? ?? 'EUR',
        counterpartyName: j['counterpartyName'] as String?,
        description: j['description'] as String?,
        category: j['category'] as String? ?? 'SONSTIGES',
        categoryLabel: j['categoryLabel'] as String? ?? '',
        status: j['status'] as String? ?? 'BOOKED',
        internal: j['internal'] as bool? ?? false,
        excluded: j['excluded'] as bool? ?? false,
        note: j['note'] as String?,
      );
}

/// Zwei Umsätze, die vielleicht dasselbe sind.
class DuplicatePair {
  const DuplicatePair(this.key, this.kind, this.reason, this.first, this.second);

  final String key;

  /// SAME_ACCOUNT (zweimal auf demselben Konto) oder CROSS_SOURCE (an zwei Stellen erfasst)
  final String kind;
  final String reason;
  final TxItem first;
  final TxItem second;

  factory DuplicatePair.fromJson(Map<String, dynamic> j) => DuplicatePair(
        j['key'] as String,
        j['kind'] as String,
        j['reason'] as String? ?? '',
        TxItem.fromJson(j['first'] as Map<String, dynamic>),
        TxItem.fromJson(j['second'] as Map<String, dynamic>),
      );
}

class Duplicates {
  const Duplicates(this.pairs, this.explanation);

  final List<DuplicatePair> pairs;
  final String explanation;

  factory Duplicates.fromJson(Map<String, dynamic> j) => Duplicates(
        [for (final p in j['pairs'] as List<dynamic>) DuplicatePair.fromJson(p as Map<String, dynamic>)],
        j['explanation'] as String? ?? '',
      );
}

class TxPage {
  const TxPage(this.items, this.hasMore, this.incomeCents, this.expenseCents, this.notes);

  final List<TxItem> items;
  final bool hasMore;
  final int incomeCents;
  final int expenseCents;
  final List<String> notes;

  factory TxPage.fromJson(Map<String, dynamic> j) => TxPage(
        [for (final i in j['items'] as List<dynamic>) TxItem.fromJson(i as Map<String, dynamic>)],
        j['hasMore'] as bool? ?? false,
        (j['incomeCents'] as num?)?.toInt() ?? 0,
        (j['expenseCents'] as num?)?.toInt() ?? 0,
        [for (final n in (j['notes'] as List<dynamic>? ?? const [])) n as String],
      );
}
