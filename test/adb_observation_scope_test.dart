import 'package:byd_smart_launcher/core/adb_observation_scope.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('reused ADB connect must retain the latest motion and charging cards', () {
    expect(shouldClearAdbObservations(
        'connect', {'adb': 'connected', 'sessionReused': true}), false);
    expect(shouldClearAdbObservations(
        'connect', {'adb': 'connected', 'sessionReused': false}), true);
    expect(shouldClearAdbObservations(
        'connect', {'adb': 'not_connected'}), true);
  });

  test('real disconnect clears current cards; read-only probes never do', () {
    expect(shouldClearAdbObservations('disconnect', {}), true);
    expect(shouldClearAdbObservations('motion', {'ok': true}), false);
    expect(shouldClearAdbObservations('charging', {'ok': true}), false);
    expect(shouldClearAdbObservations('fids', {'ok': true}), false);
  });
}
