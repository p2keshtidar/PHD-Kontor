/// Eine Benachrichtigung (/F510/, /F520/).
class AppNotification {
  const AppNotification({
    required this.id,
    required this.type,
    required this.title,
    required this.body,
    required this.read,
    required this.createdAt,
    this.bookingId,
  });

  final int id;
  final String type;
  final String title;
  final String body;
  final bool read;
  final DateTime createdAt;
  final int? bookingId;

  factory AppNotification.fromJson(Map<String, dynamic> j) => AppNotification(
        id: (j['id'] as num).toInt(),
        type: j['type'] as String,
        title: j['title'] as String,
        body: j['body'] as String,
        read: j['read'] as bool,
        createdAt: DateTime.parse(j['createdAt'] as String),
        bookingId: (j['bookingId'] as num?)?.toInt(),
      );
}
