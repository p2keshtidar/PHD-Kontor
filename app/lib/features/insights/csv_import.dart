import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';

/// /K040/ Ergebnis eines CSV-Imports
class ImportResult {
  const ImportResult({
    required this.format,
    required this.imported,
    required this.skipped,
    required this.errors,
    required this.incomeCents,
    required this.expenseCents,
    this.firstDate,
    this.lastDate,
  });

  final String format;
  final int imported;
  final int skipped;
  final List<String> errors;
  final int incomeCents;
  final int expenseCents;
  final DateTime? firstDate;
  final DateTime? lastDate;

  factory ImportResult.fromJson(Map<String, dynamic> j) => ImportResult(
        format: j['format'] as String,
        imported: (j['imported'] as num).toInt(),
        skipped: (j['skipped'] as num).toInt(),
        errors: ((j['errors'] as List<dynamic>?) ?? const []).cast<String>(),
        incomeCents: (j['incomeCents'] as num).toInt(),
        expenseCents: (j['expenseCents'] as num).toInt(),
        firstDate: j['firstDate'] == null ? null : DateTime.parse(j['firstDate'] as String),
        lastDate: j['lastDate'] == null ? null : DateTime.parse(j['lastDate'] as String),
      );
}

/// Ein früherer Import
class CsvImportInfo {
  const CsvImportInfo(this.id, this.fileName, this.rowsImported, this.firstDate, this.lastDate);

  final int id;
  final String fileName;
  final int rowsImported;
  final DateTime? firstDate;
  final DateTime? lastDate;

  factory CsvImportInfo.fromJson(Map<String, dynamic> j) => CsvImportInfo(
        (j['id'] as num).toInt(),
        j['fileName'] as String,
        (j['rowsImported'] as num).toInt(),
        j['firstDate'] == null ? null : DateTime.parse(j['firstDate'] as String),
        j['lastDate'] == null ? null : DateTime.parse(j['lastDate'] as String),
      );
}

/// Dateiinhalt als Text: zuerst UTF-8, sonst ISO-8859-1 (z. B. Sparkasse, Volksbank).
String decodeCsvBytes(List<int> bytes) {
  try {
    return utf8.decode(bytes);
  } on FormatException {
    return latin1.decode(bytes);
  }
}

class CsvImportRepository {
  CsvImportRepository(this._api);

  final ApiClient _api;

  Future<ImportResult> upload(String fileName, String content) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/imports',
        data: {'fileName': fileName, 'content': content});
    return ImportResult.fromJson(res.data!);
  }

  Future<String> sample() async {
    final res = await _api.dio.get<String>('/api/imports/sample');
    return res.data!;
  }

  Future<List<CsvImportInfo>> list() async {
    final res = await _api.dio.get<List<dynamic>>('/api/imports');
    return res.data!.map((e) => CsvImportInfo.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> delete(int id) => _api.dio.delete<void>('/api/imports/$id');
}

final csvImportRepositoryProvider =
    Provider<CsvImportRepository>((ref) => CsvImportRepository(ref.watch(apiClientProvider)));

final csvImportsProvider =
    FutureProvider.autoDispose<List<CsvImportInfo>>((ref) => ref.watch(csvImportRepositoryProvider).list());
