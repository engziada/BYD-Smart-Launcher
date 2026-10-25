/// Planning-only range estimate calculated from a user-supplied usable battery
/// capacity and user-supplied average consumption. This is NOT a measured BMS
/// consumption or certified real range; every input has explicit provenance.
class PlanningRangeEstimate {
  const PlanningRangeEstimate({
    required this.socPercent,
    required this.capacityKwh,
    required this.averageKwhPer100Km,
    required this.rangeKm,
  });

  final double socPercent;
  final double capacityKwh;
  final double averageKwhPer100Km;
  final double rangeKm;
}

PlanningRangeEstimate? calculatePlanningRange({
  required num? socPercent,
  required num? usableCapacityKwh,
  required num? averageKwhPer100Km,
}) {
  final soc = socPercent?.toDouble();
  final capacity = usableCapacityKwh?.toDouble();
  final average = averageKwhPer100Km?.toDouble();
  if (soc == null || capacity == null || average == null ||
      !soc.isFinite || !capacity.isFinite || !average.isFinite ||
      soc < 0 || soc > 100 ||
      capacity < 5 || capacity > 200 ||
      average < 5 || average > 45) {
    return null;
  }
  final result = (capacity * soc / 100) / average * 100;
  if (!result.isFinite || result < 0 || result > 4000) return null;
  return PlanningRangeEstimate(
    socPercent: soc,
    capacityKwh: capacity,
    averageKwhPer100Km: average,
    rangeKm: result,
  );
}
