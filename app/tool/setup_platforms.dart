// Richtet die Plattform-Ordner (android/, ios/, web/) für dieses Projekt ein.
//
// Ausführen im Ordner app/, NACH "flutter create" und "flutter pub get":
//
//   flutter create . --platforms=android,ios,web --org de.frauas --project-name girokonto_app
//   flutter pub get
//   dart run tool/setup_platforms.dart
//
// Das Skript ist idempotent (mehrfach ausführbar) und ergänzt:
//   Android: Internet-, Kontakte- und Benachrichtigungs-Berechtigung, http zum Backend (Entwicklung),
//            Core Library Desugaring für flutter_local_notifications
//   iOS:     Beschreibung für den Kontakte-Zugriff, lokale http-Verbindungen,
//            klassischer App-Start ohne UIScene (sonst stürzt flutter_contacts 1.x beim Start ab)

import 'dart:io';

void main() {
  var changed = 0;
  changed += _androidManifest();
  changed += _androidGradle();
  changed += _iosInfoPlist();
  changed += _iosClassicLifecycle();
  changed += _webNames();
  stdout.writeln(changed == 0 ? 'Alles bereits eingerichtet.' : '$changed Datei(en) angepasst.');
}

int _androidManifest() {
  final file = File('android/app/src/main/AndroidManifest.xml');
  if (!file.existsSync()) {
    stdout.writeln('Übersprungen: ${file.path} fehlt (erst "flutter create" ausführen).');
    return 0;
  }
  var s = file.readAsStringSync();
  final original = s;

  const permissions = [
    'android.permission.INTERNET',
    'android.permission.READ_CONTACTS',
    'android.permission.POST_NOTIFICATIONS',
  ];
  for (final p in permissions) {
    if (!s.contains('"$p"')) {
      s = s.replaceFirst('<application', '<uses-permission android:name="$p"/>\n    <application');
    }
  }
  s = s.replaceFirst(RegExp(r'android:label="[^"]*"'), 'android:label="Girokonto"');
  if (!s.contains('usesCleartextTraffic')) {
    s = s.replaceFirst('<application', '<application\n        android:usesCleartextTraffic="true"');
  }
  if (s != original) {
    file.writeAsStringSync(s);
    stdout.writeln('Angepasst: ${file.path}');
    return 1;
  }
  return 0;
}

int _androidGradle() {
  final kts = File('android/app/build.gradle.kts');
  final groovy = File('android/app/build.gradle');
  final file = kts.existsSync() ? kts : groovy;
  if (!file.existsSync()) {
    stdout.writeln('Übersprungen: android/app/build.gradle(.kts) fehlt.');
    return 0;
  }
  final isKts = file.path.endsWith('.kts');
  var s = file.readAsStringSync();
  final original = s;

  final enableLine = isKts ? 'isCoreLibraryDesugaringEnabled = true' : 'coreLibraryDesugaringEnabled true';
  if (!s.contains('oreLibraryDesugaringEnabled')) {
    if (s.contains('compileOptions {')) {
      s = s.replaceFirst('compileOptions {', 'compileOptions {\n        $enableLine');
    } else {
      s = s.replaceFirst(RegExp(r'android\s*\{'), 'android {\n    compileOptions {\n        $enableLine\n    }');
    }
  }

  final depLine = isKts
      ? 'coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")'
      : "coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.1.4'";
  if (!s.contains('desugar_jdk_libs')) {
    // eigener dependencies-Block am Ende der Datei (Gradle erlaubt mehrere)
    s = '${s.trimRight()}\n\ndependencies {\n    $depLine\n}\n';
  }

  if (s != original) {
    file.writeAsStringSync(s);
    stdout.writeln('Angepasst: ${file.path}');
    return 1;
  }
  return 0;
}

int _iosInfoPlist() {
  final file = File('ios/Runner/Info.plist');
  if (!file.existsSync()) {
    stdout.writeln('Übersprungen: ${file.path} fehlt.');
    return 0;
  }
  var s = file.readAsStringSync();
  final original = s;
  final additions = StringBuffer();
  if (!s.contains('NSContactsUsageDescription')) {
    additions.writeln('\t<key>NSContactsUsageDescription</key>');
    additions.writeln('\t<string>Damit du Freunde findest, die diese App nutzen, und ihnen Geld senden kannst.</string>');
  }
  if (!s.contains('NSAppTransportSecurity')) {
    additions.writeln('\t<key>NSAppTransportSecurity</key>');
    additions.writeln('\t<dict>');
    additions.writeln('\t\t<key>NSAllowsLocalNetworking</key>');
    additions.writeln('\t\t<true/>');
    additions.writeln('\t</dict>');
  }
  s = s.replaceFirstMapped(
      RegExp(r'(<key>CFBundleDisplayName</key>\s*<string>)[^<]*(</string>)'),
      (m) => '${m[1]}Girokonto${m[2]}');
  if (additions.isNotEmpty) {
    final idx = s.lastIndexOf('</dict>');
    s = s.substring(0, idx) + additions.toString() + s.substring(idx);
  }
  if (s != original) {
    file.writeAsStringSync(s);
    stdout.writeln('Angepasst: ${file.path}');
    return 1;
  }
  return 0;
}

int _webNames() {
  var changed = 0;
  final manifest = File('web/manifest.json');
  if (manifest.existsSync()) {
    final s = manifest.readAsStringSync();
    final n = s
        .replaceFirst(RegExp(r'"name":\s*"[^"]*"'), '"name": "Girokonto"')
        .replaceFirst(RegExp(r'"short_name":\s*"[^"]*"'), '"short_name": "Girokonto"')
        .replaceFirst(RegExp(r'"description":\s*"[^"]*"'),
            '"description": "Smartphone-Girokonto mit Finanzassistent (Projekt Informatik, FRA-UAS)"');
    if (n != s) {
      manifest.writeAsStringSync(n);
      changed++;
    }
  }
  final index = File('web/index.html');
  if (index.existsSync()) {
    final s = index.readAsStringSync();
    final n = s
        .replaceFirst(RegExp(r'<title>[^<]*</title>'), '<title>Girokonto</title>')
        .replaceFirst(RegExp(r'apple-mobile-web-app-title" content="[^"]*"'),
            'apple-mobile-web-app-title" content="Girokonto"');
    if (n != s) {
      index.writeAsStringSync(n);
      changed++;
    }
  }
  return changed;
}

/// Neue Flutter-Versionen (ab 3.38) erzeugen iOS-Projekte mit UIScene. Dann gibt es beim
/// Registrieren der Plugins noch kein Fenster, und flutter_contacts 1.x stürzt ab
/// ("Unexpectedly found nil" in SwiftFlutterContactsPlugin.swift). Darum: klassischer Start.
int _iosClassicLifecycle() {
  var changed = 0;
  final delegate = File('ios/Runner/AppDelegate.swift');
  if (delegate.existsSync() &&
      !delegate.readAsStringSync().contains('Girokonto: klassischer App-Start')) {
    // Die Kommentarzeile ist wichtig: Eine AppDelegate, die exakt der alten Flutter-Vorlage
    // entspricht, stellt "flutter build" sonst automatisch wieder auf UIScene um.
    delegate.writeAsStringSync('''import Flutter
import UIKit

// Girokonto: klassischer App-Start ohne UIScene (siehe app/tool/setup_platforms.dart)

@main
@objc class AppDelegate: FlutterAppDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
''');
    stdout.writeln('Angepasst: ${delegate.path}');
    changed++;
  }
  final plist = File('ios/Runner/Info.plist');
  if (plist.existsSync()) {
    final s = plist.readAsStringSync();
    final key = s.indexOf('<key>UIApplicationSceneManifest</key>');
    if (key >= 0) {
      // zugehöriges <dict> ... </dict> inklusive verschachtelter dicts finden
      final start = s.indexOf('<dict>', key);
      var depth = 0;
      var end = -1;
      final tag = RegExp(r'<dict>|</dict>|<dict/>');
      for (final m in tag.allMatches(s, start)) {
        if (m[0] == '<dict>') depth++;
        if (m[0] == '</dict>') depth--;
        if (depth == 0) {
          end = m.end;
          break;
        }
      }
      if (end > 0) {
        plist.writeAsStringSync(s.substring(0, key) + s.substring(end).replaceFirst(RegExp(r'^\s*\n'), '\n'));
        stdout.writeln('Angepasst: ${plist.path} (UIScene entfernt)');
        changed++;
      }
    }
  }
  return changed;
}
