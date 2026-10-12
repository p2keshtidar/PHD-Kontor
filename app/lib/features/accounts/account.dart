class Account {
  const Account({
    required this.id,
    required this.type,
    required this.name,
    required this.balanceCents,
    this.icon,
    this.iban,
  });

  final int id;

  /// MAIN, SUB, SAVINGS
  final String type;
  final String name;
  final String? icon;
  final String? iban;
  final int balanceCents;

  bool get isMain => type == 'MAIN';
  bool get isSub => type == 'SUB';
  bool get isSavings => type == 'SAVINGS';

  factory Account.fromJson(Map<String, dynamic> json) => Account(
        id: (json['id'] as num).toInt(),
        type: json['type'] as String,
        name: json['name'] as String,
        icon: json['icon'] as String?,
        iban: json['iban'] as String?,
        balanceCents: (json['balanceCents'] as num).toInt(),
      );
}
