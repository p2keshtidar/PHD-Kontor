import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'loan_models.dart';

class LoansRepository {
  LoansRepository(this._api);

  final ApiClient _api;

  /// Schritt 1: Vergleich (unverbindliche Rechenbeispiele) mit Bonität und Filtern.
  Future<OfferResult> offers(String purpose, int principalCents, int equityCents, int termMonths,
      {String creditClass = 'GUT', int? maxMonthlyRateCents, int? maxTotalCents, bool onlyEligible = false}) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/loans/offers', data: {
      'purpose': purpose,
      'principalCents': principalCents,
      'equityCents': equityCents,
      'termMonths': termMonths,
      'creditClass': creditClass,
      if (maxMonthlyRateCents != null) 'maxMonthlyRateCents': maxMonthlyRateCents,
      if (maxTotalCents != null) 'maxTotalCents': maxTotalCents,
      if (onlyEligible) 'onlyEligible': true,
    });
    return OfferResult.fromJson(res.data!);
  }

  /// Schritt 2: unverbindliche Anfrage bei der (fiktiven) Bank – nur mit Einwilligung.
  Future<LoanApplication> request(String bank, String purpose, int principalCents, int equityCents, int termMonths,
      {String creditClass = 'GUT'}) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/loans/applications', data: {
      'bank': bank,
      'purpose': purpose,
      'principalCents': principalCents,
      'equityCents': equityCents,
      'termMonths': termMonths,
      'creditClass': creditClass,
      'consent': true,
    });
    return LoanApplication.fromJson(res.data!);
  }

  Future<List<LoanApplication>> applications() async {
    final res = await _api.dio.get<List<dynamic>>('/api/loans/applications');
    return res.data!.map((e) => LoanApplication.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<LoanApplication> application(int id) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/loans/applications/$id');
    return LoanApplication.fromJson(res.data!);
  }

  Future<void> withdraw(int applicationId) => _api.dio.post<void>('/api/loans/applications/$applicationId/withdraw');

  /// Schritt 3: Vertrag abschließen (Simulation) – noch keine Auszahlung.
  Future<LoanDetail> accept(int applicationId) async {
    final res = await _api.dio
        .post<Map<String, dynamic>>('/api/loans/applications/$applicationId/accept', data: {'confirm': true});
    return LoanDetail.fromJson(res.data!);
  }

  /// Schritt 4: Auszahlung aufs Girokonto.
  Future<LoanDetail> payout(int loanId) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/loans/$loanId/payout');
    return LoanDetail.fromJson(res.data!);
  }

  /// Vertrag vor der Auszahlung widerrufen.
  Future<LoanDetail> cancel(int loanId) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/loans/$loanId/cancel');
    return LoanDetail.fromJson(res.data!);
  }

  Future<List<ScheduleRow>> schedule(int principalCents, double interestRate, int termMonths) async {
    final res = await _api.dio.post<List<dynamic>>('/api/loans/schedule', data: {
      'principalCents': principalCents,
      'interestRate': interestRate,
      'termMonths': termMonths,
    });
    return res.data!.map((e) => ScheduleRow.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<LoanDetail> create(String bank, String purpose, int principalCents, int equityCents, int termMonths) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/loans', data: {
      'bank': bank,
      'purpose': purpose,
      'principalCents': principalCents,
      'equityCents': equityCents,
      'termMonths': termMonths,
    });
    return LoanDetail.fromJson(res.data!);
  }

  Future<List<Loan>> list() async {
    final res = await _api.dio.get<List<dynamic>>('/api/loans');
    return res.data!.map((e) => Loan.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<LoanDetail> detail(int id) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/loans/$id');
    return LoanDetail.fromJson(res.data!);
  }

  /// /K020/: Vorschau einer Sondertilgung
  Future<ExtraPreview> previewExtra(int id, int amountCents) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/loans/$id/extra-payment/preview',
        data: {'amountCents': amountCents});
    return ExtraPreview.fromJson(res.data!);
  }

  /// /K020/: Sondertilgung buchen
  Future<LoanDetail> extraPayment(int id, int amountCents) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/loans/$id/extra-payment',
        data: {'amountCents': amountCents});
    return LoanDetail.fromJson(res.data!);
  }
}

final loansRepositoryProvider = Provider<LoansRepository>((ref) => LoansRepository(ref.watch(apiClientProvider)));

final loansProvider = FutureProvider.autoDispose<List<Loan>>((ref) => ref.watch(loansRepositoryProvider).list());

final loanApplicationsProvider = FutureProvider.autoDispose<List<LoanApplication>>(
    (ref) => ref.watch(loansRepositoryProvider).applications());

final loanApplicationProvider = FutureProvider.autoDispose.family<LoanApplication, int>(
    (ref, id) => ref.watch(loansRepositoryProvider).application(id));

final loanDetailProvider =
    FutureProvider.autoDispose.family<LoanDetail, int>((ref, id) => ref.watch(loansRepositoryProvider).detail(id));
