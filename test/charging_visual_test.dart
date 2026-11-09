import 'package:byd_smart_launcher/core/charging_visual_mode.dart';
import 'package:byd_smart_launcher/widgets/charging_flow_scene.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('actual charging cannot be inferred from unverified FIDs or 100% SOC', () {
    for (final value in [
      <Object?, Object?>{
        'ok': true,
        'chargeStateCandidate': 'charging_candidate',
        'gunKindCandidate': 'dc_candidate',
        'firmwareVerified': false,
      },
      <Object?, Object?>{
        'socCandidatePercent': 100,
        'bmsStateRaw': 15,
      },
      <Object?, Object?>{
        'chargingState': 'charging',
        'firmwareVerified': true,
      },
    ]) {
      expect(
        resolveChargingVisualMode(
          previewRequested: false,
          chargingDiagnostic: value,
        ),
        ChargingVisualMode.idle,
      );
      expect(
        resolveChargingVisualMode(
          previewRequested: true,
          chargingDiagnostic: value,
        ),
        ChargingVisualMode.designPreview,
      );
    }
  });

  testWidgets('animates only for monitor charging state or the explicit preview', (tester) async {
    Future<void> show({bool preview = false, String? state}) async {
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(body: ChargingFlowScene(previewEnabled: preview, chargingState: state)),
      ));
      await tester.pump(const Duration(milliseconds: 200));
    }

    await show();
    expect(find.text('CHARGING DATA UNAVAILABLE'), findsOneWidget);
    expect(tester.hasRunningAnimations, isFalse);
    await show(state: 'CHARGING');
    expect(find.text('CHARGING'), findsOneWidget);
    expect(tester.hasRunningAnimations, isTrue);
    await show(state: 'FINISHED');
    expect(find.text('CHARGE COMPLETE'), findsOneWidget);
    expect(tester.hasRunningAnimations, isFalse);
    await show(preview: true);
    expect(find.text('DESIGN PREVIEW  •  NOT LIVE CHARGING'), findsOneWidget);
    await show();
  });
}
