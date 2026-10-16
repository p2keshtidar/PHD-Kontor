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
//   Alle:    App-Name „PHD Kontor“ (lib/core/brand.dart) auf dem Startbildschirm und im Browser-Tab
//   Beide:   niedrigste Systemversion, die Flutter unterstützt (siehe docs/technik/versionen.md)

import 'dart:io';

import 'package:girokonto_app/core/brand.dart';

/// Android 7.0 (API 24) und iOS 15: die ältesten Versionen, die Flutter 3.47 unterstützt.
/// Alle verwendeten Plugins kommen mit diesen Versionen aus.
const androidMinSdk = 24;
const iosMinVersion = '15.0';

void main() {
  var changed = 0;
  changed += _androidManifest();
  changed += _androidGradle();
  changed += _iosInfoPlist();
  changed += _iosClassicLifecycle();
  changed += _iosDeploymentTarget();
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
  // Seiten der Bank (Open Banking) im Browser öffnen: ab Android 11 muss die App das ankündigen
  const browserIntent = '<intent>\n            <action android:name="android.intent.action.VIEW"/>\n'
      '            <data android:scheme="https"/>\n        </intent>';
  if (!s.contains('android:scheme="https"')) {
    s = s.contains('<queries>')
        ? s.replaceFirst('<queries>', '<queries>\n        $browserIntent')
        : s.replaceFirst('</manifest>', '    <queries>\n        $browserIntent\n    </queries>\n</manifest>');
  }
  s = s.replaceFirst(RegExp(r'android:label="[^"]*"'), 'android:label="$appName"');
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

  // Mindestversion fest eintragen (statt "flutter.minSdkVersion")
  s = s.replaceAll(RegExp(r'minSdk\s*=\s*flutter\.minSdkVersion'), 'minSdk = $androidMinSdk');
  s = s.replaceAll(RegExp(r'minSdkVersion\s+flutter\.minSdkVersion'), 'minSdkVersion $androidMinSdk');

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
      (m) => '${m[1]}$appName${m[2]}');
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
        .replaceFirst(RegExp(r'"name":\s*"[^"]*"'), '"name": "$appName"')
        .replaceFirst(RegExp(r'"short_name":\s*"[^"]*"'), '"short_name": "$appName"')
        .replaceFirst(RegExp(r'"description":\s*"[^"]*"'),
            '"description": "$appName – Smartphone-Konto mit Finanzassistent (Projekt Informatik, FRA-UAS)"');
    if (n != s) {
      manifest.writeAsStringSync(n);
      changed++;
    }
  }
  final index = File('web/index.html');
  if (index.existsSync()) {
    final s = index.readAsStringSync();
    final n = s
        .replaceFirst(RegExp(r'<title>[^<]*</title>'), '<title>$appName</title>')
        .replaceFirst(RegExp(r'apple-mobile-web-app-title" content="[^"]*"'),
            'apple-mobile-web-app-title" content="$appName"');
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

/// iOS-Mindestversion in Podfile, Xcode-Projekt und Flutter-Framework gleich setzen.
int _iosDeploymentTarget() {
  var changed = 0;
  void patch(String path, String Function(String) f) {
    final file = File(path);
    if (!file.existsSync()) return;
    final s = file.readAsStringSync();
    final n = f(s);
    if (n != s) {
      file.writeAsStringSync(n);
      stdout.writeln('Angepasst: $path (iOS $iosMinVersion)');
      changed++;
    }
  }

  patch('ios/Podfile', (s) => s.replaceFirst(RegExp(r"^#?\s*platform :ios, '[0-9.]+'", multiLine: true),
      "platform :ios, '$iosMinVersion'"));
  patch('ios/Runner.xcodeproj/project.pbxproj',
      (s) => s.replaceAll(RegExp(r'IPHONEOS_DEPLOYMENT_TARGET = [0-9.]+;'), 'IPHONEOS_DEPLOYMENT_TARGET = $iosMinVersion;'));
  patch('ios/Flutter/AppFrameworkInfo.plist', (s) => s.replaceFirstMapped(
      RegExp(r'(<key>MinimumOSVersion</key>\s*<string>)[0-9.]+(</string>)'), (m) => '${m[1]}$iosMinVersion${m[2]}'));
  return changed;
}
