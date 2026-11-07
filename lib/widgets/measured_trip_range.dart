import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class MeasuredTripRange extends StatefulWidget {
  const MeasuredTripRange({super.key});

  @override
  State<MeasuredTripRange> createState() => _MeasuredTripRangeState();
}

class _MeasuredTripRangeState extends State<MeasuredTripRange> {
  static const _channel = MethodChannel('com.ziada.byd_smart_launcher/vehicle');
  final _distance = TextEditingController();
  final _energy = TextEditingController();
  final _capacity = TextEditingController();
  final _soc = TextEditingController();
  bool _eligible = false;
  bool _busy = false;
  int _count = 0;
  double? _consumption;
  String? _message;

  @override
  void initState() {
    super.initState();
    _call('measuredTripsStatus');
  }

  @override
  void dispose() {
    _distance.dispose();
    _energy.dispose();
    _capacity.dispose();
    _soc.dispose();
    super.dispose();
  }

  double? _number(TextEditingController field) =>
      double.tryParse(field.text.trim().replaceAll(',', '.'));

  double? get _estimate {
    final soc = _number(_soc);
    final capacity = _number(_capacity);
    final consumption = _consumption;
    if (soc == null || !soc.isFinite || soc < 0 || soc > 100 ||
        capacity == null || !capacity.isFinite || capacity < 5 ||
        capacity > 200 || consumption == null) return null;
    return capacity * soc / consumption;
  }

  Future<void> _call(String method) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final status = await _channel.invokeMapMethod<String, Object?>(method, {
        if (method == 'measuredTripsRecord') ...{
          'distanceKm': _number(_distance),
          'drivingKwh': _number(_energy),
          'eligible': _eligible,
        },
      });
      if (!mounted) return;
      setState(() {
        if (status?['ok'] == true) {
          _count = (status?['tripCount'] as num?)?.toInt() ?? 0;
          _consumption = (status?['consumption'] as num?)?.toDouble();
          _message = null;
          if (method == 'measuredTripsRecord') {
            _distance.clear();
            _energy.clear();
            _eligible = false;
          }
        } else {
          _message = switch (status?['error']) {
            'invalid_trip_measurements' => 'Check the trip distance and measured energy.',
            'ev_driving_without_charging_required' => 'Confirm the trip qualifies before saving.',
            _ => 'Trip history could not be updated.',
          };
        }
      });
    } on PlatformException {
      if (mounted) setState(() => _message = 'Trip history is unavailable.');
    } on MissingPluginException {
      if (mounted) setState(() => _message = 'Trip history is unavailable.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _clear() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear measured trip history?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Clear')),
        ],
      ),
    );
    if (mounted && confirmed == true) await _call('measuredTripsClear');
  }

  Widget _field(String label, TextEditingController controller) => TextField(
    controller: controller,
    keyboardType: const TextInputType.numberWithOptions(decimal: true),
    decoration: InputDecoration(labelText: label),
    onChanged: (_) => setState(() {}),
  );

  @override
  Widget build(BuildContext context) {
    final average = _consumption;
    final estimate = _estimate;
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Text('Range from measured trips', style: Theme.of(context).textTheme.titleMedium),
      const Text('Use net battery energy measured for an EV-only trip. Include only '
          'trips with zero charging and no engine operation.'),
      _field('Trip distance (km)', _distance),
      _field('Measured driving energy (kWh)', _energy),
      CheckboxListTile(
        contentPadding: EdgeInsets.zero,
        value: _eligible,
        onChanged: _busy ? null : (value) => setState(() => _eligible = value ?? false),
        title: const Text('EV driving only, without charging'),
      ),
      Wrap(spacing: 8, children: [
        FilledButton(
          onPressed: _busy || !_eligible ? null : () => _call('measuredTripsRecord'),
          child: const Text('Save measured trip'),
        ),
        TextButton(onPressed: _busy || _count == 0 ? null : _clear, child: const Text('Clear trip history')),
      ]),
      if (_message != null) Text(_message!),
      Text(average == null ? 'No measured trips recorded.'
          : '$_count trips • ${average.toStringAsFixed(1)} kWh/100 km'),
      _field('Current BYD battery SOC (%)', _soc),
      _field('Usable battery capacity (kWh)', _capacity),
      Text(estimate == null ? 'Trip-based range estimate: —'
          : 'Trip-based range estimate: ${estimate.toStringAsFixed(0)} km'),
    ]);
  }
}
