/// Angemeldeter Nutzer, wie ihn das Backend unter /api/me liefert.
class AppUser {
  const AppUser({
    required this.id,
    required this.email,
    required this.firstName,
    required this.lastName,
    required this.phone,
    required this.birthDate,
    required this.role,
    this.twoFactorEnabled = false,
    this.language,
  });

  final int id;
  final String email;
  final String firstName;
  final String lastName;
  final String phone;
  final DateTime birthDate;
  final String role;

  /// /K050/
  final bool twoFactorEnabled;

  /// Gespeicherte Sprache des Kontos ('de'/'en') oder null
  final String? language;

  String get fullName => '$firstName $lastName';
  bool get isAdmin => role == 'ADMIN';

  factory AppUser.fromJson(Map<String, dynamic> json) => AppUser(
        id: (json['id'] as num).toInt(),
        email: json['email'] as String,
        firstName: json['firstName'] as String,
        lastName: json['lastName'] as String,
        phone: json['phone'] as String,
        birthDate: DateTime.parse(json['birthDate'] as String),
        role: json['role'] as String,
        twoFactorEnabled: json['twoFactorEnabled'] as bool? ?? false,
        language: json['language'] as String?,
      );
}
