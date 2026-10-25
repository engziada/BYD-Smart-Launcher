import 'package:byd_smart_launcher/core/adb_stream_recovery.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('only unexpected live foreground transport loss gets one recovery', () {
    for (final reason in ['adb_disconnected', 'adb_session_changed']) {
      expect(
        shouldAttemptAdbStreamRecovery(
          reason: reason,
          inForeground: true,
          operatorDisconnected: false,
          recoveryAlreadyTried: false,
        ),
        isTrue,
      );
    }
    for (final reason in [
      'operator_stopped',
      'listener_detached',
      'app_not_foreground',
      'activity_destroyed',
      'firmware_fids_or_scale_unvalidated',
      'read_failed',
      'invalid_or_stale_candidate',
      null,
    ]) {
      expect(
        shouldAttemptAdbStreamRecovery(
          reason: reason,
          inForeground: true,
          operatorDisconnected: false,
          recoveryAlreadyTried: false,
        ),
        isFalse,
        reason: 'Should refuse recovery for $reason',
      );
    }
  });

  test('no reconnect while backgrounded, manually disconnected or after retry', () {
    expect(
      shouldAttemptAdbStreamRecovery(
        reason: 'adb_disconnected',
        inForeground: false,
        operatorDisconnected: false,
        recoveryAlreadyTried: false,
      ),
      isFalse,
    );
    expect(
      shouldAttemptAdbStreamRecovery(
        reason: 'adb_session_changed',
        inForeground: true,
        operatorDisconnected: true,
        recoveryAlreadyTried: false,
      ),
      isFalse,
    );
    expect(
      shouldAttemptAdbStreamRecovery(
        reason: 'adb_disconnected',
        inForeground: true,
        operatorDisconnected: false,
        recoveryAlreadyTried: true,
      ),
      isFalse,
    );
  });

  test('replayed stopped status before native initialisation is ignored', () {
    expect(ignoreUnarmedNativeStop(seenCurrentSessionInitialization: false),
        isTrue);
    expect(ignoreUnarmedNativeStop(seenCurrentSessionInitialization: true),
        isFalse);
  });
}
