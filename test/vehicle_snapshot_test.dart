import 'package:flutter_test/flutter_test.dart';
import 'package:byd_smart_launcher/core/vehicle_snapshot.dart';

void main() {
  test('missing data remains unavailable', () {
    final snapshot = VehicleSnapshot.fromMap({
      'connected': false,
      'adbState': 'not_configured',
    });
    expect(snapshot.socPercent, isNull);
    expect(snapshot.speedKmh, isNull);
    expect(snapshot.rangeEstimateKm, isNull);
    expect(snapshot.hasLiveVehicleData, isFalse);
  });

  test('numeric telemetry parses into typed readings', () {
    final snapshot = VehicleSnapshot.fromMap({
      'connected': true,
      'adbState': 'connected',
      'helperState': 'running',
      'socPercent': 53,
      'speedKmh': 29,
      'rangeEstimateKm': 218.5,
      'avgKwhPer100km': 13.6,
      'source': 'live',
      'capturedAtEpochMs': 1729209600000,
    });
    expect(snapshot.socPercent, 53);
    expect(snapshot.speedKmh, 29.0);
    expect(snapshot.rangeEstimateKm, 218.5);
    expect(snapshot.hasLiveVehicleData, isTrue);
    expect(snapshot.capturedAt, isNotNull);
  });

  test('demo does not masquerade as live vehicle data', () {
    final snapshot = VehicleSnapshot.fromMap({
      'connected': true,
      'source': 'demo',
    });
    expect(snapshot.hasLiveVehicleData, isFalse);
  });
}
