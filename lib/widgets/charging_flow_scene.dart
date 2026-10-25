import 'dart:math' as math;
import 'dart:ui' show MaskFilter;

import 'package:flutter/material.dart';

/// Original vector illustration. No Kinex/BYDMate models, assets or internals.
///
/// The moving green energy pulses are a DESIGN PREVIEW until an independently
/// verified, fresh charging-state signal exists on this exact BYD firmware.
class ChargingFlowScene extends StatefulWidget {
  const ChargingFlowScene({
    super.key,
    required this.previewEnabled,
    this.socCandidate,
  });

  final bool previewEnabled;
  final double? socCandidate;

  @override
  State<ChargingFlowScene> createState() => _ChargingFlowSceneState();
}

class _ChargingFlowSceneState extends State<ChargingFlowScene>
    with SingleTickerProviderStateMixin {
  late final AnimationController _flow = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 2200),
  );

  @override
  void initState() {
    super.initState();
    if (widget.previewEnabled) _flow.repeat();
  }

  @override
  void didUpdateWidget(covariant ChargingFlowScene oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.previewEnabled != oldWidget.previewEnabled) {
      if (widget.previewEnabled) {
        _flow.repeat();
      } else {
        _flow.stop();
        _flow.value = 0;
      }
    }
  }

  @override
  void dispose() {
    _flow.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final double? soc = widget.socCandidate;
    final socLabel = soc != null && soc.isFinite && soc >= 0 && soc <= 100
        ? '${soc.toStringAsFixed(0)}% SOC candidate'
        : 'SOC unavailable';

    return Semantics(
      label: widget.previewEnabled
          ? 'Animated charging cable design preview. Not live charging data.'
          : 'Vehicle visualization. Actual charging state not verified.',
      child: Container(
        height: 236,
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF122333), Color(0xFF0B1520), Color(0xFF192C3A)],
          ),
          border: Border.all(color: const Color(0xFF355369)),
        ),
        child: Stack(
          children: [
            Positioned.fill(
              child: RepaintBoundary(
                child: AnimatedBuilder(
                  animation: _flow,
                  builder: (context, _) => CustomPaint(
                    painter: _VehicleChargePainter(
                      progress: _flow.value,
                      flowEnabled: widget.previewEnabled,
                    ),
                  ),
                ),
              ),
            ),
            const Positioned(
              left: 20,
              top: 16,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('SONG PLUS  /  ENERGY FLOW',
                      style: TextStyle(
                        color: Color(0xFFB5CADD),
                        fontSize: 11,
                        letterSpacing: 1.4,
                        fontWeight: FontWeight.w700,
                      )),
                  SizedBox(height: 6),
                  Text('Charging visualizer',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                        fontWeight: FontWeight.w600,
                      )),
                ],
              ),
            ),
            Positioned(
              left: 20,
              bottom: 18,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.previewEnabled
                        ? 'DESIGN PREVIEW  •  NOT LIVE CHARGING'
                        : 'CHARGING STATE NOT VERIFIED',
                    style: TextStyle(
                      color: widget.previewEnabled
                          ? const Color(0xFF78EDAE)
                          : const Color(0xFFB5CADD),
                      fontSize: 11,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 0.7,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    socLabel,
                    style: const TextStyle(
                      color: Color(0xFFE5F0F8),
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            Positioned(
              right: 18,
              bottom: 18,
              child: Icon(
                widget.previewEnabled
                    ? Icons.bolt_rounded
                    : Icons.power_off_outlined,
                color: widget.previewEnabled
                    ? const Color(0xFF79EDB0)
                    : const Color(0xFF91A6B6),
                size: 28,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _VehicleChargePainter extends CustomPainter {
  _VehicleChargePainter({
    required this.progress,
    required this.flowEnabled,
  });

  final double progress;
  final bool flowEnabled;

  @override
  void paint(Canvas canvas, Size size) {
    // Scale one authored 880x236 scene to arbitrary head-unit widths.
    final scale = math.min(size.width / 880, size.height / 236);
    canvas.save();
    canvas.translate(
      (size.width - 880 * scale) / 2,
      (size.height - 236 * scale) / 2,
    );
    canvas.scale(scale);

    final floor = Paint()
      ..shader = const RadialGradient(
        colors: [Color(0x553BB39C), Color(0x00152B37)],
      ).createShader(const Rect.fromLTWH(300, 140, 500, 105));
    canvas.drawOval(const Rect.fromLTWH(275, 160, 535, 88), floor);

    // Rear charging cable leads to the left/rear quarter of an original
    // stylized vehicle silhouette, with directional moving electric pulses.
    final cable = Path()
      ..moveTo(94, 188)
      ..cubicTo(161, 191, 196, 153, 273, 151)
      ..quadraticBezierTo(296, 149, 309, 154);
    canvas.drawPath(cable, Paint()
      ..color = const Color(0xFF071419)
      ..strokeWidth = 15
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke);
    canvas.drawPath(cable, Paint()
      ..color = const Color(0xFF426C66)
      ..strokeWidth = 6
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke);
    if (flowEnabled) {
      final green = const Color(0xFF52FF9A);
      canvas.drawPath(cable, Paint()
        ..color = green.withValues(alpha: 0.35)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 9
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 9));
      canvas.drawPath(cable, Paint()
        ..color = green.withValues(alpha: 0.80)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.2
        ..strokeCap = StrokeCap.round);
      for (final metric in cable.computeMetrics()) {
        for (var i = 0; i < 8; i++) {
          final t = (progress + i / 8) % 1.0;
          final tangent = metric.getTangentForOffset(metric.length * t);
          if (tangent == null) continue;
          final point = tangent.position;
          final alpha = 0.35 + 0.65 * (1 - (t - 0.5).abs());
          canvas.drawCircle(point, 8, Paint()
            ..color = green.withValues(alpha: alpha * 0.75)
            ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 9));
          canvas.drawCircle(point, 3.1, Paint()
            ..color = const Color(0xFFDDFFEA));
        }
      }
    }

    // Charging post and connector, original vector forms.
    canvas.drawRRect(
      RRect.fromRectAndRadius(const Rect.fromLTWH(59, 118, 54, 94),
          const Radius.circular(10)),
      Paint()..color = const Color(0xFF2E4553),
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(const Rect.fromLTWH(65, 123, 42, 46),
          const Radius.circular(7)),
      Paint()..color = const Color(0xFF142631),
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(const Rect.fromLTWH(73, 133, 26, 19),
          const Radius.circular(4)),
      Paint()..color = flowEnabled
          ? const Color(0xFF52E899) : const Color(0xFF617986),
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(const Rect.fromLTWH(78, 171, 16, 24),
          const Radius.circular(4)),
      Paint()..color = const Color(0xFF10212A),
    );

    // Vehicle chassis, wheels and gloss. No third-party 3D model is used.
    for (final center in [const Offset(402, 191), const Offset(695, 191)]) {
      canvas.drawCircle(center, 36, Paint()
        ..color = const Color(0xFF071118)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6));
      canvas.drawCircle(center, 30, Paint()..color = const Color(0xFF080D14));
      canvas.drawCircle(center, 20, Paint()..color = const Color(0xFF42545F));
      canvas.drawCircle(center, 13, Paint()..color = const Color(0xFF82939D));
      canvas.drawCircle(center, 5, Paint()..color = const Color(0xFF203340));
    }

    final chassis = Path()
      ..moveTo(294, 166)
      ..quadraticBezierTo(292, 152, 315, 140)
      ..lineTo(358, 127)
      ..quadraticBezierTo(398, 90, 462, 85)
      ..lineTo(605, 84)
      ..quadraticBezierTo(639, 90, 675, 130)
      ..lineTo(759, 143)
      ..quadraticBezierTo(778, 154, 781, 169)
      ..lineTo(771, 183)
      ..lineTo(732, 184)
      ..quadraticBezierTo(727, 152, 696, 151)
      ..quadraticBezierTo(663, 151, 658, 184)
      ..lineTo(438, 184)
      ..quadraticBezierTo(436, 153, 402, 152)
      ..quadraticBezierTo(369, 153, 364, 184)
      ..lineTo(315, 182)
      ..close();
    canvas.drawPath(chassis, Paint()
      ..shader = const LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          Color(0xFFB9CAD5), Color(0xFF526E84), Color(0xFF183447),
        ],
        stops: [0, 0.50, 1],
      ).createShader(const Rect.fromLTWH(292, 83, 492, 104)));
    canvas.drawPath(chassis, Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5
      ..color = const Color(0xFFCCE0E7));

    final windows = Path()
      ..moveTo(383, 126)
      ..quadraticBezierTo(431, 94, 472, 93)
      ..lineTo(600, 93)
      ..quadraticBezierTo(630, 98, 650, 129)
      ..close();
    canvas.drawPath(windows, Paint()
      ..shader = const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF143245), Color(0xFF6594AB), Color(0xFF122A3C)],
      ).createShader(const Rect.fromLTWH(375, 90, 275, 45)));
    canvas.drawLine(const Offset(526, 91), const Offset(526, 129),
        Paint()..color = const Color(0xFF243F50)..strokeWidth = 5);
    canvas.drawLine(const Offset(468, 139), const Offset(468, 177),
        Paint()..color = const Color(0x88436376)..strokeWidth = 2);
    canvas.drawLine(const Offset(624, 138), const Offset(623, 177),
        Paint()..color = const Color(0x88436376)..strokeWidth = 2);

    for (final dx in [485.0, 642.0]) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(Rect.fromLTWH(dx, 140, 22, 3.7),
            const Radius.circular(2)),
        Paint()..color = const Color(0xFFCFDEE8),
      );
    }
    // Front light and rear energy inlet.
    canvas.drawRRect(
      RRect.fromRectAndRadius(const Rect.fromLTWH(760, 152, 17, 8),
          const Radius.circular(4)),
      Paint()..color = const Color(0xFFFFE5B0),
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(const Rect.fromLTWH(296, 148, 15, 13),
          const Radius.circular(5)),
      Paint()..color = flowEnabled
          ? const Color(0xFF66FFAA)
          : const Color(0xFF87A4AF),
    );
    if (flowEnabled) {
      canvas.drawCircle(const Offset(304, 154), 19, Paint()
        ..color = const Color(0xFF48FF96).withValues(alpha: 0.62)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 15));
    }

    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _VehicleChargePainter oldDelegate) =>
      oldDelegate.progress != progress ||
      oldDelegate.flowEnabled != flowEnabled;
}
