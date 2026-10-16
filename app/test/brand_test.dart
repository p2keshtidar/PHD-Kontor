import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/brand.dart';

import '../tool/setup_platforms.dart' as setup;

/// App-Name (BR01–BR02): Startbildschirm, Browser-Tab und Web-Manifest zeigen „PHD Kontor“,
/// nicht den technischen Namen „girokonto_app“ aus `flutter create`.
void main() {
  test('BR01 setup_platforms trägt den App-Namen in Android, iOS und Web ein', () {
    final tmp = Directory.systemTemp.createTempSync('brand_');
    void write(String path, String text) => (File('${tmp.path}/$path')..createSync(recursive: true)).writeAsStringSync(text);
    // so legt "flutter create --project-name girokonto_app" die Dateien an (gekürzt)
    write('android/app/src/main/AndroidManifest.xml',
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <application android:label="girokonto_app" android:name="\${applicationName}">\n'
        '    </application>\n</manifest>\n');
    write('ios/Runner/Info.plist',
        '<plist version="1.0">\n<dict>\n\t<key>CFBundleDisplayName</key>\n\t<string>Girokonto App</string>\n</dict>\n</plist>\n');
    write('web/index.html',
        '<head>\n  <meta name="apple-mobile-web-app-title" content="girokonto_app">\n  <title>girokonto_app</title>\n</head>\n');
    write('web/manifest.json',
        '{\n    "name": "girokonto_app",\n    "short_name": "girokonto_app",\n    "description": "A new Flutter project."\n}\n');

    final before = Directory.current;
    try {
      Directory.current = tmp;
      setup.main();
      setup.main(); // idempotent: zweiter Lauf ändert nichts mehr
    } finally {
      Directory.current = before;
    }
    String read(String path) => File('${tmp.path}/$path').readAsStringSync();
    expect(read('android/app/src/main/AndroidManifest.xml'), contains('android:label="$appName"'));
    expect(read('ios/Runner/Info.plist'), contains('<string>$appName</string>'));
    expect(read('web/index.html'), allOf(contains('<title>$appName</title>'), contains('content="$appName"')));
    final manifest = read('web/manifest.json');
    expect(manifest, allOf(contains('"name": "$appName"'), contains('"short_name": "$appName"')));
    for (final f in ['android/app/src/main/AndroidManifest.xml', 'ios/Runner/Info.plist', 'web/index.html', 'web/manifest.json']) {
      expect(read(f), isNot(contains('girokonto_app')), reason: f);
    }
    tmp.deleteSync(recursive: true);
  });

  test('BR02 App-Name ist „PHD Kontor“ und enthält nicht das Wort „Bank“ (§ 39 KWG)', () {
    expect(appName, 'PHD Kontor');
    expect(appName.toLowerCase(), isNot(contains('bank')));
  });
}
