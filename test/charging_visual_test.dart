import 'package:byd_smart_launcher/core/charging_visual_mode.dart';
import 'package:byd_smart_launcher/main.dart';
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

  testWidgets('design-only green charging flow requires manual preview toggle',
      (tester) async {
    tester.view.physicalSize = const Size(1600, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(const LauncherApp());
    await tester.pump();

    expect(find.byType(ChargingFlowScene), findsOneWidget);
    expect(find.text('CHARGING STATE NOT VERIFIED'), findsOneWidget);
    expect(find.text('DESIGN PREVIEW  •  NOT LIVE CHARGING'), findsNothing);

    final previewSwitch =
        find.widgetWithText(SwitchListTile, 'Preview animated charging cable');
    expect(previewSwitch, findsOneWidget);
    expect(tester.widget<SwitchListTile>(previewSwitch).value, isFalse);

    await tester.tap(previewSwitch);
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.widget<SwitchListTile>(previewSwitch).value, isTrue);
    expect(find.text('DESIGN PREVIEW  •  NOT LIVE CHARGING'), findsOneWidget);

    await tester.tap(previewSwitch);
    await tester.pump(const Duration(milliseconds: 200));
    expect(tester.widget<SwitchListTile>(previewSwitch).value, isFalse);
    expect(find.text('CHARGING STATE NOT VERIFIED'), findsOneWidget);
  });
}
