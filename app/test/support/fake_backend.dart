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
         'balanceCents': 120000, 'shared': true, 'ownerName': null, 'memberCount': 2},
        {'id': 12, 'type': 'SUB', 'name': 'WG-Kasse', 'icon': 'home', 'iban': null,
         'balanceCents': 4500, 'shared': true, 'ownerName': 'Mia Musterfrau-Langname', 'memberCount': 3},
      ];

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

  static Map<String, dynamic> loan({int extraPaid = 0}) => {
        'id': 7, 'bank': 'NORDLICHT', 'bankName': 'Nordlicht Kredit', 'purpose': 'AUTO', 'purposeLabel': 'Auto',
        'principalCents': 2000000, 'equityCents': 0, 'interestRate': 5.59, 'termMonths': 60, 'monthlyRateCents': 38286,
        'startDate': '2026-10-12', 'nextDueDate': '2026-11-12', 'paidInstallments': 1, 'remainingCents': 1971031,
        'progressPercent': 1.4, 'status': 'ACTIVE', 'accountId': 14, 'remainingInstallments': 59,
        'expectedEndDate': '2031-10-12', 'extraPaidCents': extraPaid,
      };

  static Map<String, dynamic> loanDetail({int extraPaid = 0}) => {
        'loan': loan(extraPaid: extraPaid), 'totalInterestCents': 297160, 'interestSavedCents': extraPaid > 0 ? 12345 : 0,
        'schedule': [
          for (var m = 1; m <= 60; m++)
            {'month': m, 'dueDate': '2026-${((10 + m - 1) % 12 + 1).toString().padLeft(2, '0')}-12', 'rateCents': 38286,
             'interestCents': 9317, 'principalCents': 28969, 'remainingCents': 2000000 - m * 33000,
             'paid': m == 1, 'extraCents': m == 1 ? extraPaid : 0},
        ],
        'extraPayments': [],
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
        'GET /api/plans': (_) => [planSummary()],
        'GET /api/plans/*': (_) => planDetail(),
        'GET /api/loans': (_) => [loan()],
        'GET /api/loans/*': (_) => loanDetail(),
        'GET /api/standing-orders': (_) => [standingOrder()],
        'GET /api/entries': (_) => entriesSummary(),
        'PUT /api/me/language': (_) => const FakeResponse(204),
        'GET /api/entries/categories': (_) => entryCategories(),
        'GET /api/imports': (_) => [],
        'GET /api/subaccounts/*/members': (_) => [
              {'userId': 1, 'name': 'Erika Mustermann', 'owner': true, 'you': true},
              {'userId': 2, 'name': 'Ben Beispiel', 'owner': false, 'you': false},
            ],
      };
}
