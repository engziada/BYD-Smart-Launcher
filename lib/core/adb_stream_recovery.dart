/// Read-only recovery decisions for the operator-enabled native telemetry stream.
///
/// Never recover from an intentional disconnect, app backgrounding, firmware
/// mismatch, stale values, lack of an event listener or explicit Stop.
/// One bounded retry is permitted only for an unexpected transport disconnect.
bool shouldAttemptAdbStreamRecovery({
  required String? reason,
  required bool inForeground,
  required bool operatorDisconnected,
  required bool recoveryAlreadyTried,
}) {
  const transientLosses = {'adb_disconnected', 'adb_session_changed'};
  return inForeground &&
      !operatorDisconnected &&
      !recoveryAlreadyTried &&
      transientLosses.contains(reason);
}

/// A newly attached native EventChannel listener may replay the *previous*
/// session's stopped state. It must not terminate the next explicit Start
/// before receiving that Start's initializing/candidate event.
bool ignoreUnarmedNativeStop({
  required bool seenCurrentSessionInitialization,
}) =>
    !seenCurrentSessionInitialization;
