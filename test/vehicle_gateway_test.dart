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
    expect((await gateway.probeSoc())['adb'], 'not_connected');
    expect((await gateway.disconnectAdb())['adb'], 'not_connected');
    expect(called, ['adbConnect', 'adbProbe', 'adbProbeSoc', 'adbDisconnect']);
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

  test('missing Android plugin returns an explicit ADB unavailable error', () async {
    const gateway = PlatformVehicleGateway();
    expect((await gateway.connectAdb())['error'], 'not_implemented');
  });

  test('demo gateway refuses ADB connection', () async {
    const demo = DemoVehicleGateway();
    expect((await demo.connectAdb())['error'], 'demo_only');
    expect((await demo.probeAdb())['error'], 'demo_only');
    expect((await demo.probeSoc())['error'], 'demo_only');
  });
}
