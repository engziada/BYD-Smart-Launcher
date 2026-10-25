import 'package:byd_smart_launcher/core/planning_range.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('calculates useful operator-input planning estimate, not BMS range', () {
    final r = calculatePlanningRange(
      socPercent: 50,
      usableCapacityKwh: 80,
      averageKwhPer100Km: 20,
    );
    expect(r, isNotNull);
    expect(r!.rangeKm, 200);
    expect(r.socPercent, 50);
    expect(calculatePlanningRange(
      socPercent: 0, usableCapacityKwh: 80, averageKwhPer100Km: 20,
    )!.rangeKm, 0);
    expect(calculatePlanningRange(
      socPercent: 100, usableCapacityKwh: 65.5, averageKwhPer100Km: 15,
    )!.rangeKm, closeTo(436.6667, 0.01));
  });

  test('unknown, invalid, extreme or non-finite inputs never yield a range', () {
    final invalid = [
      calculatePlanningRange(socPercent: null, usableCapacityKwh: 80, averageKwhPer100Km: 20),
      calculatePlanningRange(socPercent: -1, usableCapacityKwh: 80, averageKwhPer100Km: 20),
      calculatePlanningRange(socPercent: 101, usableCapacityKwh: 80, averageKwhPer100Km: 20),
      calculatePlanningRange(socPercent: 50, usableCapacityKwh: null, averageKwhPer100Km: 20),
      calculatePlanningRange(socPercent: 50, usableCapacityKwh: 250, averageKwhPer100Km: 20),
      calculatePlanningRange(socPercent: 50, usableCapacityKwh: 80, averageKwhPer100Km: 0),
      calculatePlanningRange(socPercent: 50, usableCapacityKwh: 80, averageKwhPer100Km: 60),
      calculatePlanningRange(socPercent: double.nan, usableCapacityKwh: 80, averageKwhPer100Km: 20),
      calculatePlanningRange(socPercent: 50, usableCapacityKwh: double.infinity, averageKwhPer100Km: 20),
    ];
    for (final v in invalid) {
      expect(v, isNull);
    }
  });
}
