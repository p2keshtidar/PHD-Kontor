class Account {
  const Account({
    required this.id,
    required this.type,
    required this.name,
    required this.balanceCents,
    this.icon,
    this.iban,
    this.shared = false,
    this.ownerName,
    this.memberCount = 0,
  });

  final int id;

  /// MAIN, SUB, SAVINGS
  final String type;
  final String name;
  final String? icon;
  final String? iban;
  final int balanceCents;

  /// /K060/: geteiltes Unterkonto
  final bool shared;

  /// Besitzer, wenn das Unterkonto jemand anderem gehört (sonst null)
  final String? ownerName;
  final int memberCount;

  /// Gehört jemand anderem und ist mit mir geteilt
  bool get isForeign => ownerName != null;

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
        shared: json['shared'] as bool? ?? false,
        ownerName: json['ownerName'] as String?,
        memberCount: (json['memberCount'] as num?)?.toInt() ?? 0,
      );
}
