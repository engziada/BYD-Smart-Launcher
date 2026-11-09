import 'package:byd_smart_launcher/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.ziada.byd_smart_launcher/vehicle');
  const events =
      MethodChannel('com.ziada.byd_smart_launcher/native_candidate_stream');
  const oldStartButton = 'Connect & start live readings';

  void setupSize(WidgetTester tester) {
    tester.view.physicalSize = const Size(1600, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
  }

  tearDown(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(channel, null);
    messenger.setMockMethodCallHandler(events, null);
  });

  testWidgets('ADB and the native stream start automatically and render live speed',
      (tester) async {
    setupSize(tester);
    final calls = <String>[];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(events, (call) async {
      calls.add('event:${call.method}');
      return null;
    });
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      switch (call.method) {
        case 'adbConnect':
          return <String, Object?>{'adb': 'connected'};
        case 'nativeCandidateStreamStart':
          return <String, Object?>{'ok': true, 'streamState': 'initializing'};
        case 'nativeCandidateStreamStop':
          return <String, Object?>{'ok': true, 'streamState': 'stopped'};
        case 'getSnapshot':
          return <String, Object?>{
            'connected': false, 'source': 'unavailable',
            'helperState': 'not_installed', 'capturedAtEpochMs': 0,
          };
        default:
          return <String, Object?>{};
      }
    });

    await tester.pumpWidget(const LauncherApp());
    await tester.pump();
    expect(find.text('Vehicle overview'), findsOneWidget);
    await tester.pumpAndSettle();
    expect(find.text(oldStartButton), findsNothing);
    expect(calls.where((c) => c == 'adbConnect').length, 1);
    expect(calls, contains('event:listen'));
    expect(calls, contains('nativeCandidateStreamStart'));
    expect(calls.where((c) => c == 'comfortMappings').length, 1);
    expect(calls, isNot(contains('dashboardSample')));

    await messenger.handlePlatformMessage(
      events.name,
      const StandardMethodCodec().encodeSuccessEnvelope({
        'streamState': 'candidate',
        'socCandidatePercent': 68.0,
        'odometerCandidateKm': 12345.6,
        'speedKmh': 37.0,
        'capturedAtEpochMs': DateTime.now().millisecondsSinceEpoch,
        'firmwareVerified': false,
        'vehicleWriteAuthorized': false,
      }),
      (_) {},
    );
    await tester.pump();
    expect(find.text('37 km/h'), findsOneWidget);
    expect(find.text('12345.6 km'), findsOneWidget);
    expect(find.text('Vehicle online'), findsOneWidget);

    await tester.pump(const Duration(seconds: 4));
    expect(find.text('37 km/h'), findsNothing);
    expect(find.text('Waiting for fresh readings…'), findsOneWidget);
    expect(calls.where((c) => c == 'nativeCandidateStreamStart').length, 1);
    expect(calls, isNot(contains('dashboardSample')));
  });

  testWidgets('dashboard pauses in background and reconnects on resume',
      (tester) async {
    setupSize(tester);
    final calls = <String>[];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(events, (call) async => null);
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      if (call.method == 'adbConnect') return {'adb': 'connected'};
      if (call.method == 'nativeCandidateStreamStart') {
        return {'ok': true, 'streamState': 'initializing'};
      }
      return <String, Object?>{};
    });

    await tester.pumpWidget(const LauncherApp());
    await tester.pump();
    await tester.pumpAndSettle();
    expect(calls.where((m) => m == 'nativeCandidateStreamStart').length, 1);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    await tester.pump(const Duration(seconds: 20));
    expect(calls, contains('nativeCandidateStreamStop'));
    expect(calls.where((m) => m == 'nativeCandidateStreamStart').length, 1);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    expect(find.text(oldStartButton), findsNothing);
    expect(calls.where((m) => m == 'nativeCandidateStreamStart').length, 2);
  });

  testWidgets('unavailable native stream never displays guessed telemetry',
      (tester) async {
    setupSize(tester);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(events, (call) async => null);
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'adbConnect') return {'adb': 'connected'};
      if (call.method == 'nativeCandidateStreamStart') {
        return {'ok': false, 'reason': 'firmware_fids_or_scale_unvalidated'};
      }
      return <String, Object?>{};
    });

    await tester.pumpWidget(const LauncherApp());
    await tester.pump();
    await tester.pumpAndSettle();
    expect(find.text(oldStartButton), findsNothing);
    expect(find.textContaining('Vehicle readings unavailable'), findsOneWidget);
    // With the visual hero card, the metric tiles can be below the fold.
    // Assert the visible scene reports missing telemetry, not a guessed SOC.
    expect(find.text('SOC unavailable'), findsOneWidget);
    expect(find.text('CHARGING DATA UNAVAILABLE'), findsOneWidget);
  });
}
