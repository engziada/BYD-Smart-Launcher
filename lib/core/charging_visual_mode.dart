/// The imported DiLink charging FIDs are not yet verified on this Song Plus.
///
/// Visual animation must never be inferred from SOC 100%, an attached plug,
/// the Leopard 3 charging enum, or an unverified motion/charging candidate.
/// Until a separate on-car acceptance adds a verified state contract, the
/// animation is available only as a clearly labelled, explicit design preview.
enum ChargingVisualMode { idle, designPreview }

ChargingVisualMode resolveChargingVisualMode({
  required bool previewRequested,
  required Map<Object?, Object?> chargingDiagnostic,
}) {
  // Intentionally refuse to promote ANY current diagnostic into a live
  // charging assertion, even if it is named "charging_candidate".
  return previewRequested
      ? ChargingVisualMode.designPreview
      : ChargingVisualMode.idle;
}
