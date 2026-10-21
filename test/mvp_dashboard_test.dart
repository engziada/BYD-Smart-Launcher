import 'package:byd_smart_launcher/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.ziada.byd_smart_launcher/vehicle');
  const events =
      MethodChannel('com.ziada.byd_smart_launcher/native_candidate_stream');

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

  testWidgets('native stream starts explicitly and stops; no Flutter poll timer',
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
    expect(find.text('Native telemetry — P2 technical preview'), findsOneWidget);
    expect(find.text('Start native read-only stream'), findsOneWidget);
    expect(calls, isNot(contains('nativeCandidateStreamStart')));
    expect(calls, isNot(contains('dashboardSample')));

    await tester.tap(find.text('Start native read-only stream'));
    await tester.pumpAndSettle();
    expect(calls, contains('adbConnect'));
    expect(calls, contains('event:listen'));
    expect(calls, contains('nativeCandidateStreamStart'));
    expect(find.text('Stop updates'), findsOneWidget);

    await tester.pump(const Duration(seconds: 20));
    expect(calls.where((c) => c == 'nativeCandidateStreamStart').length, 1);
    expect(calls, isNot(contains('dashboardSample')));

    await tester.tap(find.text('Stop updates'));
    await tester.pumpAndSettle();
    expect(calls, contains('nativeCandidateStreamStop'));
    expect(find.text('Start native read-only stream'), findsOneWidget);
    expect(find.textContaining('Native stream stopped'), findsOneWidget);
  });

  testWidgets('background stops stream and resume never restarts it',
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
    await tester.tap(find.text('Start native read-only stream'));
    await tester.pumpAndSettle();
    expect(calls.where((m) => m == 'nativeCandidateStreamStart').length, 1);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    await tester.pump(const Duration(seconds: 20));
    expect(calls, contains('nativeCandidateStreamStop'));
    expect(calls.where((m) => m == 'nativeCandidateStreamStart').length, 1);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(find.text('Start native read-only stream'), findsOneWidget);
    expect(find.textContaining('background'), findsOneWidget);
    expect(calls.where((m) => m == 'nativeCandidateStreamStart').length, 1);
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
    await tester.tap(find.text('Start native read-only stream'));
    await tester.pumpAndSettle();
    expect(find.text('Start native read-only stream'), findsOneWidget);
    expect(find.textContaining('firmware_fids_or_scale_unvalidated'), findsOneWidget);
    expect(find.text('— %'), findsOneWidget);
  });
}
