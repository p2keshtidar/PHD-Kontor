/// Virtuelle Karte (/F310/).
class PaymentCard {
  const PaymentCard({
    required this.id,
    required this.holderName,
    required this.maskedNumber,
    required this.last4,
    required this.expiry,
    required this.status,
    required this.dailyLimitCents,
    required this.maxDailyLimitCents,
    required this.onlineEnabled,
    required this.spentTodayCents,
  });

  final int id;
  final String holderName;
  final String maskedNumber;
  final String last4;
  final String expiry;
  final String status;
  final int dailyLimitCents;
  final int maxDailyLimitCents;
  final bool onlineEnabled;
  final int spentTodayCents;

  bool get isLocked => status == 'LOCKED';

  factory PaymentCard.fromJson(Map<String, dynamic> j) => PaymentCard(
        id: (j['id'] as num).toInt(),
        holderName: j['holderName'] as String,
        maskedNumber: j['maskedNumber'] as String,
        last4: j['last4'] as String,
        expiry: j['expiry'] as String,
        status: j['status'] as String,
        dailyLimitCents: (j['dailyLimitCents'] as num).toInt(),
        maxDailyLimitCents: (j['maxDailyLimitCents'] as num).toInt(),
        onlineEnabled: j['onlineEnabled'] as bool,
        spentTodayCents: (j['spentTodayCents'] as num).toInt(),
      );
}

class CardDetails {
  const CardDetails(this.number, this.cvc, this.expiry);

  final String number;
  final String cvc;
  final String expiry;

  /// "9999 9912 3456 7890"
  String get formattedNumber {
    final parts = <String>[];
    for (var i = 0; i < number.length; i += 4) {
      parts.add(number.substring(i, i + 4 > number.length ? number.length : i + 4));
    }
    return parts.join(' ');
  }
}

class CardPaymentResult {
  const CardPaymentResult({
    required this.approved,
    required this.message,
    required this.amountCents,
    required this.merchant,
    this.reasonCode,
  });

  final bool approved;
  final String? reasonCode;
  final String message;
  final int amountCents;
  final String merchant;

  factory CardPaymentResult.fromJson(Map<String, dynamic> j) => CardPaymentResult(
        approved: j['approved'] as bool,
        reasonCode: j['reasonCode'] as String?,
        message: j['message'] as String,
        amountCents: (j['amountCents'] as num).toInt(),
        merchant: j['merchant'] as String,
      );
}
