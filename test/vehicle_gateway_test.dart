import 'package:byd_smart_launcher/core/vehicle_gateway.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.ziada.byd_smart_launcher/vehicle');

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('user-invoked ADB control is explicitly routed to native methods', () async {
    final called = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      called.add(call.method);
      return <String, Object?>{'adb': 'not_connected'};
    });
    const gateway = PlatformVehicleGateway();
    expect((await gateway.connectAdb())['adb'], 'not_connected');
    expect((await gateway.probeAdb())['adb'], 'not_connected');
    expect((await gateway.probeFirmware())['adb'], 'not_connected');
    expect((await gateway.probeSoc())['adb'], 'not_connected');
    expect((await gateway.disconnectAdb())['adb'], 'not_connected');
    expect(called, ['adbConnect', 'adbProbe', 'adbProbeFirmware', 'adbProbeSoc', 'adbDisconnect']);
  });

  test('operator-triggered FID discovery routes only to the read-only native method', () async {
    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      return <String, Object?>{
        'ok': true, 'status': 'catalog_candidate',
        'firmwareVerified': false, 'vehicleWriteAuthorized': false,
      };
    });
    const gateway = PlatformVehicleGateway();
    final discovered = await gateway.probeFidCatalog();
    expect(discovered['ok'], true);
    expect(discovered['firmwareVerified'], false);
    expect(discovered['vehicleWriteAuthorized'], false);
    expect(calls, ['adbProbeFids']);
    const demo = DemoVehicleGateway();
    expect((await demo.probeFidCatalog())['error'], 'demo_only');
    expect(calls, ['adbProbeFids']);
  });

  test('charging diagnostic is an explicit read-only call; demo never probes car', () async {
    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      return <String, Object?>{
        'ok': true,
        'gunKindCandidate': 'dc_candidate',
        'chargeStateCandidate': 'charging_candidate',
        'automaticEventEligible': false,
        'completionConfirmed': false,
        'vehicleWriteAuthorized': false,
      };
    });
    const gateway = PlatformVehicleGateway();
    final snapshot = await gateway.sampleChargingCandidates();
    expect(snapshot['gunKindCandidate'], 'dc_candidate');
    expect(snapshot['automaticEventEligible'], false);
    expect(snapshot['completionConfirmed'], false);
    expect(calls, ['chargingSample']);
    const demo = DemoVehicleGateway();
    final refused = await demo.sampleChargingCandidates();
    expect(refused['status'], 'demo_only');
    expect(refused['automaticEventEligible'], false);
    expect(calls, ['chargingSample']);
  });

  test('explicit Telegram save sends token only through native channel', () async {
    String? called;
    Map<Object?, Object?>? params;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      called = call.method;
      params = (call.arguments as Map).cast<Object?, Object?>();
      return <String, Object?>{'ok': true, 'configured': true};
    });
    const gateway = PlatformVehicleGateway();
    final reply = await gateway.telegramSave('123456:ABCDEFGHIJKLMNOPQRSTUVWXYZ', '123');
    expect(reply['ok'], true);
    expect(called, 'telegramSave');
    expect(params?['chatId'], '123');
  });

  test('dashboard two-FID diagnostic is isolated from energy and demo', () async {
    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      return <String, Object?>{
        'ok': true, 'socCandidatePercent': 89.0,
        'odometerCandidateKm': 42010.6, 'firmwareVerified': false,
      };
    });
    const gateway = PlatformVehicleGateway();
    final result = await gateway.sampleDashboardCandidates();
    expect(result['ok'], true);
    expect(result['firmwareVerified'], false);
    expect(calls, ['dashboardSample']);
    const demo = DemoVehicleGateway();
    expect((await demo.sampleDashboardCandidates())['reason'], 'demo_only');
    expect(calls, ['dashboardSample']);
  });

  test('explicit telemetry reading uses fixed native method', () async {
    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      return <String, Object?>{'ok': true};
    });
    const gateway = PlatformVehicleGateway();
    expect((await gateway.sampleTelemetry())['ok'], true);
    expect((await gateway.rangeCapacityStatus())['ok'], true);
    expect((await gateway.setRangeCapacity(60.0))['ok'], true);
    expect(calls, ['telemetrySample', 'rangeCapacityStatus', 'rangeCapacitySet']);
  });

  test('demo does not sample car data', () async {
    const demo = DemoVehicleGateway();
    expect((await demo.sampleTelemetry())['reason'], 'demo_only');
    expect((await demo.setRangeCapacity(60.0))['reason'], 'demo_only');
  });

  test('rule mutations stay typed and routed only to native storage', () async {
    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      return <String, Object?>{'ok': true, 'rules': []};
    });
    const gateway = PlatformVehicleGateway();
    expect((await gateway.automationStatus())['ok'], true);
    expect((await gateway.automationAdd('SOC_BELOW', 25.0))['ok'], true);
    expect((await gateway.automationToggle('abc123', true))['ok'], true);
    expect((await gateway.automationRemove('abc123'))['ok'], true);
    expect((await gateway.automationMasterSet(false))['ok'], true);
    expect(calls, [
      'automationStatus', 'automationAdd', 'automationToggle',
      'automationRemove', 'automationMasterSet',
    ]);
  });

  test('demo cannot mutate automation rules', () async {
    const gateway = DemoVehicleGateway();
    expect((await gateway.automationAdd('POWER_ON', null))['error'], 'demo_only');
    expect((await gateway.automationMasterSet(true))['error'], 'demo_only');
  });

  test('Telegram queue actions use fixed native methods', () async {
    final methods = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      methods.add(call.method);
      return <String, Object?>{'ok': true, 'pendingMessages': 0};
    });
    const gateway = PlatformVehicleGateway();
    expect((await gateway.telegramOutboxStatus())['ok'], true);
    expect((await gateway.telegramRetry())['ok'], true);
    expect((await gateway.telegramClearPending())['ok'], true);
    expect(methods, ['telegramOutboxStatus', 'telegramRetry', 'telegramClearPending']);
  });

  test('diagnostic export is explicit and demo never writes a report', () async {
    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      return <String, Object?>{'ok': true, 'location': 'Downloads/BYD-Smart-Launcher'};
    });
    const gateway = PlatformVehicleGateway();
    final response = await gateway.exportDiagnostics();
    expect(response['ok'], true);
    expect(calls, ['diagnosticsExport']);
    const demo = DemoVehicleGateway();
    expect((await demo.exportDiagnostics())['error'], 'demo_only');
    expect(calls, ['diagnosticsExport']);
  });

  test('missing Android plugin returns an explicit ADB unavailable error', () async {
    const gateway = PlatformVehicleGateway();
    expect((await gateway.connectAdb())['error'], 'not_implemented');
  });

  test('demo gateway refuses ADB connection', () async {
    const demo = DemoVehicleGateway();
    expect((await demo.connectAdb())['error'], 'demo_only');
    expect((await demo.probeAdb())['error'], 'demo_only');
    expect((await demo.probeFirmware())['error'], 'demo_only');
    expect((await demo.probeSoc())['error'], 'demo_only');
  });
}
