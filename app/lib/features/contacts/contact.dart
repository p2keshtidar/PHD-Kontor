/// Gespeicherter Empfänger im Adressbuch (/F220/).
class Contact {
  const Contact({
    required this.id,
    required this.userId,
    required this.displayName,
    required this.fullName,
    required this.iban,
  });

  final int id;
  final int userId;
  final String displayName;
  final String fullName;
  final String iban;

  factory Contact.fromJson(Map<String, dynamic> json) => Contact(
        id: (json['id'] as num).toInt(),
        userId: (json['userId'] as num).toInt(),
        displayName: json['displayName'] as String,
        fullName: json['fullName'] as String,
        iban: json['iban'] as String,
      );
}

/// Ein App-Nutzer, der in den Gerätekontakten gefunden wurde.
class ContactMatch {
  const ContactMatch({
    required this.userId,
    required this.fullName,
    required this.iban,
    required this.phone,
    required this.alreadyContact,
  });

  final int userId;
  final String fullName;
  final String iban;
  final String phone;
  final bool alreadyContact;

  factory ContactMatch.fromJson(Map<String, dynamic> json) => ContactMatch(
        userId: (json['userId'] as num).toInt(),
        fullName: json['fullName'] as String,
        iban: json['iban'] as String,
        phone: json['phone'] as String,
        alreadyContact: json['alreadyContact'] as bool,
      );
}
