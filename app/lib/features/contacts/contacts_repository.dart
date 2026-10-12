import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'contact.dart';

class ContactsRepository {
  ContactsRepository(this._api);

  final ApiClient _api;

  Future<List<Contact>> fetchAll() async {
    final res = await _api.dio.get<List<dynamic>>('/api/contacts');
    return res.data!.map((e) => Contact.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// [recipient]: IBAN, E-Mail oder Telefonnummer
  Future<Contact> add(String recipient, {String? nickname}) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/contacts', data: {
      'recipient': recipient.trim(),
      if (nickname != null && nickname.trim().isNotEmpty) 'nickname': nickname.trim(),
    });
    return Contact.fromJson(res.data!);
  }

  Future<void> delete(int id) async {
    await _api.dio.delete<void>('/api/contacts/$id');
  }

  Future<List<ContactMatch>> match(List<String> phones) async {
    final res = await _api.dio.post<List<dynamic>>('/api/contacts/match', data: {'phones': phones});
    return res.data!.map((e) => ContactMatch.fromJson(e as Map<String, dynamic>)).toList();
  }
}

final contactsRepositoryProvider =
    Provider<ContactsRepository>((ref) => ContactsRepository(ref.watch(apiClientProvider)));

final contactsProvider = FutureProvider.autoDispose<List<Contact>>(
  (ref) => ref.watch(contactsRepositoryProvider).fetchAll(),
);
