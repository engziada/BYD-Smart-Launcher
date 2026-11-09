import 'package:byd_smart_launcher/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('Light is the default and diagnostics stay in Advanced',
      (tester) async {
    tester.view.physicalSize = const Size(1600, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(const LauncherApp());
    await tester.pump();
    await tester.tap(find.text('Settings').last);
    await tester.pump();

    final lightSwitch = find.widgetWithText(SwitchListTile, 'Light scene mode');
    expect(lightSwitch, findsOneWidget);
    expect(tester.widget<SwitchListTile>(lightSwitch).value, isTrue);
    expect(Theme.of(tester.element(lightSwitch)).brightness, Brightness.light);
    expect(find.text('Export sanitized diagnostics JSON'), findsNothing);

    await tester.tap(lightSwitch);
    await tester.pumpAndSettle();
    expect(Theme.of(tester.element(lightSwitch)).brightness, Brightness.dark);
    await tester.tap(lightSwitch);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Advanced'));
    await tester.pumpAndSettle();
    expect(find.text('Export sanitized diagnostics JSON'), findsOneWidget);
    final demoSwitch = find.widgetWithText(SwitchListTile, 'Explicit demo mode');
    expect(tester.widget<SwitchListTile>(demoSwitch).value, isFalse);
  });
}
