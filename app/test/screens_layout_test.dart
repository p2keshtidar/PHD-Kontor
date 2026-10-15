import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/accounts/account_home_screen.dart';
import 'package:girokonto_app/features/accounts/transactions_screen.dart';
import 'package:girokonto_app/features/auth/login_screen.dart';
import 'package:girokonto_app/features/auth/register_screen.dart';
import 'package:girokonto_app/features/banking/bank_accounts_screen.dart';
import 'package:girokonto_app/features/banking/bank_transaction_screen.dart';
import 'package:girokonto_app/features/banking/bank_transactions_screen.dart';
import 'package:girokonto_app/features/banking/connect_bank_screen.dart';
import 'package:girokonto_app/features/card/card_screen.dart';
import 'package:girokonto_app/features/contacts/address_book_screen.dart';
import 'package:girokonto_app/features/assistant/assistant_screen.dart';
import 'package:girokonto_app/features/contracts/contracts_screen.dart';
import 'package:girokonto_app/features/dashboard/dashboard_screen.dart';
import 'package:girokonto_app/features/groups/group_screen.dart';
import 'package:girokonto_app/features/groups/groups_screen.dart';
import 'package:girokonto_app/features/multibank/all_transactions_screen.dart';
import 'package:girokonto_app/features/multibank/duplicates_screen.dart';
import 'package:girokonto_app/features/planning/planning_screen.dart';
import 'package:girokonto_app/features/market/applications_screen.dart';
import 'package:girokonto_app/features/market/market_screen.dart';
import 'package:girokonto_app/features/insights/csv_import_screen.dart';
import 'package:girokonto_app/features/insights/insights_screen.dart';
import 'package:girokonto_app/features/loans/extra_payment_screen.dart';
import 'package:girokonto_app/features/loans/loan_application_screen.dart';
import 'package:girokonto_app/features/loans/loan_detail_screen.dart';
import 'package:girokonto_app/features/loans/loan_offer_screen.dart';
import 'package:girokonto_app/features/loans/loan_models.dart';
import 'package:girokonto_app/features/loans/loan_planner_screen.dart';
import 'package:girokonto_app/features/notifications/notifications_screen.dart';
import 'package:girokonto_app/features/profile/profile_screen.dart';
import 'package:girokonto_app/features/profile/two_factor_screen.dart';
import 'package:girokonto_app/features/savings/plan_detail_screen.dart';
import 'package:girokonto_app/features/savings/plan_wizard_screen.dart';
import 'package:girokonto_app/features/savings/savings_home_screen.dart';
import 'package:girokonto_app/features/entries/entries_screen.dart';
import 'package:girokonto_app/features/entries/entry_form_screen.dart';
import 'package:girokonto_app/features/standing_orders/standing_order_form_screen.dart';
import 'package:girokonto_app/features/standing_orders/standing_orders_screen.dart';
import 'package:girokonto_app/features/subaccounts/subaccount_screen.dart';
import 'package:girokonto_app/features/transfer/transfer_screen.dart';

import 'package:girokonto_app/l10n/l10n.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

/// Jeder Bildschirm in vier Formaten: kein Überlauf, keine Ausnahme.
/// Kleines Handy (320 dp, z. B. iPhone SE 1), normales Handy, Tablet quer, große Schrift (150 %).
void main() {
  final screens = <String, Widget Function()>{
    'Login': () => const LoginScreen(),
    'Registrierung': () => const RegisterScreen(),
    'Startseite': () => const AccountHomeScreen(),
    'Umsätze': () => const TransactionsScreen(accountId: 10),
    'Überweisen': () => const TransferScreen(),
    'Adressbuch': () => const AddressBookScreen(),
    'Karte': () => const CardScreen(),
    'Unterkonto (eigenes, geteilt)': () => const SubaccountScreen(accountId: 11),
    'Unterkonto (fremdes)': () => const SubaccountScreen(accountId: 12),
    'Benachrichtigungen': () => const NotificationsScreen(),
    'Finanzüberblick': () => const InsightsScreen(),
    'Finanz-Dashboard': () => const DashboardScreen(),
    'Alle Umsätze': () => const AllTransactionsScreen(),
    'Mögliche Duplikate': () => const DuplicatesScreen(),
    'Budgets & Prognose': () => const PlanningScreen(),
    'Finanz-Marktplatz': () => const MarketScreen(),
    'Meine Anträge': () => const ApplicationsScreen(),
    'Gemeinsame Ausgaben': () => const GroupsScreen(),
    'Finanzassistent': () => const AssistantScreen(),
    'Verträge': () => const ContractsScreen(),
    'Vertrag': () => const ContractDetailScreen(contractId: 5),
    'Vertrag anlegen': () => const ContractFormScreen(),
    'Gruppe (WG)': () => const GroupScreen(groupId: 1),
    'CSV-Import': () => const CsvImportScreen(),
    'Sparen': () => const SavingsHomeScreen(),
    'Plan-Detail': () => const PlanDetailScreen(planId: 5),
    'Sparplan-Assistent': () => const PlanWizardScreen(type: 'GOAL'),
    'Investplan-Assistent': () => const PlanWizardScreen(type: 'INVEST'),
    'Kreditplaner': () => const LoanPlannerScreen(),
    'Kredit-Detail': () => const LoanDetailScreen(loanId: 7),
    'Kreditangebot (Anfrage)': () {
      final r = OfferResult.fromJson(FakeBackend.offerResult());
      return LoanOfferScreen(selected: SelectedOffer(r, r.offers.first));
    },
    'Kreditanfrage (Zusage)': () => const LoanApplicationScreen(applicationId: 51),
    'Sondertilgung': () => ExtraPaymentScreen(loan: Loan.fromJson(FakeBackend.loan())),
    'Daueraufträge': () => const StandingOrdersScreen(),
    'Neuer Dauerauftrag': () => const StandingOrderFormScreen(),
    'Zwei-Faktor': () => const TwoFactorScreen(),
    'Profil': () => const ProfileScreen(),
    'Einnahmen & Ausgaben': () => const EntriesScreen(),
    'Einnahme eintragen': () => const EntryFormScreen(kind: 'INCOME'),
    'Ausgabe eintragen': () => const EntryFormScreen(kind: 'EXPENSE'),
    'Echte Bankkonten': () => const BankAccountsScreen(),
    'Bank verbinden': () => const ConnectBankScreen(),
    'Umsätze verbundener Konten': () => const BankTransactionsScreen(),
    'Umsatz eines verbundenen Kontos': () => const BankTransactionScreen(id: 604),
  };

  final formats = <String, (Size, double)>{
    'klein 320×568': (const Size(320, 568), 1.0),
    'Handy 412×915': (const Size(412, 915), 1.0),
    'Tablet 1280×800': (const Size(1280, 800), 1.0),
    'große Schrift 150 %': (const Size(375, 812), 1.5),
  };

  // Englisch: alle Bildschirme auf einem normalen Handy (Texte sind oft länger)
  group('Englisch', () {
    for (final s in screens.entries) {
      testWidgets(s.key, (tester) async {
        final errors = <String>[];
        final previous = FlutterError.onError;
        FlutterError.onError = (d) => errors.add(d.toString());
        addTearDown(() => FlutterError.onError = previous);
        L10n.language.value = 'en';
        await pumpScreen(tester, s.value(), size: const Size(360, 740), settle: false);
        for (var i = 0; i < 10; i++) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        FlutterError.onError = previous;
        expect(errors, isEmpty, reason: errors.join('\n----\n'));
        expect(find.byType(ErrorWidget), findsNothing);
      });
    }
  });

  for (final s in screens.entries) {
    group(s.key, () {
      for (final f in formats.entries) {
        testWidgets(f.key, (tester) async {
          // Fehler mit allen Details sammeln (welches Widget, welche Datei/Zeile)
          final errors = <String>[];
          final previous = FlutterError.onError;
          FlutterError.onError = (d) => errors.add(d.toString());
          addTearDown(() => FlutterError.onError = previous);
          await pumpScreen(tester, s.value(), size: f.value.$1, textScale: f.value.$2, settle: false);
          // ein paar Frames für Ladevorgänge und Animationen
          for (var i = 0; i < 10; i++) {
            await tester.pump(const Duration(milliseconds: 100));
          }
          FlutterError.onError = previous;
          expect(errors, isEmpty, reason: errors.join('\n----\n'));
          expect(tester.takeException(), isNull);
          expect(find.byType(ErrorWidget), findsNothing);
        });
      }
    });
  }
}
