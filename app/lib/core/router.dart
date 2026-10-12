import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/accounts/account_home_screen.dart';
import '../features/accounts/booking_detail_screen.dart';
import '../features/accounts/booking_item.dart';
import '../features/accounts/transactions_screen.dart';
import '../features/auth/app_user.dart';
import '../features/auth/auth_controller.dart';
import '../features/auth/login_screen.dart';
import '../features/auth/register_screen.dart';
import '../features/card/card_screen.dart';
import '../features/contacts/address_book_screen.dart';
import '../features/insights/insights_screen.dart';
import '../features/loans/loan_detail_screen.dart';
import '../features/loans/loan_models.dart';
import '../features/loans/loan_offer_screen.dart';
import '../features/loans/loan_planner_screen.dart';
import '../features/savings/plan_detail_screen.dart';
import '../features/savings/plan_wizard_screen.dart';
import '../features/savings/savings_home_screen.dart';
import '../features/notifications/notifications_screen.dart';
import '../features/profile/profile_screen.dart';
import '../features/shell/home_shell.dart';
import '../features/shell/splash_screen.dart';
import '../features/subaccounts/subaccount_screen.dart';
import '../features/transfer/transfer_models.dart';
import '../features/transfer/transfer_screen.dart';
import '../features/transfer/transfer_success_screen.dart';

/// Alle Routen der App. Der Redirect sorgt dafür, dass nicht angemeldete Nutzer
/// immer auf /login landen und angemeldete nie dort.
final routerProvider = Provider<GoRouter>((ref) {
  final authState = ValueNotifier<AsyncValue<AppUser?>>(const AsyncLoading());
  ref.listen<AsyncValue<AppUser?>>(
    authControllerProvider,
    (_, next) => authState.value = next,
    fireImmediately: true,
  );
  ref.onDispose(authState.dispose);

  return GoRouter(
    initialLocation: '/konto',
    refreshListenable: authState,
    redirect: (context, state) {
      final auth = authState.value;
      final location = state.matchedLocation;

      if (auth.isLoading && !auth.hasValue) {
        // Sitzung wird beim App-Start noch geprüft
        return location == '/splash' ? null : '/splash';
      }
      final loggedIn = auth.valueOrNull != null;
      final onAuthPage = location == '/login' || location == '/register';

      if (!loggedIn) return onAuthPage ? null : '/login';
      if (onAuthPage || location == '/splash') return '/konto';
      return null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (_, __) => const SplashScreen()),
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/register', builder: (_, __) => const RegisterScreen()),
      // Vollbild-Seiten (ohne untere Navigation), werden mit context.push geöffnet
      GoRoute(path: '/profil', builder: (_, __) => const ProfileScreen()),
      GoRoute(
        path: '/umsaetze/:accountId',
        builder: (_, state) =>
            TransactionsScreen(accountId: int.parse(state.pathParameters['accountId']!)),
      ),
      GoRoute(
        path: '/umsatz',
        builder: (_, state) => BookingDetailScreen(item: state.extra as BookingItem?),
      ),
      GoRoute(
        path: '/adressbuch',
        builder: (_, state) =>
            AddressBookScreen(selectMode: state.uri.queryParameters['select'] == '1'),
      ),
      GoRoute(
        path: '/unterkonto/:accountId',
        builder: (_, state) =>
            SubaccountScreen(accountId: int.parse(state.pathParameters['accountId']!)),
      ),
      GoRoute(path: '/benachrichtigungen', builder: (_, __) => const NotificationsScreen()),
      // Phase 4: Finanzüberblick, Pläne, Kredite
      GoRoute(path: '/finanzen', builder: (_, __) => const InsightsScreen()),
      GoRoute(
        path: '/plan/neu',
        builder: (_, state) => PlanWizardScreen(type: state.uri.queryParameters['type'] == 'INVEST' ? 'INVEST' : 'GOAL'),
      ),
      GoRoute(
        path: '/plan/:planId',
        builder: (_, state) => PlanDetailScreen(planId: int.parse(state.pathParameters['planId']!)),
      ),
      GoRoute(path: '/kredit/neu', builder: (_, __) => const LoanPlannerScreen()),
      GoRoute(
        path: '/kredit/angebot',
        builder: (_, state) => LoanOfferScreen(selected: state.extra as SelectedOffer?),
      ),
      GoRoute(
        path: '/kredit/:loanId',
        builder: (_, state) => LoanDetailScreen(loanId: int.parse(state.pathParameters['loanId']!)),
      ),
      GoRoute(
        path: '/ueberweisung-erfolg',
        builder: (_, state) => TransferSuccessScreen(result: state.extra as TransferResult?),
      ),
      StatefulShellRoute.indexedStack(
        builder: (_, __, shell) => HomeShell(shell: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(
                path: '/konto',
                builder: (_, __) => const AccountHomeScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/ueberweisen', builder: (_, __) => const TransferScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/sparen', builder: (_, __) => const SavingsHomeScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/karte', builder: (_, __) => const CardScreen()),
          ]),
        ],
      ),
    ],
  );
});
