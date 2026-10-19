import 'package:flutter/services.dart';

import 'vehicle_snapshot.dart';

abstract interface class VehicleGateway {
  Future<VehicleSnapshot> getSnapshot();
  Stream<VehicleSnapshot> get snapshots;
  Future<Map<Object?, Object?>> getDiagnostics();
  Future<Map<Object?, Object?>> connectAdb();
  Future<Map<Object?, Object?>> disconnectAdb();
  Future<Map<Object?, Object?>> probeAdb();
  Future<Map<Object?, Object?>> probeSoc();
  Future<Map<Object?, Object?>> probeFirmware();
  Future<Map<Object?, Object?>> sampleTelemetry();
  Future<Map<Object?, Object?>> rangeCapacityStatus();
  Future<Map<Object?, Object?>> setRangeCapacity(double? kwh);
  Future<Map<Object?, Object?>> automationStatus();
  Future<Map<Object?, Object?>> automationAdd(String kind, double? thresholdPercent);
  Future<Map<Object?, Object?>> automationToggle(String id, bool enabled);
  Future<Map<Object?, Object?>> automationRemove(String id);
  Future<Map<Object?, Object?>> automationMasterSet(bool enabled);
  Future<Map<Object?, Object?>> telegramStatus();
  Future<Map<Object?, Object?>> telegramSave(String token, String chatId);
  Future<Map<Object?, Object?>> telegramClear();
  Future<Map<Object?, Object?>> telegramSendTest();
  Future<Map<Object?, Object?>> telegramOutboxStatus();
  Future<Map<Object?, Object?>> telegramRetry();
  Future<Map<Object?, Object?>> telegramClearPending();
  void dispose();
}

/// All exposed operations are read-only. Android bridge returns unknown until connected.
class PlatformVehicleGateway implements VehicleGateway {
  const PlatformVehicleGateway();

  static const MethodChannel _methods =
      MethodChannel('com.ziada.byd_smart_launcher/vehicle');
  static const EventChannel _events =
      EventChannel('com.ziada.byd_smart_launcher/telemetry');

  @override
  Future<VehicleSnapshot> getSnapshot() async {
    try {
      final raw = await _methods.invokeMapMethod<Object?, Object?>('getSnapshot');
      return raw == null
          ? VehicleSnapshot.unavailable()
          : VehicleSnapshot.fromMap(raw);
    } on PlatformException {
      return VehicleSnapshot.unavailable();
    } on MissingPluginException {
      return VehicleSnapshot.unavailable();
    }
  }

  @override
  Stream<VehicleSnapshot> get snapshots => _events
      .receiveBroadcastStream()
      .map((dynamic value) => value is Map
          ? VehicleSnapshot.fromMap(value.cast<Object?, Object?>())
          : VehicleSnapshot.unavailable());

  @override
  Future<Map<Object?, Object?>> getDiagnostics() async {
    try {
      return await _methods
              .invokeMapMethod<Object?, Object?>('getDiagnostics') ??
          {};
    } on PlatformException {
      return {'error': 'platform_unavailable'};
    } on MissingPluginException {
      return {'error': 'not_implemented'};
    }
  }

  Future<Map<Object?, Object?>> _adbAction(String name) async {
    try {
      return await _methods.invokeMapMethod<Object?, Object?>(name) ?? {};
    } on PlatformException {
      return {'error': 'platform_unavailable'};
    } on MissingPluginException {
      return {'error': 'not_implemented'};
    }
  }

  @override
  Future<Map<Object?, Object?>> connectAdb() => _adbAction('adbConnect');

  @override
  Future<Map<Object?, Object?>> disconnectAdb() => _adbAction('adbDisconnect');

  @override
  Future<Map<Object?, Object?>> probeAdb() => _adbAction('adbProbe');

  @override
  Future<Map<Object?, Object?>> probeSoc() => _adbAction('adbProbeSoc');

  @override
  Future<Map<Object?, Object?>> probeFirmware() => _adbAction('adbProbeFirmware');

  @override
  Future<Map<Object?, Object?>> sampleTelemetry() => _adbAction('telemetrySample');

  @override
  Future<Map<Object?, Object?>> rangeCapacityStatus() => _adbAction('rangeCapacityStatus');

  @override
  Future<Map<Object?, Object?>> setRangeCapacity(double? kwh) async {
    try {
      return await _methods.invokeMapMethod<Object?, Object?>(
            'rangeCapacitySet', {'kwh': kwh},
          ) ?? {};
    } on PlatformException {
      return {'ok': false, 'error': 'platform_unavailable'};
    } on MissingPluginException {
      return {'ok': false, 'error': 'not_implemented'};
    }
  }

  @override
  Future<Map<Object?, Object?>> automationStatus() => _adbAction('automationStatus');

  Future<Map<Object?, Object?>> _automationChange(String method, Map<String, Object?> args) async {
    try {
      return await _methods.invokeMapMethod<Object?, Object?>(method, args) ?? {};
    } on PlatformException {
      return {'ok': false, 'error': 'platform_unavailable'};
    } on MissingPluginException {
      return {'ok': false, 'error': 'not_implemented'};
    }
  }

  @override
  Future<Map<Object?, Object?>> automationAdd(String kind, double? thresholdPercent) =>
      _automationChange('automationAdd', {'kind': kind, 'thresholdPercent': thresholdPercent});

  @override
  Future<Map<Object?, Object?>> automationToggle(String id, bool enabled) =>
      _automationChange('automationToggle', {'id': id, 'enabled': enabled});

  @override
  Future<Map<Object?, Object?>> automationRemove(String id) =>
      _automationChange('automationRemove', {'id': id});

  @override
  Future<Map<Object?, Object?>> automationMasterSet(bool enabled) =>
      _automationChange('automationMasterSet', {'enabled': enabled});

  @override
  Future<Map<Object?, Object?>> telegramStatus() => _adbAction('telegramStatus');

  @override
  Future<Map<Object?, Object?>> telegramSave(String token, String chatId) async {
    try {
      return await _methods.invokeMapMethod<Object?, Object?>(
            'telegramSave', {'token': token, 'chatId': chatId},
          ) ??
          {};
    } on PlatformException {
      return {'ok': false, 'error': 'platform_unavailable'};
    } on MissingPluginException {
      return {'ok': false, 'error': 'not_implemented'};
    }
  }

  @override
  Future<Map<Object?, Object?>> telegramClear() => _adbAction('telegramClear');

  @override
  Future<Map<Object?, Object?>> telegramSendTest() => _adbAction('telegramTest');

  @override
  Future<Map<Object?, Object?>> telegramOutboxStatus() =>
      _adbAction('telegramOutboxStatus');

  @override
  Future<Map<Object?, Object?>> telegramRetry() => _adbAction('telegramRetry');

  @override
  Future<Map<Object?, Object?>> telegramClearPending() =>
      _adbAction('telegramClearPending');

  @override
  void dispose() {}
}

/// DEMO is opt-in only, and can never dispatch commands to the car.
class DemoVehicleGateway implements VehicleGateway {
  const DemoVehicleGateway();

  @override
  Future<VehicleSnapshot> getSnapshot() async => VehicleSnapshot.fromMap({
        'connected': false,
        'adbState': 'demo_only',
        'helperState': 'demo_only',
        'socPercent': 53,
        'speedKmh': 0.0,
        'gear': 'P',
        'powerKw': 0.0,
        'rangeEstimateKm': 226.0,
        'avgKwhPer100km': 15.5,
        'source': 'demo',
        'capturedAtEpochMs': DateTime.now().millisecondsSinceEpoch,
      });

  @override
  Stream<VehicleSnapshot> get snapshots => Stream.fromFuture(getSnapshot());

  @override
  Future<Map<Object?, Object?>> getDiagnostics() async =>
      {'mode': 'demo', 'carAccess': false};

  @override
  Future<Map<Object?, Object?>> connectAdb() async => {'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> disconnectAdb() async => {'mode': 'demo'};

  @override
  Future<Map<Object?, Object?>> probeAdb() async => {'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> probeSoc() async => {'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> probeFirmware() async => {'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> sampleTelemetry() async =>
      {'ok': false, 'reason': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> rangeCapacityStatus() async =>
      {'ok': false, 'reason': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> setRangeCapacity(double? kwh) async =>
      {'ok': false, 'reason': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> automationStatus() async =>
      {'ok': true, 'masterEnabled': false, 'executionConnected': false, 'rules': []};

  @override
  Future<Map<Object?, Object?>> automationAdd(String kind, double? thresholdPercent) async =>
      {'ok': false, 'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> automationToggle(String id, bool enabled) async =>
      {'ok': false, 'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> automationRemove(String id) async =>
      {'ok': false, 'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> automationMasterSet(bool enabled) async =>
      {'ok': false, 'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> telegramStatus() async =>
      {'configured': false, 'mode': 'demo'};

  @override
  Future<Map<Object?, Object?>> telegramSave(String token, String chatId) async =>
      {'ok': false, 'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> telegramClear() async =>
      {'ok': false, 'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> telegramSendTest() async =>
      {'ok': false, 'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> telegramOutboxStatus() async =>
      {'ok': true, 'pendingMessages': 0};

  @override
  Future<Map<Object?, Object?>> telegramRetry() async =>
      {'ok': false, 'error': 'demo_only'};

  @override
  Future<Map<Object?, Object?>> telegramClearPending() async =>
      {'ok': false, 'error': 'demo_only'};

  @override
  void dispose() {}
}
