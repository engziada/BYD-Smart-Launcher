import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class ComfortControls extends StatefulWidget {
  const ComfortControls({
    super.key,
    required this.readings,
    required this.busy,
    required this.demo,
    required this.refresh,
    required this.write,
  });

  final Map<Object?, Object?> readings;
  final bool busy;
  final bool demo;
  final Future<void> Function() refresh;
  final Future<Map<Object?, Object?>> Function(String, int) write;

  @override
  State<ComfortControls> createState() => _ComfortControlsState();
}

class _ComfortControlsState extends State<ComfortControls> {
  static const _controls = {
    'ac_power': ('A/C power', 0, 1, '0 = Off, 1 = On'),
    'ac_fan': ('A/C fan level', 0, 7, 'Level 0–7'),
    'ac_temperature': ('A/C set temperature', 17, 33, '17–33 °C'),
    'front_left': ('Front left window', 0, 100, '0–100% open'),
    'front_right': ('Front right window', 0, 100, '0–100% open'),
    'rear_left': ('Rear left window', 0, 100, '0–100% open'),
    'rear_right': ('Rear right window', 0, 100, '0–100% open'),
  };
  final _target = TextEditingController();
  String _selected = 'ac_power';
  bool _writing = false;
  String? _status;

  @override
  void dispose() {
    _target.dispose();
    super.dispose();
  }

  Future<void> _apply(int value) async {
    setState(() {
      _writing = true;
      _status = null;
    });
    try {
      final result = await widget.write(_selected, value);
      if (!mounted) return;
      final state = result['readback'];
      setState(() {
        _status = switch (result['status']) {
          'confirmed' => 'Confirmed by vehicle readback: $state.',
          'already_at_target' => 'The vehicle already reports $state.',
          'cancelled' => 'Command cancelled.',
          'confirmation_expired' => 'Confirmation expired. Review the target and try again.',
          'park_not_verified' => 'Park and zero speed could not be confirmed.',
          'firmware_not_verified' || 'mapping_not_verified' => 'This control needs on-car verification.',
          'write_outcome_unknown' || 'sent_unconfirmed' => 'Check the factory display before issuing another command.',
          'write_rejected' => 'The vehicle service rejected the command.',
          'celsius_required' => 'The vehicle must report Celsius for this command.',
          _ => 'Control unavailable (${result['status'] ?? 'unknown'}).',
        };
      });
    } on PlatformException {
      if (mounted) setState(() => _status = 'Control service unavailable.');
    } on MissingPluginException {
      if (mounted) setState(() => _status = 'Control service unavailable.');
    } finally {
      if (mounted) setState(() => _writing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final control = _controls[_selected]!;
    final readback = widget.readings['${_selected}_state'];
    final writable = widget.readings['${_selected}_writable'] == true;
    final value = int.tryParse(_target.text);
    final available = !widget.busy && !widget.demo && !_writing;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      OutlinedButton.icon(
        onPressed: available ? widget.refresh : null,
        icon: const Icon(Icons.manage_search),
        label: const Text('Read climate and window state'),
      ),
      DropdownButton<String>(
        value: _selected,
        onChanged: available ? (id) => setState(() {
          _selected = id!;
          _target.clear();
          _status = null;
        }) : null,
        items: _controls.entries.map((entry) =>
            DropdownMenuItem(value: entry.key, child: Text(entry.value.$1))).toList(),
      ),
      Text('${writable ? 'Vehicle readback' : 'Raw state candidate'}: ${readback ?? '—'}'),
      if (!writable)
        const Text('Write access needs a reviewed firmware profile and a valid readback.'),
      TextField(
        controller: _target,
        enabled: available && writable,
        keyboardType: TextInputType.number,
        inputFormatters: [LengthLimitingTextInputFormatter(4)],
        decoration: InputDecoration(labelText: 'Target value', helperText: control.$4),
        onChanged: (_) => setState(() => _status = null),
      ),
      FilledButton(
        onPressed: available && writable && value != null &&
            value >= control.$2 && value <= control.$3 ? () => _apply(value) : null,
        child: const Text('Review and apply'),
      ),
      if (widget.busy || _writing) const LinearProgressIndicator(),
      if (_status != null) Text(_status!),
    ]);
  }
}
