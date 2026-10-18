/// Vehicle telemetry contract: absent values are unknown, not zero.
class VehicleSnapshot {
  final bool connected;
  final String adbState;
  final String helperState;
  final int? socPercent;
  final double? speedKmh;
  final String? gear;
  final double? powerKw;
  final String? turnSignal;
  final double? rangeEstimateKm;
  final double? avgKwhPer100km;
  final DateTime? capturedAt;
  final String source;

  const VehicleSnapshot({
    required this.connected,
    required this.adbState,
    required this.helperState,
    this.socPercent,
    this.speedKmh,
    this.gear,
    this.powerKw,
    this.turnSignal,
    this.rangeEstimateKm,
    this.avgKwhPer100km,
    this.capturedAt,
    required this.source,
  });

  factory VehicleSnapshot.unavailable() => const VehicleSnapshot(
        connected: false,
        adbState: 'not_configured',
        helperState: 'unavailable',
        source: 'unavailable',
      );

  factory VehicleSnapshot.fromMap(Map<Object?, Object?> map) {
    num? number(String key) => map[key] is num ? map[key] as num : null;
    final timestamp = number('capturedAtEpochMs')?.toInt();
    return VehicleSnapshot(
      connected: map['connected'] == true,
      adbState: map['adbState'] as String? ?? 'unknown',
      helperState: map['helperState'] as String? ?? 'unknown',
      socPercent: number('socPercent')?.toInt(),
      speedKmh: number('speedKmh')?.toDouble(),
      gear: map['gear'] as String?,
      powerKw: number('powerKw')?.toDouble(),
      turnSignal: map['turnSignal'] as String?,
      rangeEstimateKm: number('rangeEstimateKm')?.toDouble(),
      avgKwhPer100km: number('avgKwhPer100km')?.toDouble(),
      capturedAt: timestamp == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(timestamp),
      source: map['source'] as String? ?? 'unknown',
    );
  }

  bool get hasLiveVehicleData => connected && source == 'live';
}
