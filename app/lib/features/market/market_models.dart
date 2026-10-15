/// Finanz-Marktplatz (nur simulierte Anbieter).
library;

int _i(Object? v) => (v as num).toInt();
int? _iOrNull(Object? v) => v == null ? null : (v as num).toInt();
List<String> _s(Object? v) => [for (final x in (v as List<dynamic>? ?? const [])) x as String];

class MarketProduct {
  const MarketProduct({
    required this.id,
    required this.category,
    required this.categoryLabel,
    required this.provider,
    required this.name,
    required this.monthlyFeeCents,
    required this.annualFeeCents,
    required this.requirements,
    required this.pros,
    required this.cons,
    required this.documents,
    required this.exampleCents,
    required this.exampleText,
    required this.eligible,
    required this.favorite,
    this.interestPercent,
    this.termMonths,
    this.minAmountCents,
    this.orderFeeCents,
  });

  final String id;
  final String category;
  final String categoryLabel;
  final String provider;
  final String name;
  final int monthlyFeeCents;
  final int annualFeeCents;
  final double? interestPercent;
  final int? termMonths;
  final int? minAmountCents;
  final int? orderFeeCents;
  final List<String> requirements;
  final List<String> pros;
  final List<String> cons;
  final List<String> documents;
  final int exampleCents;
  final String exampleText;
  final bool eligible;
  final bool favorite;

  factory MarketProduct.fromJson(Map<String, dynamic> j) => MarketProduct(
        id: j['id'] as String,
        category: j['category'] as String,
        categoryLabel: j['categoryLabel'] as String? ?? '',
        provider: j['provider'] as String,
        name: j['name'] as String,
        monthlyFeeCents: _i(j['monthlyFeeCents']),
        annualFeeCents: _i(j['annualFeeCents']),
        interestPercent: (j['interestPercent'] as num?)?.toDouble(),
        termMonths: _iOrNull(j['termMonths']),
        minAmountCents: _iOrNull(j['minAmountCents']),
        orderFeeCents: _iOrNull(j['orderFeeCents']),
        requirements: _s(j['requirements']),
        pros: _s(j['pros']),
        cons: _s(j['cons']),
        documents: _s(j['documents']),
        exampleCents: _i(j['exampleCents']),
        exampleText: j['exampleText'] as String? ?? '',
        eligible: j['eligible'] as bool? ?? true,
        favorite: j['favorite'] as bool? ?? false,
      );
}

class MarketCatalog {
  const MarketCatalog(this.category, this.amountCents, this.products, this.categories, this.disclaimer, this.sortText);

  final String category;
  final int amountCents;
  final List<MarketProduct> products;
  final Map<String, String> categories;
  final String disclaimer;
  final String sortText;

  factory MarketCatalog.fromJson(Map<String, dynamic> j) => MarketCatalog(
        j['category'] as String,
        _i(j['amountCents']),
        [for (final p in j['products'] as List<dynamic>) MarketProduct.fromJson(p as Map<String, dynamic>)],
        {for (final e in (j['categories'] as Map<String, dynamic>).entries) e.key: e.value as String},
        j['disclaimer'] as String? ?? '',
        j['sortExplanation'] as String? ?? '',
      );
}

class CompareRow {
  const CompareRow(this.label, this.values);

  final String label;
  final List<String> values;

  factory CompareRow.fromJson(Map<String, dynamic> j) => CompareRow(j['label'] as String, _s(j['values']));
}

class Comparison {
  const Comparison(this.products, this.rows, this.disclaimer);

  final List<MarketProduct> products;
  final List<CompareRow> rows;
  final String disclaimer;

  factory Comparison.fromJson(Map<String, dynamic> j) => Comparison(
        [for (final p in j['products'] as List<dynamic>) MarketProduct.fromJson(p as Map<String, dynamic>)],
        [for (final r in j['rows'] as List<dynamic>) CompareRow.fromJson(r as Map<String, dynamic>)],
        j['disclaimer'] as String? ?? '',
      );
}

class ApplicationDocument {
  const ApplicationDocument(this.key, this.label, this.provided);

  final String key;
  final String label;
  final bool provided;

  factory ApplicationDocument.fromJson(Map<String, dynamic> j) =>
      ApplicationDocument(j['key'] as String, j['label'] as String, j['provided'] as bool? ?? false);
}

class MarketApplication {
  const MarketApplication(this.id, this.productId, this.productName, this.provider, this.category, this.status,
      this.statusLabel, this.statusReason, this.note, this.documents, this.createdAt);

  final int id;
  final String productId;
  final String productName;
  final String provider;
  final String category;

  /// DOCUMENTS_MISSING, SUBMITTED, APPROVED, DECLINED, WITHDRAWN
  final String status;
  final String statusLabel;
  final String? statusReason;
  final String? note;
  final List<ApplicationDocument> documents;
  final DateTime createdAt;

  bool get isOpen => status == 'DOCUMENTS_MISSING' || status == 'SUBMITTED';

  factory MarketApplication.fromJson(Map<String, dynamic> j) => MarketApplication(
        _i(j['id']),
        j['productId'] as String,
        j['productName'] as String,
        j['provider'] as String,
        j['category'] as String,
        j['status'] as String,
        j['statusLabel'] as String? ?? '',
        j['statusReason'] as String?,
        j['note'] as String?,
        [for (final d in j['documents'] as List<dynamic>) ApplicationDocument.fromJson(d as Map<String, dynamic>)],
        DateTime.parse(j['createdAt'] as String),
      );
}
