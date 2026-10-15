import '../../core/money.dart';
import '../../l10n/l10n.dart';

DateTime? _date(dynamic v) => v == null ? null : DateTime.parse(v as String).toLocal();
int? _int(dynamic v) => v == null ? null : (v as num).toInt();

/// Anbieter für Kontoinformationen (z. B. Sandbox mit Testdaten oder ein PSD2-Dienst).
class BankProvider {
  const BankProvider({required this.id, required this.name, required this.sandbox, required this.description});

  final String id;
  final String name;
  final bool sandbox;
  final String description;

  factory BankProvider.fromJson(Map<String, dynamic> j) => BankProvider(
        id: j['id'] as String,
        name: j['name'] as String,
        sandbox: j['sandbox'] as bool? ?? false,
        description: j['description'] as String? ?? '',
      );
}

class Institution {
  const Institution({
    required this.id,
    required this.name,
    required this.historyDays,
    required this.accessDays,
    required this.sandbox,
    this.bic,
  });

  final String id;
  final String name;
  final String? bic;
  final int historyDays;
  final int accessDays;
  final bool sandbox;

  factory Institution.fromJson(Map<String, dynamic> j) => Institution(
        id: j['id'] as String,
        name: j['name'] as String,
        bic: j['bic'] as String?,
        historyDays: _int(j['historyDays']) ?? 90,
        accessDays: _int(j['accessDays']) ?? 90,
        sandbox: j['sandbox'] as bool? ?? false,
      );
}

/// Ein verbundenes Konto bei einer echten Bank (bzw. Sandbox-Bank).
class BankAccount {
  const BankAccount({
    required this.id,
    required this.connectionId,
    required this.bankName,
    required this.name,
    required this.currency,
    required this.connectionStatus,
    required this.sandbox,
    this.ibanMasked,
    this.balanceCents,
    this.balanceAt,
    this.lastSyncAt,
    this.lastSyncStatus,
  });

  final int id;
  final int connectionId;
  final String bankName;
  final String name;
  final String? ibanMasked;
  final String currency;
  final int? balanceCents;
  final DateTime? balanceAt;
  final DateTime? lastSyncAt;

  /// OK, PARTIAL, FAILED
  final String? lastSyncStatus;

  /// ACTIVE, EXPIRED, ERROR …
  final String connectionStatus;
  final bool sandbox;

  bool get syncOk => lastSyncStatus == 'OK' && connectionStatus == 'ACTIVE';

  factory BankAccount.fromJson(Map<String, dynamic> j) => BankAccount(
        id: (j['id'] as num).toInt(),
        connectionId: (j['connectionId'] as num).toInt(),
        bankName: j['bankName'] as String? ?? '',
        name: j['name'] as String? ?? '',
        ibanMasked: j['ibanMasked'] as String?,
        currency: j['currency'] as String? ?? 'EUR',
        balanceCents: _int(j['balanceCents']),
        balanceAt: _date(j['balanceAt']),
        lastSyncAt: _date(j['lastSyncAt']),
        lastSyncStatus: j['lastSyncStatus'] as String?,
        connectionStatus: j['connectionStatus'] as String? ?? 'ACTIVE',
        sandbox: j['sandbox'] as bool? ?? false,
      );
}

class BankOverview {
  const BankOverview({
    required this.accounts,
    required this.totalBalanceCents,
    required this.monthIncomeCents,
    required this.monthExpenseCents,
    required this.anySandbox,
    required this.anyStale,
  });

  final List<BankAccount> accounts;
  final int totalBalanceCents;
  final int monthIncomeCents;
  final int monthExpenseCents;
  final bool anySandbox;
  final bool anyStale;

  factory BankOverview.fromJson(Map<String, dynamic> j) => BankOverview(
        accounts: [for (final a in j['accounts'] as List<dynamic>) BankAccount.fromJson(a as Map<String, dynamic>)],
        totalBalanceCents: _int(j['totalBalanceCents']) ?? 0,
        monthIncomeCents: _int(j['monthIncomeCents']) ?? 0,
        monthExpenseCents: _int(j['monthExpenseCents']) ?? 0,
        anySandbox: j['anySandbox'] as bool? ?? false,
        anyStale: j['anyStale'] as bool? ?? false,
      );
}

/// Bankverbindung = Freigabe (Consent) für eine Bank mit einem oder mehreren Konten.
class BankConnection {
  const BankConnection({
    required this.id,
    required this.provider,
    required this.sandbox,
    required this.bankName,
    required this.status,
    required this.statusLabel,
    required this.permissions,
    required this.accounts,
    this.consentExpiresAt,
    this.daysLeft,
    this.lastSyncAt,
    this.lastSyncStatus,
    this.lastErrorMessage,
  });

  final int id;
  final String provider;
  final bool sandbox;
  final String bankName;

  /// PENDING, ACTIVE, EXPIRED, REVOKED, REJECTED, ERROR
  final String status;
  final String statusLabel;
  final List<String> permissions;
  final DateTime? consentExpiresAt;
  final int? daysLeft;
  final DateTime? lastSyncAt;
  final String? lastSyncStatus;
  final String? lastErrorMessage;
  final List<BankAccount> accounts;

  bool get isActive => status == 'ACTIVE';
  bool get isPending => status == 'PENDING';
  bool get canRenew => status == 'EXPIRED' || status == 'REJECTED' || status == 'ERROR' || status == 'REVOKED';

  factory BankConnection.fromJson(Map<String, dynamic> j) => BankConnection(
        id: (j['id'] as num).toInt(),
        provider: j['provider'] as String,
        sandbox: j['sandbox'] as bool? ?? false,
        bankName: j['bankName'] as String? ?? '',
        status: j['status'] as String,
        statusLabel: j['statusLabel'] as String? ?? j['status'] as String,
        permissions: [for (final p in j['permissions'] as List<dynamic>? ?? const []) p as String],
        consentExpiresAt: _date(j['consentExpiresAt']),
        daysLeft: _int(j['daysLeft']),
        lastSyncAt: _date(j['lastSyncAt']),
        lastSyncStatus: j['lastSyncStatus'] as String?,
        lastErrorMessage: j['lastErrorMessage'] as String?,
        accounts: [
          for (final a in j['accounts'] as List<dynamic>? ?? const []) BankAccount.fromJson(a as Map<String, dynamic>)
        ],
      );
}

class ConnectStart {
  const ConnectStart(this.connection, this.authorizationUrl, this.sandbox, this.nextStepText);

  final BankConnection connection;
  final String? authorizationUrl;
  final bool sandbox;
  final String nextStepText;

  factory ConnectStart.fromJson(Map<String, dynamic> j) => ConnectStart(
        BankConnection.fromJson(j['connection'] as Map<String, dynamic>),
        j['authorizationUrl'] as String?,
        j['sandbox'] as bool? ?? false,
        j['nextStepText'] as String? ?? '',
      );
}

class SyncResult {
  const SyncResult(this.status, this.newTransactions, this.message);

  /// OK, PARTIAL, FAILED, EXPIRED
  final String status;
  final int newTransactions;
  final String message;

  bool get ok => status == 'OK';

  factory SyncResult.fromJson(Map<String, dynamic> j) => SyncResult(
        j['status'] as String,
        _int(j['newTransactions']) ?? 0,
        j['resultMessage'] as String? ?? '',
      );
}

/// Ein Umsatz eines verbundenen Kontos: Originaldaten der Bank + eigene Bearbeitung.
class BankTransaction {
  const BankTransaction({
    required this.id,
    required this.accountId,
    required this.accountName,
    required this.bankName,
    required this.status,
    required this.statusLabel,
    required this.bookingDate,
    required this.amountCents,
    required this.currency,
    required this.reversal,
    required this.autoCategory,
    required this.category,
    required this.categoryLabel,
    required this.tags,
    required this.excluded,
    required this.edited,
    required this.sandbox,
    this.valueDate,
    this.counterparty,
    this.remittance,
    this.relatedId,
    this.note,
  });

  final int id;
  final int accountId;
  final String accountName;
  final String bankName;

  /// PENDING, BOOKED, CANCELLED, SUPERSEDED, REVERSED
  final String status;
  final String statusLabel;
  final DateTime bookingDate;
  final DateTime? valueDate;
  final int amountCents;
  final String currency;
  final String? counterparty;
  final String? remittance;
  final bool reversal;
  final int? relatedId;
  final String autoCategory;
  final String category;
  final String categoryLabel;
  final String? note;
  final List<String> tags;
  final bool excluded;
  final bool edited;
  final bool sandbox;

  bool get isIncoming => amountCents > 0;
  bool get isPending => status == 'PENDING';

  /// nicht (mehr) wirksam: storniert, ersetzt oder zurückgebucht
  bool get isInactive => status == 'CANCELLED' || status == 'SUPERSEDED' || status == 'REVERSED';
  String get title => (counterparty?.isNotEmpty ?? false) ? counterparty! : (remittance ?? '');

  factory BankTransaction.fromJson(Map<String, dynamic> j) => BankTransaction(
        id: (j['id'] as num).toInt(),
        accountId: (j['accountId'] as num).toInt(),
        accountName: j['accountName'] as String? ?? '',
        bankName: j['bankName'] as String? ?? '',
        status: j['status'] as String,
        statusLabel: j['statusLabel'] as String? ?? j['status'] as String,
        bookingDate: DateTime.parse(j['bookingDate'] as String),
        valueDate: j['valueDate'] == null ? null : DateTime.parse(j['valueDate'] as String),
        amountCents: (j['amountCents'] as num).toInt(),
        currency: j['currency'] as String? ?? 'EUR',
        counterparty: j['counterparty'] as String?,
        remittance: j['remittance'] as String?,
        reversal: j['reversal'] as bool? ?? false,
        relatedId: _int(j['relatedId']),
        autoCategory: j['autoCategory'] as String? ?? 'SONSTIGES',
        category: j['category'] as String? ?? 'SONSTIGES',
        categoryLabel: j['categoryLabel'] as String? ?? '',
        note: j['note'] as String?,
        tags: [for (final t in j['tags'] as List<dynamic>? ?? const []) t as String],
        excluded: j['excluded'] as bool? ?? false,
        edited: j['edited'] as bool? ?? false,
        sandbox: j['sandbox'] as bool? ?? false,
      );
}

class BankTransactionPage {
  const BankTransactionPage(this.items, this.hasMore);

  final List<BankTransaction> items;
  final bool hasMore;

  factory BankTransactionPage.fromJson(Map<String, dynamic> j) => BankTransactionPage(
        [for (final t in j['items'] as List<dynamic>) BankTransaction.fromJson(t as Map<String, dynamic>)],
        j['hasMore'] as bool? ?? false,
      );
}

/// Filter der Umsatzliste.
class BankTxFilter {
  const BankTxFilter({this.accountId, this.query = '', this.category, this.status});

  final int? accountId;
  final String query;
  final String? category;

  /// null = alle außer ersetzten Vormerkungen
  final String? status;

  BankTxFilter copyWith({String? query, Object? category = _keep, Object? status = _keep}) => BankTxFilter(
        accountId: accountId,
        query: query ?? this.query,
        category: identical(category, _keep) ? this.category : category as String?,
        status: identical(status, _keep) ? this.status : status as String?,
      );

  Map<String, dynamic> toQuery(int page) => {
        'page': page,
        'size': 30,
        if (accountId != null) 'accountId': accountId,
        if (query.trim().isNotEmpty) 'q': query.trim(),
        if (category != null) 'category': category,
        if (status != null) 'status': status,
      };

  @override
  bool operator ==(Object other) =>
      other is BankTxFilter &&
      other.accountId == accountId &&
      other.query == query &&
      other.category == category &&
      other.status == status;

  @override
  int get hashCode => Object.hash(accountId, query, category, status);
}

const Object _keep = Object();

/// Statusfilter der Umsatzliste (Schlüssel wie im Backend).
Map<String, String> get bankStatusFilters => {
      'PENDING': tr('Vorgemerkt'),
      'BOOKED': tr('Gebucht'),
      'CANCELLED': tr('Storniert'),
      'REVERSED': tr('Zurückgebucht'),
    };

/// Betrag in Euro wie überall in der App, andere Währungen mit Währungscode.
String formatBankAmount(int cents, String currency) {
  if (currency == 'EUR') return formatCents(cents);
  final sign = cents < 0 ? '-' : '';
  return '$sign${(cents.abs() / 100).toStringAsFixed(2)} $currency';
}
