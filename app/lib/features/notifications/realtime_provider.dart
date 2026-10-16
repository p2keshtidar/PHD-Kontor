import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/messenger.dart';
import '../../core/realtime_service.dart';
import '../../core/token_storage.dart';
import '../accounts/accounts_repository.dart';
import '../accounts/bookings_repository.dart';
import '../allocations/allocations_repository.dart';
import '../card/card_repository.dart';
import '../creditcard/credit_card_section.dart';
import '../contracts/contracts_repository.dart';
import '../dashboard/dashboard_repository.dart';
import '../groups/groups_repository.dart';
import '../health/health_score_card.dart';
import '../insights/insights_repository.dart';
import '../loans/loans_repository.dart';
import '../planning/planning_repository.dart';
import '../savings/plans_repository.dart';
import 'background_check.dart';
import 'local_notifications.dart';
import 'notifications_repository.dart';

/// Startet die Live-Verbindung, solange der Nutzer angemeldet im Hauptbereich ist.
/// Bei jeder Meldung werden Kontostand, Umsätze, Karte und Zähler neu geladen.
final realtimeProvider = Provider.autoDispose<RealtimeService>((ref) {
  final service = RealtimeService(
    api: ref.watch(apiClientProvider),
    storage: ref.watch(tokenStorageProvider),
    onNotification: (n) {
      ref.invalidate(unreadCountProvider);
      ref.invalidate(notificationsProvider);
      ref.invalidate(accountsProvider);
      ref.invalidate(recentBookingsProvider);
      ref.invalidate(cardProvider);
      ref.invalidate(plansProvider);
      ref.invalidate(loansProvider);
      ref.invalidate(loanApplicationsProvider);
      ref.invalidate(accountAllocationsProvider);
      ref.invalidate(overviewProvider);
      ref.invalidate(netWorthProvider);
      ref.invalidate(dashboardProvider);
      ref.invalidate(budgetsProvider);
      ref.invalidate(creditCardProvider);
      ref.invalidate(healthScoreProvider);
      ref.invalidate(groupsProvider);
      ref.invalidate(contractsProvider);
      ref.invalidate(groupDetailProvider);
      ref.invalidate(forecastProvider);
      ref.invalidate(monthsProvider);

      BackgroundCheck.markShown(n.id); // /K070/: im Hintergrund nicht noch einmal melden

      // Unbekannter Zustand (z. B. Browser vor dem ersten Ereignis) zählt als Vordergrund
      final lifecycle = WidgetsBinding.instance.lifecycleState;
      final inForeground = lifecycle == null || lifecycle == AppLifecycleState.resumed;
      if (inForeground) {
        showNow(rootMessengerKey.currentState, SnackBar(
          content: Text('${n.title}: ${n.body}'),
          behavior: SnackBarBehavior.floating,
        ));
      } else {
        LocalNotifications.show(n.id, n.title, n.body);
      }
    },
  );
  // Live-Verbindung sofort starten: auf iOS wartet init() auf die Antwort zur
  // Benachrichtigungs-Erlaubnis – solange darf die Verbindung nicht blockiert sein.
  service.start();
  LocalNotifications.init();
  BackgroundCheck.start(); // /K070/ Android: auch bei geschlossener App nach Meldungen schauen
  ref.onDispose(service.stop);
  return service;
});
