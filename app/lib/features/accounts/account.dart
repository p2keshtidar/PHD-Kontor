import '../../l10n/l10n.dart';

/// Zwecke digitaler Konten (Schlüssel wie im Backend) mit Standard-Symbol.
Map<String, (String, String)> get digitalPurposes => {
      'SPARZIEL': (tr('Sparziel'), 'savings'),
      'URLAUB': (tr('Urlaub'), 'flight'),
      'AUTO': (tr('Auto'), 'car'),
      'IMMOBILIE': (tr('Immobilie'), 'home'),
      'RUECKZAHLUNG': (tr('Rückzahlung'), 'payments'),
      'WG': (tr('WG-Kasse'), 'groups'),
      'NOTGROSCHEN': (tr('Notgroschen'), 'shield'),
      'SONSTIGES': (tr('Sonstiges'), 'savings'),
    };

/// Rollen in geteilten digitalen Konten.
Map<String, String> get digitalRoles => {
      'OWNER': tr('Besitzer'),
      'FULL': tr('Einzahlen und auszahlen'),
      'DEPOSIT': tr('Nur einzahlen'),
      'VIEW': tr('Nur ansehen'),
    };

/// Angaben zu einem digitalen Konto (Topf im Projektbank-Konto – kein eigenes Bankkonto).
class DigitalInfo {
  const DigitalInfo({
    required this.purpose,
    required this.role,
    required this.canDeposit,
    required this.canWithdraw,
    required this.canManage,
    required this.goalReached,
    required this.custodyText,
    this.targetCents,
    this.targetDate,
    this.progressPercent,
    this.remainingCents,
    this.monthlyNeededCents,
  });

  final String purpose;
  final int? targetCents;
  final DateTime? targetDate;
  final double? progressPercent;
  final int? remainingCents;
  final int? monthlyNeededCents;
  final bool goalReached;

  /// OWNER, FULL, DEPOSIT, VIEW
  final String role;
  final bool canDeposit;
  final bool canWithdraw;
  final bool canManage;

  /// Erklärung vom Server, wie das Geld gehalten wird
  final String custodyText;

  String get purposeLabel => digitalPurposes[purpose]?.$1 ?? purpose;
  String get roleLabel => digitalRoles[role] ?? role;
  bool get hasTarget => targetCents != null;

  factory DigitalInfo.fromJson(Map<String, dynamic> j) => DigitalInfo(
        purpose: j['purpose'] as String? ?? 'SONSTIGES',
        targetCents: (j['targetCents'] as num?)?.toInt(),
        targetDate: j['targetDate'] == null ? null : DateTime.parse(j['targetDate'] as String),
        progressPercent: (j['progressPercent'] as num?)?.toDouble(),
        remainingCents: (j['remainingCents'] as num?)?.toInt(),
        monthlyNeededCents: (j['monthlyNeededCents'] as num?)?.toInt(),
        goalReached: j['goalReached'] as bool? ?? false,
        role: j['role'] as String? ?? 'OWNER',
        canDeposit: j['canDeposit'] as bool? ?? true,
        canWithdraw: j['canWithdraw'] as bool? ?? true,
        canManage: j['canManage'] as bool? ?? true,
        custodyText: j['custodyText'] as String? ?? '',
      );
}

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
    this.digital,
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

  /// nur bei digitalen Konten (Typ SUB)
  final DigitalInfo? digital;

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
        digital: json['digital'] is Map<String, dynamic>
            ? DigitalInfo.fromJson(json['digital'] as Map<String, dynamic>)
            : null,
      );
}
