import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

/// Antwort mit Statuscode (für Fehlerfälle).
class FakeResponse {
  const FakeResponse(this.status, [this.body]);
  final int status;
  final Object? body;
}

/// Simulierter Server für Widget-Tests: ersetzt die HTTP-Schicht von dio.
///
/// Routen: "METHODE /pfad", "*" steht für einen Pfadteil, z. B. "GET /api/plans/*".
/// Der Handler bekommt die Anfrage und liefert JSON (Status 200) oder eine [FakeResponse].
class FakeBackend implements HttpClientAdapter {
  FakeBackend([Map<String, Object? Function(RequestOptions)>? routes]) {
    this.routes.addAll(defaultRoutes());
    if (routes != null) this.routes.addAll(routes);
  }

  final Map<String, Object? Function(RequestOptions)> routes = {};
  final List<RequestOptions> requests = [];

  /// Alle Anfragen an einen Pfad (z. B. "POST /api/transfers")
  List<RequestOptions> calls(String route) =>
      requests.where((r) => '${r.method} ${r.path}' == route || _matches(route, r)).toList();

  Map<String, dynamic> lastBody(String route) {
    final c = calls(route);
    if (c.isEmpty) throw StateError('Keine Anfrage an $route. Bisher: ${requests.map((r) => '${r.method} ${r.path}')}');
    final d = c.last.data;
    return d is Map<String, dynamic> ? d : (jsonDecode(d as String) as Map<String, dynamic>);
  }

  bool _matches(String route, RequestOptions r) {
    final parts = route.split(' ');
    if (parts[0] != r.method) return false;
    final want = parts[1].split('/');
    final got = r.path.split('?').first.split('/');
    if (want.length != got.length) return false;
    for (var i = 0; i < want.length; i++) {
      if (want[i] != '*' && want[i] != got[i]) return false;
    }
    return true;
  }

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requests.add(options);
    // genaueste Route zuerst (ohne *)
    final keys = routes.keys.toList()..sort((a, b) => '*'.allMatches(a).length.compareTo('*'.allMatches(b).length));
    for (final k in keys) {
      if (_matches(k, options)) {
        final result = routes[k]!(options);
        final status = result is FakeResponse ? result.status : (options.method == 'POST' ? 201 : 200);
        final body = result is FakeResponse ? result.body : result;
        if (body is String && options.path.endsWith('/sample')) {
          return ResponseBody.fromString(body, status, headers: {
            Headers.contentTypeHeader: ['text/csv; charset=utf-8'],
          });
        }
        return ResponseBody.fromString(body == null ? '' : jsonEncode(body), status, headers: {
          Headers.contentTypeHeader: ['application/json; charset=utf-8'],
        });
      }
    }
    return ResponseBody.fromString(
        jsonEncode({'code': 'NOT_FOUND', 'message': 'Keine Testroute für ${options.method} ${options.path}'}), 404,
        headers: {Headers.contentTypeHeader: ['application/json']});
  }

  @override
  void close({bool force = false}) {}

  // ------------------------------------------------------------------ Testdaten

  static Map<String, dynamic> user({bool twoFactor = false}) => {
        'id': 1, 'email': 'erika@example.org', 'firstName': 'Erika', 'lastName': 'Mustermann',
        'phone': '015112345678', 'birthDate': '1999-05-01', 'role': 'USER', 'twoFactorEnabled': twoFactor,
      };

  static Map<String, dynamic> tokens({bool twoFactor = false}) =>
      {'accessToken': 'a.b.c', 'refreshToken': 'r', 'user': user(twoFactor: twoFactor)};

  static List<Map<String, dynamic>> accounts() => [
        {'id': 10, 'type': 'MAIN', 'name': 'Girokonto', 'icon': null, 'iban': 'DE89000100010000000010',
         'balanceCents': 523450, 'shared': false, 'ownerName': null, 'memberCount': 0},
        {'id': 11, 'type': 'SUB', 'name': 'Urlaub Mallorca mit der ganzen Familie', 'icon': 'flight', 'iban': null,
         'balanceCents': 120000, 'shared': true, 'ownerName': null, 'memberCount': 2,
         'digital': digital('URLAUB', target: 300000, progress: 40.0, remaining: 180000, monthly: 22500)},
        {'id': 12, 'type': 'SUB', 'name': 'WG-Kasse', 'icon': 'home', 'iban': null,
         'balanceCents': 4500, 'shared': true, 'ownerName': 'Mia Musterfrau-Langname', 'memberCount': 3,
         'digital': digital('WG', role: 'VIEW')},
      ];

  /// Konten mit weiterem Girokonto (21) und Tagesgeld (20), beides Simulation
  static List<Map<String, dynamic>> accountsWithExtra() => [
        ...accounts(),
        {'id': 20, 'type': 'CALL', 'name': 'Tagesgeld', 'icon': null, 'iban': 'DE89000100010000000020',
         'balanceCents': 250000, 'shared': false, 'ownerName': null, 'memberCount': 0,
         'product': {'product': 'CALL', 'productLabel': 'Tagesgeld', 'interestPercent': 2.0, 'receivesTransfers': false,
           'sendsTransfers': false, 'closable': true,
           'note': 'Fiktiver Zinssatz der Simulation, Zinsen am Monatsende. Ein- und Auszahlungen nur über deine eigenen Konten.'}},
        {'id': 21, 'type': 'CURRENT', 'name': 'Haushalt', 'icon': null, 'iban': 'DE89000100010000000021',
         'balanceCents': 12345, 'shared': false, 'ownerName': null, 'memberCount': 0,
         'product': {'product': 'CURRENT', 'productLabel': 'Weiteres Girokonto', 'interestPercent': null,
           'receivesTransfers': true, 'sendsTransfers': false, 'closable': true,
           'note': 'Eigene IBAN für Eingänge. Überweisungen an andere nur vom Girokonto.'}},
      ];

  static Map<String, dynamic> bankProducts({bool canOpen = true}) => {
        'products': [
          {'type': 'CURRENT', 'label': 'Weiteres Girokonto', 'interestPercent': null, 'simulation': true,
           'description': 'Eigene IBAN für Eingänge, z. B. als Haushaltskonto. Geld bewegst du per Umbuchung zwischen '
               'deinen Konten; Überweisungen an andere gehen nur vom Girokonto.'},
          {'type': 'CALL', 'label': 'Tagesgeld', 'interestPercent': 2.0, 'simulation': true,
           'description': 'Zinsen jeden Monat, täglich verfügbar. Ein- und Auszahlungen nur über deine eigenen Konten.'},
        ],
        'openCount': canOpen ? 1 : 3, 'maxCount': 3, 'canOpen': canOpen,
        'disclaimer': 'Konten der Projektbank sind eine Simulation mit Spielgeld – keine echten Bankkonten, '
            'keine Einlagensicherung. Der Zinssatz des Tagesgelds ist fiktiv.',
      };

  /// Alle Umsätze: Projektbank (Umbuchung, Zinsen) und verbundene Bank (gebucht, vorgemerkt)
  static Map<String, dynamic> transactionsPage({bool hasMore = false, bool empty = false}) => {
        'items': [
          if (!empty) ...[
          {'source': 'EXTERNAL', 'id': 701, 'accountId': 601, 'accountName': 'Girokonto', 'institution': 'Sandbox Hauptbank',
           'date': '2026-10-13', 'bookedAt': null, 'amountCents': -4599, 'currency': 'EUR', 'counterpartyName': 'Supermarkt Nord',
           'description': 'Einkauf', 'category': 'LEBENSMITTEL', 'categoryLabel': 'Lebensmittel', 'status': 'PENDING',
           'internal': false, 'excluded': false},
          {'source': 'PROJEKTBANK', 'id': 300, 'accountId': 20, 'accountName': 'Tagesgeld', 'institution': 'Projektbank',
           'date': '2026-10-12', 'bookedAt': '2026-10-12T09:00:00Z', 'amountCents': 50000, 'currency': 'EUR',
           'counterpartyName': 'Girokonto', 'description': 'Umbuchung Girokonto → Tagesgeld', 'category': 'UMBUCHUNG',
           'categoryLabel': 'Umbuchung', 'status': 'BOOKED', 'internal': true, 'excluded': false},
          {'source': 'PROJEKTBANK', 'id': 300, 'accountId': 10, 'accountName': 'Girokonto', 'institution': 'Projektbank',
           'date': '2026-10-12', 'bookedAt': '2026-10-12T09:00:00Z', 'amountCents': -50000, 'currency': 'EUR',
           'counterpartyName': 'Tagesgeld', 'description': 'Umbuchung Girokonto → Tagesgeld', 'category': 'UMBUCHUNG',
           'categoryLabel': 'Umbuchung', 'status': 'BOOKED', 'internal': true, 'excluded': false},
          {'source': 'PROJEKTBANK', 'id': 299, 'accountId': 20, 'accountName': 'Tagesgeld', 'institution': 'Projektbank',
           'date': '2026-10-01', 'bookedAt': '2026-10-01T06:00:00Z', 'amountCents': 47, 'currency': 'EUR',
           'counterpartyName': 'Projektbank', 'description': 'Zinsen Tagesgeld 09/2026 (Simulation)',
           'category': 'SONSTIGE_EINNAHMEN', 'categoryLabel': 'Sonstige Einnahmen', 'status': 'BOOKED', 'internal': false,
           'excluded': false},
          ],
        ],
        'page': 0, 'size': 30, 'hasMore': hasMore, 'incomeCents': 47, 'expenseCents': 0,
        'notes': ['Konten der Projektbank sind eine Simulation mit Spielgeld.',
          'Summen ohne Umbuchungen zwischen eigenen Konten, Sparraten, Kreditauszahlungen und vorgemerkte Umsätze.'],
      };

  /// Angaben eines digitalen Kontos wie vom Server
  static Map<String, dynamic> digital(String purpose,
          {int? target, double? progress, int? remaining, int? monthly, String role = 'OWNER'}) =>
      {
        'purpose': purpose, 'purposeLabel': purpose, 'targetCents': target,
        'targetDate': target == null ? null : '2027-06-01', 'progressPercent': progress, 'remainingCents': remaining,
        'monthlyNeededCents': monthly, 'goalReached': false, 'role': role, 'roleLabel': role,
        'canDeposit': role != 'VIEW', 'canWithdraw': role == 'OWNER' || role == 'FULL', 'canManage': role == 'OWNER',
        'custody': 'INTERNAL', 'custodyLabel': 'Umgebucht innerhalb der Projektbank (Simulation)',
        'custodyText': 'Das Geld wurde von einem Girokonto der Projektbank hierher umgebucht. Ein digitales Konto ist '
            'ein Topf in deinem Konto – kein eigenes Bankkonto und ohne eigene Einlagensicherung.',
      };

  static Map<String, dynamic> bookingPage() => {
        'items': [
          for (var i = 0; i < 8; i++)
            {
              'bookingId': 100 + i, 'type': i.isEven ? 'TRANSFER' : 'CARD',
              'description': i == 0 ? 'Sehr langer Verwendungszweck für die Miete Oktober inklusive Nebenkosten' : 'Einkauf $i',
              'bookedAt': '2026-10-${(12 - i).toString().padLeft(2, '0')}T10:00:00Z', 'amountCents': i.isEven ? -85000 : 2399,
              'counterpartyName': 'Hausverwaltung Main GmbH & Co. KG', 'counterpartyIban': 'DE12000100010000000099',
              'category': 'WOHNEN', 'categoryLabel': 'Wohnen',
            },
        ],
        'page': 0, 'size': 20, 'hasMore': false,
      };

  /// Finanz-Dashboard (GET /api/dashboard): Girokonto, digitales Konto mit Ziel, Depot, verbundene Bank (veraltet),
  /// ein Kredit. Verlauf: verbundene Bank erst ab Juli enthalten.
  static Map<String, dynamic> dashboard({bool empty = false}) {
    final months = ['2025-11', '2025-12', '2026-01', '2026-02', '2026-03', '2026-04', '2026-05', '2026-06', '2026-07',
      '2026-08', '2026-09', '2026-10'];
    if (empty) {
      return {
        'date': '2026-10-13',
        'netWorth': {'accountsCents': 100000, 'digitalCents': 0, 'plansCents': 0, 'depotCents': 0, 'externalCents': 0,
          'loansCents': 0, 'totalCents': 100000},
        'liquidity': {'availableCents': 100000, 'reservedCents': 0, 'earmarkedCents': 0, 'externalCents': 0},
        'accounts': [
          {'kind': 'PROJEKTBANK', 'id': 10, 'name': 'Girokonto', 'institution': 'Projektbank (Simulation)',
           'balanceCents': 100000, 'simulation': true, 'stale': false, 'note': null},
        ],
        'cards': [],
        'debts': {'totalCents': 0, 'monthlyRatesCents': 0, 'debtRatioPercent': null, 'loans': []},
        'cashflow': {'incomeCents': 0, 'expenseCents': 0, 'netCents': 0, 'months': [
          for (final m in months.sublist(6)) {'month': m, 'complete': m != '2026-10', 'incomeCents': 0,
            'expenseCents': 0, 'netCents': 0},
        ]},
        'goals': [],
        'topExpenses': [],
        'topExpensesMonth': null,
        'history': [
          for (final m in months) {'month': m, 'totalCents': m == '2026-10' ? 100000 : 0,
            'projektbankCents': m == '2026-10' ? 100000 : 0, 'depotCents': 0, 'externalCents': null,
            'externalIncluded': true},
        ],
        'notes': ['Girokonto, digitale Konten, Pläne, Depot und Kredite der Projektbank sind eine Simulation mit Spielgeld.'],
        'generatedAt': '2026-10-13T10:00:00Z',
      };
    }
    return {
      'date': '2026-10-13',
      'netWorth': {'accountsCents': 647950, 'digitalCents': 50000, 'plansCents': 0, 'depotCents': 22710,
        'externalCents': 312045, 'loansCents': -500000, 'totalCents': 532705},
      'liquidity': {'availableCents': 647950, 'reservedCents': 72710, 'earmarkedCents': 15000, 'externalCents': 312045},
      'accounts': [
        {'kind': 'PROJEKTBANK', 'id': 10, 'name': 'Girokonto', 'institution': 'Projektbank (Simulation)',
         'balanceCents': 647950, 'simulation': true, 'stale': false, 'note': null},
        {'kind': 'DIGITAL', 'id': 11, 'name': 'Urlaub', 'institution': 'Projektbank (Simulation)',
         'balanceCents': 50000, 'simulation': true, 'stale': false, 'note': null},
        {'kind': 'PLAN', 'id': 5, 'name': 'Weltportfolio', 'institution': 'Projektbank (Simulation)',
         'balanceCents': 22710, 'simulation': true, 'stale': false, 'note': null},
        {'kind': 'EXTERNAL', 'id': 601, 'name': 'Girokonto', 'institution': 'Sandbox Hauptbank',
         'balanceCents': 312045, 'simulation': true, 'stale': true, 'note': 'Stand 10.10.2026'},
        {'kind': 'LOAN', 'id': 7, 'name': 'Kredit Auto (Nordlicht Kredit)', 'institution': 'Projektbank (Simulation)',
         'balanceCents': -500000, 'simulation': true, 'stale': false, 'note': null},
      ],
      'cards': [
        {'id': 3, 'label': 'Debitkarte (Simulation)', 'maskedNumber': '•••• 4242', 'status': 'ACTIVE',
         'dailyLimitCents': 100000, 'spentTodayCents': 2399, 'simulation': true},
      ],
      'debts': {'totalCents': 500000, 'monthlyRatesCents': 22046, 'debtRatioPercent': 7.5, 'loans': [
        {'id': 7, 'name': 'Auto · Nordlicht Kredit', 'status': 'ACTIVE', 'remainingCents': 500000,
         'monthlyRateCents': 22046, 'nextDueDate': '2026-11-13'},
      ]},
      'cashflow': {'incomeCents': 145000, 'expenseCents': 61230, 'netCents': 83770, 'months': [
        for (final (i, m) in months.sublist(6).indexed)
          {'month': m, 'complete': m != '2026-10', 'incomeCents': 290000, 'expenseCents': 140000 + i * 5000,
           'netCents': 150000 - i * 5000},
      ]},
      'goals': [
        {'kind': 'DIGITAL', 'id': 11, 'name': 'Urlaub', 'savedCents': 50000, 'targetCents': 300000,
         'progressPercent': 16.7, 'targetDate': '2027-06-30', 'reached': false},
        {'kind': 'PLAN', 'id': 5, 'name': 'Notgroschen', 'savedCents': 120000, 'targetCents': 100000,
         'progressPercent': 100.0, 'targetDate': null, 'reached': true},
      ],
      'topExpenses': [
        {'category': 'WOHNEN', 'label': 'Wohnen', 'cents': 90500, 'percent': 62.3},
        {'category': 'LEBENSMITTEL', 'label': 'Lebensmittel', 'cents': 25811, 'percent': 17.8},
        {'category': 'FREIZEIT', 'label': 'Freizeit', 'cents': 8000, 'percent': 5.5},
      ],
      'topExpensesMonth': '2026-09',
      'history': [
        for (final (i, m) in months.indexed)
          {'month': m, 'totalCents': i < 3 ? 0 : -300000 + i * 80000, 'projektbankCents': i < 3 ? 0 : 100000 + i * 40000,
           'depotCents': i < 6 ? 0 : (i - 5) * 4000, 'externalCents': i < 8 ? null : 312045, 'externalIncluded': i >= 8},
      ],
      'notes': [
        'Girokonto, digitale Konten, Pläne, Depot und Kredite der Projektbank sind eine Simulation mit Spielgeld.',
        'Verbundene Banken: letzter abgerufener Kontostand, nur Konten in Euro.',
        'Mindestens ein Kontostand einer verbundenen Bank ist älter als 2 Tage oder die Verbindung ist gestört.',
      ],
      'generatedAt': '2026-10-13T10:00:00Z',
    };
  }

  static List<Map<String, dynamic>> months() => [
        for (var m = 5; m <= 10; m++)
          {
            'month': '2026-${m.toString().padLeft(2, '0')}', 'complete': m < 10, 'hasData': true,
            'incomeCents': 265000, 'expenseCents': 187974, 'fixedCents': 100000, 'freeCents': 77026,
            'categories': [
              {'category': 'WOHNEN', 'label': 'Wohnen', 'kind': 'FIXED', 'cents': 90500},
              {'category': 'LEBENSMITTEL', 'label': 'Lebensmittel', 'kind': 'VARIABLE', 'cents': 47666},
              {'category': 'VERTRAEGE', 'label': 'Verträge & Abos', 'kind': 'FIXED', 'cents': 9500},
            ],
          },
      ];

  static Map<String, dynamic> overview() => {
        'hasData': true, 'monthsUsed': 3, 'avgIncomeCents': 265000, 'avgExpenseCents': 186617,
        'avgFixedCents': 100000, 'avgFreeCents': 78383, 'planRatesCents': 0, 'availableCents': 78383,
        'maxLoanRateCents': 54868, 'demoProfile': 'EINSTEIGER',
      };

  static Map<String, dynamic> planSummary() => {
        'id': 5, 'type': 'GOAL', 'name': 'Urlaub', 'icon': 'flight', 'status': 'ACTIVE', 'riskLevel': 'AUSGEWOGEN',
        'targetCents': 300000, 'targetDate': '2028-04-01', 'monthlyRateCents': 15000, 'executionDay': 15,
        'currentValueCents': 45210, 'depositedCents': 45000, 'progressPercent': 15.1, 'projectedDate': '2028-03-15',
        'onTrack': true, 'nextExecutionDate': '2026-10-15', 'accountId': 13, 'reminderDays': 3,
      };

  static Map<String, dynamic> planDetail() => {
        'summary': planSummary(), 'cashCents': 22500, 'depotValueCents': 22710, 'gainCents': 210, 'gainPercent': 0.47,
        'expectedReturnPa': 4.0, 'riskExplanation': 'Halb Sparkonto, halb Fonds.', 'requiredRateCents': 14800,
        'positions': [
          {'fund': 'WELT_AKTIEN', 'label': 'Welt-Aktienfonds', 'units': 1.23, 'price': 104.2, 'valueCents': 12817,
           'investedCents': 12750, 'weightPercent': 56.4},
        ],
        'history': [
          {'date': '2026-08-15', 'valueCents': 15000, 'depositedCents': 15000},
          {'date': '2026-09-15', 'valueCents': 30120, 'depositedCents': 30000},
          {'date': '2026-10-15', 'valueCents': 45210, 'depositedCents': 45000},
        ],
        'tips': [
          {'category': 'FREIZEIT', 'label': 'Freizeit', 'text': 'Im letzten Monat 40 € mehr als sonst.',
           'saveCents': 4000, 'monthsEarlier': 2},
        ],
      };

  static Map<String, dynamic> loan({int extraPaid = 0, String status = 'ACTIVE'}) => {
        'id': 7, 'bank': 'NORDLICHT', 'bankName': 'Nordlicht Kredit', 'purpose': 'AUTO', 'purposeLabel': 'Auto',
        'principalCents': 2000000, 'equityCents': 0, 'interestRate': 5.59, 'termMonths': 60, 'monthlyRateCents': 38286,
        'startDate': '2026-10-12', 'nextDueDate': status == 'ACTIVE' ? '2026-11-12' : null,
        'paidInstallments': status == 'ACTIVE' ? 1 : 0, 'remainingCents': status == 'ACTIVE' ? 1971031 : 0,
        'progressPercent': status == 'ACTIVE' ? 1.4 : 0.0, 'status': status, 'accountId': 14,
        'remainingInstallments': status == 'ACTIVE' ? 59 : 60,
        'expectedEndDate': '2031-10-12', 'extraPaidCents': extraPaid,
        'statusLabel': switch (status) {
          'CONTRACTED' => 'Vertrag geschlossen – Auszahlung ausstehend',
          'CANCELLED' => 'Widerrufen',
          'PAID_OFF' => 'Zurückgezahlt',
          _ => 'Ausgezahlt – Raten laufen',
        },
        'effectiveRate': 5.74, 'simulation': true, 'applicationId': 51,
      };

  static Map<String, dynamic> loanDetail({int extraPaid = 0, String status = 'ACTIVE'}) => {
        'loan': loan(extraPaid: extraPaid, status: status), 'totalInterestCents': 297160, 'interestSavedCents': extraPaid > 0 ? 12345 : 0,
        'schedule': [
          for (var m = 1; m <= 60; m++)
            {'month': m, 'dueDate': '2026-${((10 + m - 1) % 12 + 1).toString().padLeft(2, '0')}-12', 'rateCents': 38286,
             'interestCents': 9317, 'principalCents': 28969, 'remainingCents': 2000000 - m * 33000,
             'paid': m == 1, 'extraCents': m == 1 ? extraPaid : 0},
        ],
        'extraPayments': [],
      };

  // ------------------------------------------------------------------ Sparregeln (Sprint 5)

  static Map<String, dynamic> allocationRun(
          {int id = 41, String status = 'COMPLETED', String kind = 'TRANSFER', String? reason, int amount = 5000}) =>
      {
        'id': id, 'ruleId': 31, 'targetAccountId': 11, 'targetName': 'Urlaub Mallorca mit der ganzen Familie',
        'sourceType': kind == 'TRANSFER' ? 'PROJEKTBANK' : 'EXTERNAL',
        'sourceName': kind == 'TRANSFER' ? 'Girokonto' : 'Sandbox-Hausbank (Testdaten)', 'kind': kind,
        'kindLabel': kind == 'TRANSFER' ? 'Umbuchung' : 'Vormerkung', 'dueDate': '2026-10-09', 'amountCents': amount,
        'status': status,
        'statusLabel': switch (status) {
          'COMPLETED' => kind == 'TRANSFER' ? 'Umgebucht' : 'Vormerkung erfasst',
          'RETRYING' => 'Neuer Versuch am 14.10.2026',
          'FAILED' => 'Fehlgeschlagen',
          _ => status,
        },
        'attempts': status == 'FAILED' ? 3 : 1, 'nextAttempt': status == 'RETRYING' ? '2026-10-14' : null,
        'failureReason': reason, 'bookingId': status == 'COMPLETED' && kind == 'TRANSFER' ? 120 : null,
        'manual': false, 'updatedAt': '2026-10-09T10:00:00Z',
      };

  static Map<String, dynamic> allocationRule({int id = 31, String status = 'ACTIVE', String source = 'PROJEKTBANK'}) =>
      {
        'id': id, 'targetAccountId': 11, 'targetName': 'Urlaub Mallorca mit der ganzen Familie', 'sourceType': source,
        'sourceId': source == 'PROJEKTBANK' ? 10 : 5,
        'sourceName': source == 'PROJEKTBANK' ? 'Girokonto' : 'Tagesgeld',
        'bankName': source == 'PROJEKTBANK' ? 'Projektbank (Simulation)' : 'Sandbox-Hausbank (Testdaten)',
        'movesMoney': source == 'PROJEKTBANK',
        'sourceExplanation': source == 'PROJEKTBANK'
            ? 'Umbuchung innerhalb der Projektbank (Simulation) – keine echte Überweisung.'
            : 'Nur Vormerkung: Das Geld bleibt bei Sandbox-Hausbank (Testdaten). Es wird keine Zahlung ausgelöst.',
        'amountCents': source == 'PROJEKTBANK' ? 5000 : 15000, 'interval': source == 'PROJEKTBANK' ? 'WEEKLY' : 'MONTHLY',
        'intervalLabel': source == 'PROJEKTBANK' ? 'wöchentlich' : 'monatlich', 'startDate': '2026-10-02',
        'endDate': null, 'nextDate': status == 'ACTIVE' ? '2026-10-16' : null, 'status': status,
        'statusLabel': status == 'ACTIVE' ? 'Aktiv' : (status == 'PAUSED' ? 'Pausiert' : 'Beendet'),
        'statusReason': status == 'PAUSED' ? 'Das Bankkonto ist nicht mehr verbunden.' : null,
        'lastRun': allocationRun(), 'createdAt': '2026-10-02T10:00:00Z',
      };

  static Map<String, dynamic> accountAllocations({bool empty = false}) => {
        'accountId': 11, 'targetName': 'Urlaub Mallorca mit der ganzen Familie', 'canAllocate': true,
        'canRelease': !empty, 'balanceCents': 120000, 'earmarkedCents': empty ? 0 : 15000,
        'earmarks': empty
            ? []
            : [
                {'externalAccountId': 5, 'sourceName': 'Tagesgeld', 'bankName': 'Sandbox-Hausbank (Testdaten)',
                 'amountCents': 15000, 'own': true, 'active': true},
              ],
        'rules': empty ? [] : [allocationRule(), allocationRule(id: 32, source: 'EXTERNAL')],
        'otherRules': empty ? 0 : 1,
        'runs': empty
            ? []
            : [
                allocationRun(id: 42, status: 'FAILED',
                    reason: 'Nicht genug Guthaben auf „Girokonto“. Verfügbar: 12,00 €.'),
                allocationRun(id: 41),
                allocationRun(id: 43, kind: 'EARMARK', amount: 15000),
              ],
        'upcoming': empty
            ? []
            : [
                {'date': '2026-10-16', 'amountCents': 5000, 'ruleId': 31, 'sourceName': 'Girokonto', 'movesMoney': true},
                {'date': '2026-11-02', 'amountCents': 15000, 'ruleId': 32, 'sourceName': 'Tagesgeld', 'movesMoney': false},
              ],
        'projectedGoalDate': empty ? null : '2027-03-12',
        'projectionText': empty ? null : 'Voraussichtlich erreicht am 12.03.2027, wenn alle Sparregeln wie geplant laufen.',
        'earmarkExplanation': 'Vormerkung: Das Geld bleibt auf deinem Konto bei der anderen Bank. Es wird keine Zahlung '
            'ausgelöst; der Betrag wird nur deinem Ziel zugerechnet.',
      };

  static List<Map<String, dynamic>> allocationSources() => [
        {'type': 'PROJEKTBANK', 'id': 10, 'sourceName': 'Girokonto', 'bankName': 'Projektbank (Simulation)',
         'ibanMasked': null, 'balanceCents': 523450, 'earmarkedCents': 0, 'availableCents': 523450, 'balanceAt': null,
         'usable': true, 'unusableReason': null, 'movesMoney': true,
         'sourceExplanation': 'Umbuchung innerhalb der Projektbank (Simulation) – keine echte Überweisung.',
         'sandbox': true, 'coverageWarning': false, 'coverageText': null},
        {'type': 'EXTERNAL', 'id': 5, 'sourceName': 'Tagesgeld', 'bankName': 'Sandbox-Hausbank (Testdaten)',
         'ibanMasked': 'DE87 •••• 4764', 'balanceCents': 2262625, 'earmarkedCents': 15000, 'availableCents': 2247625,
         'balanceAt': '2026-10-13T08:00:00Z', 'usable': true, 'unusableReason': null, 'movesMoney': false,
         'sourceExplanation': 'Nur Vormerkung: Das Geld bleibt bei Sandbox-Hausbank (Testdaten). Es wird keine Zahlung ausgelöst.',
         'sandbox': true, 'coverageWarning': false, 'coverageText': null},
        {'type': 'EXTERNAL', 'id': 6, 'sourceName': 'Online-Konto', 'bankName': 'Sandbox-Onlinebank (Testdaten)',
         'ibanMasked': 'DE11 •••• 0001', 'balanceCents': 30000, 'earmarkedCents': 0, 'availableCents': 30000,
         'balanceAt': '2026-10-01T08:00:00Z', 'usable': false,
         'unusableReason': 'Die Freigabe für Sandbox-Onlinebank (Testdaten) ist abgelaufen. Bitte die Bank neu verbinden.',
         'movesMoney': false, 'sourceExplanation': '', 'sandbox': true, 'coverageWarning': false, 'coverageText': null},
      ];

  // ------------------------------------------------------------------ Kreditvergleich (Sprint 5)

  static Map<String, dynamic> loanOffer(String bank, String name, double rate, double eff, int monthly, int total,
          {bool recommended = false, bool declined = false, String minClass = 'MITTEL'}) =>
      {
        'bank': bank, 'bankName': name, 'interestRate': rate, 'monthlyRateCents': monthly,
        'totalInterestCents': total - 1000000, 'totalCostCents': total, 'affordable': true, 'declined': declined,
        'declineReason': declined ? 'Die $name bietet erst ab Bonität „gut“ an.' : null, 'recommended': recommended,
        'providerId': 'SIMULATION', 'simulation': true, 'effectiveRate': eff, 'feesCents': 0,
        'minCreditClass': minClass, 'minCreditClassLabel': minClass.toLowerCase(), 'eligible': !declined,
        'requirements': ['Bonität mindestens „mittel“ (Selbsteinschätzung)', 'Regelmäßige Einnahmen im Girokonto'],
        'conditions': ['Keine Bearbeitungsgebühr', 'Sondertilgungen jederzeit kostenlos'],
        'exampleText': 'Unverbindliches Rechenbeispiel (Simulation, fiktive Bank): Nettodarlehensbetrag 10.000,00 €, '
            'Laufzeit 48 Monate, gebundener Sollzins 5,49 % p. a., effektiver Jahreszins 5,63 %, monatliche Rate '
            '232,52 €, Gesamtbetrag 11.160,94 €.',
        'marketDeltaPercent': -3.36,
      };

  static Map<String, dynamic> offerResult({int hidden = 0}) => {
        'purpose': 'AUTO', 'purposeLabel': 'Auto', 'principalCents': 1000000, 'equityCents': 0, 'termMonths': 48,
        'hasData': true, 'availableCents': 78383, 'maxRateCents': 54868, 'maxPrincipalCents': 2359719,
        'offers': [
          loanOffer('NORDLICHT', 'Nordlicht Kredit', 5.49, 5.63, 23252, 1116094, recommended: true),
          loanOffer('MAINUFER', 'Mainufer Bank', 5.29, 5.42, 23161, 1111724, declined: true, minClass: 'GUT'),
        ],
        'creditClass': 'MITTEL', 'creditClassLabel': 'mittel', 'hiddenCount': hidden,
        'referenceRates': [
          {'key': 'CONSUMER_ALL', 'label': 'Konsumentenkredite: effektiver Jahreszins inkl. Kosten', 'ratePercent': 8.99,
           'period': '2026-08', 'overall': true, 'source': 'Deutsche Bundesbank, MFI-Zinsstatistik (Neugeschäft)',
           'sourceUrl': 'https://www.bundesbank.de', 'fetchedAt': '2026-10-13T06:00:00Z', 'stale': false,
           'note': 'Durchschnitt neu abgeschlossener Kredite in Deutschland – kein Angebot.'},
        ],
        'providers': [
          {'id': 'SIMULATION', 'name': 'Fiktive Banken (Simulation)', 'kind': 'OFFERS', 'simulation': true, 'state': 'OK',
           'stateLabel': 'Verfügbar', 'message': 'Rechenbeispiele fiktiver Banken – keine echten Angebote.', 'updatedAt': null},
          {'id': 'PARTNER', 'name': 'Echte Kreditangebote', 'kind': 'PARTNER', 'simulation': false,
           'state': 'NOT_CONNECTED', 'stateLabel': 'Nicht angebunden', 'message': 'Nicht angebunden.', 'updatedAt': null},
        ],
        'disclaimerText': 'Alle Angebote stammen von fiktiven Banken (Simulation der Projektbank). Es sind unverbindliche '
            'Rechenbeispiele – keine echten Kreditangebote, keine Abfrage bei einer Auskunftei, keine Auszahlung echten Geldes.',
      };

  static Map<String, dynamic> loanApplication({int id = 51, String status = 'APPROVED'}) => {
        'id': id, 'provider': 'SIMULATION', 'simulation': true, 'bank': 'NORDLICHT', 'bankName': 'Nordlicht Kredit',
        'purpose': 'AUTO', 'purposeLabel': 'Auto', 'principalCents': 1000000, 'equityCents': 0, 'termMonths': 48,
        'creditClass': 'GUT', 'creditClassLabel': 'gut',
        'interestRate': status == 'DECLINED' ? null : 5.49, 'effectiveRate': status == 'DECLINED' ? null : 5.63,
        'monthlyRateCents': status == 'DECLINED' ? null : 23252, 'totalCents': status == 'DECLINED' ? null : 1116094,
        'status': status,
        'statusLabel': switch (status) {
          'APPROVED' => 'Zusage',
          'DECLINED' => 'Abgelehnt',
          'ACCEPTED' => 'Vertrag geschlossen',
          'WITHDRAWN' => 'Zurückgezogen',
          _ => 'Abgelaufen',
        },
        'decisionReason': status == 'DECLINED'
            ? 'Rate zu hoch: höchstens 70 % von 783,83 € möglich.'
            : 'Zusage (Simulation) – gültig bis 27.10.2026. Noch kein Vertrag.',
        'validUntil': status == 'APPROVED' ? '2026-10-27' : null, 'loanId': status == 'ACCEPTED' ? 7 : null,
        'createdAt': '2026-10-13T10:00:00Z',
        'nextStepText': status == 'APPROVED'
            ? 'Nächster Schritt: Vertrag abschließen. Bis dahin ist nichts verbindlich.'
            : 'Andere Bank, kleineren Betrag oder längere Laufzeit wählen.',
      };

  static Map<String, dynamic> standingOrder({String status = 'ACTIVE'}) => {
        'id': 3, 'recipientName': 'Ben Beispiel', 'recipientIban': 'DE12000100010000000042', 'amountCents': 5000,
        'reference': 'Miete Anteil', 'executionDay': 5, 'intervalMonths': 1, 'intervalLabel': 'monatlich',
        'startDate': '2026-11-05', 'endDate': null, 'status': status, 'nextExecutionDate': '2026-11-05',
        'lastExecutedMonth': null,
      };

  static Map<String, dynamic> entry({
    int id = 21,
    String kind = 'INCOME',
    String title = 'Gehalt Firma Main GmbH & Co. KG (Hauptjob)',
    int interval = 1,
    String status = 'ACTIVE',
    int amount = 265000,
  }) =>
      {
        'id': id, 'kind': kind, 'title': title, 'category': kind == 'INCOME' ? 'GEHALT' : 'WOHNEN',
        'categoryLabel': kind == 'INCOME' ? 'Gehalt' : 'Wohnen', 'amountCents': amount, 'intervalMonths': interval,
        'intervalLabel': interval == 0 ? 'einmalig' : 'monatlich', 'startDate': '2026-07-01', 'endDate': null,
        'status': status, 'nextDate': interval == 0 ? null : '2026-11-01', 'lastBookedDate': '2026-10-01',
        'bookedCount': interval == 0 ? 1 : 4,
        'monthlyCents': interval == 0 ? 0 : (kind == 'INCOME' ? amount : -amount),
      };

  static Map<String, dynamic> entriesSummary() => {
        'monthlyIncomeCents': 265000, 'monthlyExpenseCents': 90000, 'monthlyBalanceCents': 175000,
        'entries': [
          entry(),
          entry(id: 22, kind: 'EXPENSE', title: 'Miete', amount: 90000),
          entry(id: 23, kind: 'EXPENSE', title: 'Handyvertrag', amount: 1999, status: 'PAUSED'),
          entry(id: 24, kind: 'EXPENSE', title: 'Urlaub Mallorca mit der ganzen Familie im Sommer', amount: 145000,
              interval: 0, status: 'DONE'),
        ],
      };

  static List<Map<String, dynamic>> entryCategories() => [
        {'key': 'GEHALT', 'label': 'Gehalt', 'kind': 'INCOME'},
        {'key': 'SONSTIGE_EINNAHMEN', 'label': 'Sonstige Einnahmen', 'kind': 'INCOME'},
        {'key': 'WOHNEN', 'label': 'Wohnen', 'kind': 'EXPENSE'},
        {'key': 'VERTRAEGE', 'label': 'Verträge & Abos', 'kind': 'EXPENSE'},
        {'key': 'FREIZEIT', 'label': 'Freizeit', 'kind': 'EXPENSE'},
      ];

  // ------------------------------------------------------------------ Echte Bankkonten (Sandbox-Daten)

  static Map<String, dynamic> bankAccount(int id, int connectionId, String name, String bank,
          {bool sandbox = true, int balance = 146771, String status = 'ACTIVE', String sync = 'OK'}) =>
      {
        'id': id, 'connectionId': connectionId, 'bankName': bank, 'name': name, 'ibanMasked': 'DE40 •••• 19${id % 100}',
        'currency': 'EUR', 'balanceCents': balance, 'balanceType': 'closingBooked', 'balanceAt': '2026-10-13T08:00:00Z',
        'lastSyncAt': '2026-10-13T08:00:00Z', 'lastSyncStatus': sync, 'connectionStatus': status, 'sandbox': sandbox,
      };

  static List<Map<String, dynamic>> bankConnections() => [
        {
          'id': 31, 'provider': 'SANDBOX', 'sandbox': true, 'institutionId': 'SANDBOX_MAINBANK',
          'bankName': 'Sandbox-Hausbank (Testdaten)', 'status': 'ACTIVE', 'statusLabel': 'Verbunden',
          'permissions': ['Kontodetails lesen', 'Salden lesen', 'Umsätze lesen'],
          'consentExpiresAt': '2027-01-10T08:00:00Z', 'daysLeft': 89, 'lastSyncAt': '2026-10-13T08:00:00Z',
          'lastSyncStatus': 'OK', 'lastErrorMessage': null, 'createdAt': '2026-10-01T08:00:00Z', 'revokedAt': null,
          'accounts': [
            bankAccount(501, 31, 'Gehaltskonto bei der Hausbank mit langem Namen', 'Sandbox-Hausbank (Testdaten)'),
            bankAccount(502, 31, 'Tagesgeld', 'Sandbox-Hausbank (Testdaten)', balance: 2262625),
          ],
        },
        {
          'id': 32, 'provider': 'GOCARDLESS', 'sandbox': false, 'institutionId': 'SPK_MUSTER',
          'bankName': 'Sparkasse Musterstadt-Langname Filiale Innenstadt', 'status': 'EXPIRED',
          'statusLabel': 'Freigabe abgelaufen', 'permissions': [], 'consentExpiresAt': '2026-10-10T08:00:00Z',
          'daysLeft': null, 'lastSyncAt': '2026-10-09T08:00:00Z', 'lastSyncStatus': 'FAILED',
          'lastErrorMessage': 'Die Bank war nicht erreichbar. Nächster Versuch später.',
          'createdAt': '2026-07-01T08:00:00Z', 'revokedAt': null,
          'accounts': [
            bankAccount(503, 32, 'Privatkonto', 'Sparkasse Musterstadt-Langname Filiale Innenstadt',
                sandbox: false, balance: -12050, status: 'EXPIRED', sync: 'FAILED'),
          ],
        },
      ];

  static Map<String, dynamic> bankOverview() => {
        'accounts': [for (final c in bankConnections()) ...(c['accounts'] as List)],
        'totalBalanceCents': 3717346, 'monthIncomeCents': 265000, 'monthExpenseCents': 118825,
        'anySandbox': true, 'anyStale': true,
      };

  static Map<String, dynamic> bankTx(int id,
          {String status = 'BOOKED', int amount = -4530, String counterparty = 'REWE Markt Frankfurt-Bockenheim',
          bool reversal = false, String category = 'LEBENSMITTEL', String label = 'Lebensmittel'}) =>
      {
        'id': id, 'accountId': 501, 'accountName': 'Gehaltskonto', 'bankName': 'Sandbox-Hausbank (Testdaten)',
        'status': status,
        'statusLabel': const {
          'PENDING': 'Vorgemerkt', 'BOOKED': 'Gebucht', 'CANCELLED': 'Storniert', 'REVERSED': 'Zurückgebucht',
        }[status],
        'bookingDate': '2026-10-${(id % 28 + 1).toString().padLeft(2, '0')}', 'valueDate': '2026-10-12',
        'amountCents': amount, 'currency': 'EUR', 'counterparty': counterparty,
        'remittance': 'Kartenzahlung Filiale 12 Einkauf vom Wochenende mit sehr langem Verwendungszweck',
        'reversal': reversal, 'relatedId': null, 'autoCategory': category, 'category': category,
        'categoryLabel': label, 'note': null, 'tags': [], 'excluded': false, 'edited': false, 'sandbox': true,
      };

  static Map<String, dynamic> bankTxPage() => {
        'items': [
          bankTx(601, status: 'PENDING', amount: -320, counterparty: 'Bäckerei Huber'),
          bankTx(602),
          bankTx(603, status: 'CANCELLED', amount: -12000, counterparty: 'Hotel am Main', category: 'FREIZEIT', label: 'Freizeit'),
          bankTx(604, status: 'REVERSED', amount: -2990, counterparty: 'FitX Fitnessstudio', category: 'VERTRAEGE',
              label: 'Verträge & Abos'),
          bankTx(605, amount: 2990, counterparty: 'FitX Fitnessstudio', reversal: true, category: 'VERTRAEGE',
              label: 'Verträge & Abos'),
          bankTx(606, amount: 265000, counterparty: 'Muster GmbH', category: 'GEHALT', label: 'Gehalt'),
        ],
        'page': 0, 'size': 30, 'hasMore': false,
      };

  static List<Map<String, dynamic>> sandboxInstitutions() => [
        for (final b in [
          ('SANDBOX_MAINBANK', 'Sandbox-Hausbank (Testdaten)'),
          ('SANDBOX_ONLINEBANK', 'Sandbox-Onlinebank (Testdaten)'),
          ('SANDBOX_DOWN', 'Sandbox-Bank nicht erreichbar (Testdaten)'),
        ])
          {'id': b.$1, 'name': b.$2, 'bic': 'SANDDEFFXXX', 'historyDays': 730, 'accessDays': 90, 'logo': null,
           'sandbox': true},
      ];

  static Map<String, Object? Function(RequestOptions)> defaultRoutes() => {
        'GET /api/banking/accounts': (_) => bankOverview(),
        'GET /api/banking/connections': (_) => bankConnections(),
        'GET /api/banking/providers': (_) => [
              {'id': 'SANDBOX', 'name': 'Sandbox (Testdaten, keine echte Bank)', 'sandbox': true, 'available': true,
               'description': 'Simulierte Banken mit Testdaten – keine echte Bank, kein echtes Geld.'},
            ],
        'GET /api/banking/institutions': (_) => sandboxInstitutions(),
        'GET /api/banking/transactions': (_) => bankTxPage(),
        'GET /api/banking/transactions/*': (o) => bankTx(int.parse(o.path.split('/').last)),
        'GET /api/me': (_) => user(),
        'GET /api/accounts': (_) => accounts(),
        'GET /api/accounts/*/bookings': (_) => bookingPage(),
        'GET /api/accounts/products': (_) => bankProducts(),
        'POST /api/accounts/move': (_) => const FakeResponse(204),
        'GET /api/transactions': (_) => transactionsPage(),
        'GET /api/contacts': (_) => [
              {'id': 1, 'userId': 2, 'displayName': 'Ben', 'fullName': 'Ben Beispiel', 'iban': 'DE12000100010000000042'},
            ],
        'GET /api/cards/me': (_) => {
              'id': 1, 'holderName': 'ERIKA MUSTERMANN', 'maskedNumber': '•••• •••• •••• 4242', 'expiry': '10/30',
              'status': 'ACTIVE', 'dailyLimitCents': 100000, 'maxDailyLimitCents': 200000, 'onlineEnabled': true,
              'spentTodayCents': 2399,
            },
        'GET /api/notifications': (_) => {
              'items': [
                {'id': 1, 'type': 'TRANSFER_IN', 'title': 'Geld erhalten', 'body': '+42,00 € von Ben: Pizza',
                 'read': false, 'createdAt': '2026-10-12T18:00:00Z', 'bookingId': 100},
              ],
              'page': 0, 'size': 20, 'hasMore': false,
            },
        'GET /api/notifications/unread-count': (_) => {'count': 1},
        'GET /api/insights/months': (_) => months(),
        'GET /api/insights/overview': (_) => overview(),
        'GET /api/insights/net-worth': (_) =>
            {'accountsCents': 647950, 'depotCents': 22710, 'loansCents': -1971031, 'totalCents': -1300371},
        'GET /api/clock': (_) => {'date': '2026-10-13', 'offsetDays': 0},
        'GET /api/dashboard': (_) => dashboard(),
        'GET /api/plans': (_) => [planSummary()],
        'GET /api/plans/*': (_) => planDetail(),
        'GET /api/loans': (_) => [loan()],
        'GET /api/loans/applications': (_) => [loanApplication()],
        'POST /api/loans/schedule': (_) => FakeResponse(200, [
              for (var m = 1; m <= 12; m++)
                {'month': m, 'dueDate': '2027-${m.toString().padLeft(2, '0')}-13', 'rateCents': 23252,
                 'interestCents': 4575 - m * 50, 'principalCents': 18677 + m * 50, 'remainingCents': 1000000 - m * 19000,
                 'paid': false, 'extraCents': 0},
            ]),
        'GET /api/loans/applications/*': (_) => loanApplication(),
        'GET /api/allocations/accounts/*': (_) => accountAllocations(),
        'GET /api/allocations/sources': (_) => allocationSources(),
        'GET /api/loans/*': (_) => loanDetail(),
        'GET /api/standing-orders': (_) => [standingOrder()],
        'GET /api/entries': (_) => entriesSummary(),
        'PUT /api/me/language': (_) => const FakeResponse(204),
        'GET /api/entries/categories': (_) => entryCategories(),
        'GET /api/imports': (_) => [],
        'GET /api/subaccounts/*/members': (_) => [
              {'userId': 1, 'name': 'Erika Mustermann', 'owner': true, 'you': true, 'role': 'OWNER'},
              {'userId': 2, 'name': 'Ben Beispiel', 'owner': false, 'you': false, 'role': 'FULL'},
            ],
      };
}
