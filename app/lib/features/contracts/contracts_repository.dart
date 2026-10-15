import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';

int _i(Object? v) => (v as num).toInt();

/// Vertragsarten wie im Backend (Schlüssel → deutscher Text, übersetzt über tr()).
const contractKinds = <String, String>{
  'HANDY_INTERNET': 'Handy & Internet',
  'STROM_GAS': 'Strom & Gas',
  'STREAMING': 'Streaming & Abos',
  'VERSICHERUNG': 'Versicherung',
  'MIETE': 'Miete',
  'FITNESS': 'Fitness & Verein',
  'MOBILITAET': 'Mobilität',
  'SONSTIGES': 'Sonstiges',
};

class Contract {
  const Contract(this.json);

  final Map<String, dynamic> json;

  int get id => _i(json['id']);
  String get name => json['name'] as String;
  String? get provider => json['provider'] as String?;
  String get kind => json['kind'] as String;
  String get kindLabel => json['kindLabel'] as String;
  int get costCents => _i(json['costCents']);
  String get billing => json['billing'] as String;
  String get billingLabel => json['billingLabel'] as String;
  int get monthlyCents => _i(json['monthlyCents']);
  int get yearlyCents => _i(json['yearlyCents']);
  DateTime get startDate => DateTime.parse(json['startDate'] as String);
  int get minTermMonths => _i(json['minTermMonths']);
  int get renewalMonths => _i(json['renewalMonths']);
  int get noticeValue => _i(json['noticeValue']);
  String get noticeUnit => json['noticeUnit'] as String;
  String get noticeLabel => json['noticeLabel'] as String;
  int get reminderDays => _i(json['reminderDays']);
  String get status => json['status'] as String;
  String get statusLabel => json['statusLabel'] as String;
  DateTime? get nextEndDate => json['nextEndDate'] == null ? null : DateTime.parse(json['nextEndDate'] as String);
  DateTime? get cancelByDate => json['cancelByDate'] == null ? null : DateTime.parse(json['cancelByDate'] as String);
  int? get daysUntilCancelBy => (json['daysUntilCancelBy'] as num?)?.toInt();
  String? get note => json['note'] as String?;
  int get documentCount => _i(json['documentCount']);
  String get hintText => json['hintText'] as String? ?? '';

  bool get active => status == 'ACTIVE';
  bool get dueSoon => active && (daysUntilCancelBy ?? 999) <= 30;
}

class ContractsOverview {
  const ContractsOverview(this.contracts, this.monthlyTotalCents, this.yearlyTotalCents, this.dueSoon, this.note);

  final List<Contract> contracts;
  final int monthlyTotalCents;
  final int yearlyTotalCents;
  final int dueSoon;
  final String note;

  factory ContractsOverview.fromJson(Map<String, dynamic> j) => ContractsOverview(
        [for (final c in j['contracts'] as List<dynamic>) Contract(c as Map<String, dynamic>)],
        _i(j['monthlyTotalCents']),
        _i(j['yearlyTotalCents']),
        _i(j['dueSoon']),
        j['note'] as String? ?? '',
      );
}

class ContractDocument {
  const ContractDocument(this.id, this.title, this.fileName, this.contentType, this.sizeBytes, this.createdAt);

  final int id;
  final String title;
  final String fileName;
  final String contentType;
  final int sizeBytes;
  final DateTime createdAt;

  bool get isImage => contentType.startsWith('image/');

  factory ContractDocument.fromJson(Map<String, dynamic> j) => ContractDocument(_i(j['id']), j['title'] as String,
      j['fileName'] as String, j['contentType'] as String, _i(j['sizeBytes']), DateTime.parse(j['createdAt'] as String));
}

class ContractsRepository {
  ContractsRepository(this._api);

  final ApiClient _api;

  Future<ContractsOverview> overview() async =>
      ContractsOverview.fromJson((await _api.dio.get<Map<String, dynamic>>('/api/contracts')).data!);

  Future<Contract> get(int id) async => Contract((await _api.dio.get<Map<String, dynamic>>('/api/contracts/$id')).data!);

  Future<Contract> save(int? id, Map<String, Object?> body) async {
    final res = id == null
        ? await _api.dio.post<Map<String, dynamic>>('/api/contracts', data: body)
        : await _api.dio.put<Map<String, dynamic>>('/api/contracts/$id', data: body);
    return Contract(res.data!);
  }

  Future<void> cancel(int id) => _api.dio.post<void>('/api/contracts/$id/cancel', data: <String, Object?>{});

  Future<void> reactivate(int id) => _api.dio.post<void>('/api/contracts/$id/reactivate');

  Future<void> delete(int id) => _api.dio.delete<void>('/api/contracts/$id');

  Future<List<ContractDocument>> documents(int contractId) async {
    final res = await _api.dio.get<List<dynamic>>('/api/documents', queryParameters: {'contractId': contractId});
    return [for (final d in res.data!) ContractDocument.fromJson(d as Map<String, dynamic>)];
  }

  Future<void> upload(int contractId, String fileName, Uint8List bytes, String title) => _api.dio.post<void>(
        '/api/documents',
        data: FormData.fromMap({
          'file': MultipartFile.fromBytes(bytes, filename: fileName),
          'title': title,
          'contractId': contractId,
        }),
      );

  Future<Uint8List> content(int id) async {
    final res = await _api.dio
        .get<List<int>>('/api/documents/$id/content', options: Options(responseType: ResponseType.bytes));
    return Uint8List.fromList(res.data!);
  }

  Future<void> deleteDocument(int id) => _api.dio.delete<void>('/api/documents/$id');
}

final contractsRepositoryProvider =
    Provider<ContractsRepository>((ref) => ContractsRepository(ref.watch(apiClientProvider)));

final contractsProvider =
    FutureProvider.autoDispose<ContractsOverview>((ref) => ref.watch(contractsRepositoryProvider).overview());

final contractProvider =
    FutureProvider.autoDispose.family<Contract, int>((ref, id) => ref.watch(contractsRepositoryProvider).get(id));

final contractDocumentsProvider = FutureProvider.autoDispose
    .family<List<ContractDocument>, int>((ref, id) => ref.watch(contractsRepositoryProvider).documents(id));
