/// Zusammenfassung vor dem Absenden (/F240/).
class TransferCheck {
  const TransferCheck({
    required this.recipientName,
    required this.recipientIban,
    required this.amountCents,
    required this.description,
    required this.availableCents,
    required this.remainingDailyLimitCents,
  });

  final String recipientName;
  final String recipientIban;
  final int amountCents;
  final String description;
  final int availableCents;
  final int remainingDailyLimitCents;

  factory TransferCheck.fromJson(Map<String, dynamic> j) => TransferCheck(
        recipientName: j['recipientName'] as String,
        recipientIban: j['recipientIban'] as String,
        amountCents: (j['amountCents'] as num).toInt(),
        description: j['description'] as String,
        availableCents: (j['availableCents'] as num).toInt(),
        remainingDailyLimitCents: (j['remainingDailyLimitCents'] as num).toInt(),
      );
}

/// Ergebnis einer ausgeführten Überweisung (/F250/).
class TransferResult {
  const TransferResult({
    required this.bookingId,
    required this.recipientName,
    required this.recipientIban,
    required this.recipientUserId,
    required this.amountCents,
    required this.description,
    required this.bookedAt,
    required this.newBalanceCents,
  });

  final int bookingId;
  final String recipientName;
  final String recipientIban;
  final int recipientUserId;
  final int amountCents;
  final String description;
  final DateTime bookedAt;
  final int newBalanceCents;

  factory TransferResult.fromJson(Map<String, dynamic> j) => TransferResult(
        bookingId: (j['bookingId'] as num).toInt(),
        recipientName: j['recipientName'] as String,
        recipientIban: j['recipientIban'] as String,
        recipientUserId: (j['recipientUserId'] as num).toInt(),
        amountCents: (j['amountCents'] as num).toInt(),
        description: j['description'] as String,
        bookedAt: DateTime.parse(j['bookedAt'] as String),
        newBalanceCents: (j['newBalanceCents'] as num).toInt(),
      );
}
