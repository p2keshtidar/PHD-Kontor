import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'loan_models.dart';

class LoansRepository {
  LoansRepository(this._api);

  final ApiClient _api;

  Future<OfferResult> offers(String purpose, int principalCents, int equityCents, int termMonths) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/loans/offers', data: {
      'purpose': purpose,
      'principalCents': principalCents,
      'equityCents': equityCents,
      'termMonths': termMonths,
    });
    return OfferResult.fromJson(res.data!);
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
}

final loansRepositoryProvider = Provider<LoansRepository>((ref) => LoansRepository(ref.watch(apiClientProvider)));

final loansProvider = FutureProvider.autoDispose<List<Loan>>((ref) => ref.watch(loansRepositoryProvider).list());

final loanDetailProvider =
    FutureProvider.autoDispose.family<LoanDetail, int>((ref, id) => ref.watch(loansRepositoryProvider).detail(id));
