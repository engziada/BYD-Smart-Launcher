import 'package:byd_smart_launcher/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.ziada.byd_smart_launcher/vehicle');

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  testWidgets('MVP dashboard only polls after explicit start and stops on request',
      (tester) async {
    tester.view.physicalSize = const Size(1600, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      switch (call.method) {
        case 'adbConnect':
          return <String, Object?>{'adb': 'connected'};
        case 'getSnapshot':
          return <String, Object?>{
            'connected': false,
            'adbState': 'connected',
            'helperState': 'not_installed',
            'source': 'unavailable',
            'capturedAtEpochMs': 0,
          };
        case 'dashboardSample':
          return <String, Object?>{
            'ok': true,
            'socCandidatePercent': 73.0,
            'odometerCandidateKm': 42010.6,
            'firmwareVerified': false,
          };
        default:
          return <String, Object?>{};
      }
    });

    await tester.pumpWidget(const LauncherApp());
    await tester.pump();
    expect(find.text('Vehicle dashboard — MVP preview'), findsOneWidget);
    expect(find.text('Start read-only dashboard'), findsOneWidget);
    expect(calls.where((m) => m == 'dashboardSample'), isEmpty);

    await tester.ensureVisible(find.text('Start read-only dashboard'));
    await tester.tap(find.text('Start read-only dashboard'));
    await tester.pump();
    await tester.pump();
    await tester.pump();
    expect(calls.where((m) => m == 'adbConnect').length, 1);
    expect(calls.where((m) => m == 'dashboardSample').length, 1);
    expect(find.text('73 %'), findsOneWidget);
    expect(find.text('42010.6 km'), findsOneWidget);
    expect(find.text('Stop updates'), findsOneWidget);

    await tester.pump(const Duration(seconds: 16));
    await tester.pump();
    expect(calls.where((m) => m == 'dashboardSample').length, 2);

    await tester.tap(find.text('Stop updates'));
    await tester.pump();
    expect(find.text('Start read-only dashboard'), findsOneWidget);
    await tester.pump(const Duration(seconds: 20));
    expect(calls.where((m) => m == 'dashboardSample').length, 2);
    expect(find.textContaining('updates STOPPED'), findsOneWidget);
  });

  testWidgets('dashboard stops on app background transition', (tester) async {
    tester.view.physicalSize = const Size(1600, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    var sampleCount = 0;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'adbConnect') return {'adb': 'connected'};
      if (call.method == 'dashboardSample') {
        sampleCount++;
        return {
          'ok': true,
          'socCandidatePercent': 89.0,
          'odometerCandidateKm': 42010.6,
        };
      }
      return <String, Object?>{};
    });

    await tester.pumpWidget(const LauncherApp());
    await tester.pump();
    await tester.tap(find.text('Start read-only dashboard'));
    await tester.pump();
    await tester.pump();
    await tester.pump();
    expect(sampleCount, 1);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    await tester.pump(const Duration(seconds: 20));
    expect(sampleCount, 1);
    expect(find.text('Start read-only dashboard'), findsOneWidget);
    expect(find.textContaining('left the foreground'), findsOneWidget);
  });
}
