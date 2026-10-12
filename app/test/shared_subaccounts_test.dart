import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/accounts/account.dart';
import 'package:girokonto_app/features/subaccounts/subaccounts_repository.dart';

/// /K060/ Geteilte Unterkonten
void main() {
  test('Eigenes Unterkonto, mit zwei Personen geteilt', () {
    final a = Account.fromJson({
      'id': 9, 'type': 'SUB', 'name': 'WG-Kasse', 'icon': 'home', 'iban': null, 'balanceCents': 5000,
      'shared': true, 'ownerName': null, 'memberCount': 2,
    });
    expect(a.shared, isTrue);
    expect(a.isForeign, isFalse);
    expect(a.memberCount, 2);
  });

  test('Fremdes Unterkonto, mit mir geteilt', () {
    final a = Account.fromJson({
      'id': 9, 'type': 'SUB', 'name': 'WG-Kasse', 'balanceCents': 5000,
      'shared': true, 'ownerName': 'Mia Muster', 'memberCount': 1,
    });
    expect(a.isForeign, isTrue);
    expect(a.ownerName, 'Mia Muster');
  });

  test('Ältere Antwort ohne Teilen-Felder', () {
    final a = Account.fromJson({'id': 1, 'type': 'MAIN', 'name': 'Girokonto', 'balanceCents': 100});
    expect(a.shared, isFalse);
    expect(a.memberCount, 0);
  });

  test('Mitglied aus JSON', () {
    final m = SubaccountMember.fromJson({'userId': 3, 'name': 'Nico N.', 'owner': false, 'you': true});
    expect(m.you, isTrue);
    expect(m.owner, isFalse);
  });
}
