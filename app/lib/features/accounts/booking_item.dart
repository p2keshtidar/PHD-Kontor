import '../../l10n/l10n.dart';

/// Ein Umsatz aus Sicht eines Kontos (/F120/). amountCents > 0 = Eingang.
class BookingItem {
  const BookingItem({
    required this.bookingId,
    required this.type,
    required this.description,
    required this.bookedAt,
    required this.amountCents,
    required this.counterpartyName,
    this.counterpartyIban,
    this.category,
    this.categoryLabel,
    this.accountId,
    this.note,
  });

  final int bookingId;
  final String type;
  final String description;
  final DateTime bookedAt;
  final int amountCents;
  final String counterpartyName;
  final String? counterpartyIban;

  /// Kategorie aus Sicht dieses Kontos (/F560/), z. B. LEBENSMITTEL
  final String? category;
  final String? categoryLabel;

  /// Konto, aus dessen Sicht der Umsatz geladen wurde (nötig zum Ändern der Kategorie)
  final int? accountId;

  /// eigene Notiz (nur für mich sichtbar)
  final String? note;

  bool get isIncoming => amountCents > 0;

  factory BookingItem.fromJson(Map<String, dynamic> json, {int? accountId}) => BookingItem(
        bookingId: (json['bookingId'] as num).toInt(),
        type: json['type'] as String,
        description: json['description'] as String,
        bookedAt: DateTime.parse(json['bookedAt'] as String),
        amountCents: (json['amountCents'] as num).toInt(),
        counterpartyName: (json['counterpartyName'] as String?) ?? '',
        counterpartyIban: json['counterpartyIban'] as String?,
        category: json['category'] as String?,
        categoryLabel: json['categoryLabel'] as String?,
        accountId: accountId,
        note: json['note'] as String?,
      );
}

/// Anzeigenamen der Buchungstypen.
const bookingTypeLabels = <String, String>{
  'INITIAL': 'Startguthaben',
  'TRANSFER': 'Überweisung',
  'INTERNAL': 'Umbuchung',
  'CARD': 'Kartenzahlung',
  'SAVING_RATE': 'Sparrate',
  'DEPOT': 'Depot',
  'INCOME': 'Einnahme',
  'DIRECT_DEBIT': 'Lastschrift',
  'LOAN_PAYOUT': 'Kreditauszahlung',
  'LOAN_RATE': 'Kreditrate',
  'LOAN_EXTRA': 'Sondertilgung',
  'ENTRY_INCOME': 'Eigene Einnahme',
  'ENTRY_EXPENSE': 'Eigene Ausgabe',
  'INTEREST': 'Zinsen',
};

String bookingTypeLabel(String type) => tr(bookingTypeLabels[type] ?? type);
