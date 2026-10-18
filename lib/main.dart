import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'core/vehicle_gateway.dart';
import 'core/vehicle_snapshot.dart';

const Color _background = Color(0xFF151C24);
const Color _surface = Color(0xFF1C2530);
const Color _tile = Color(0xFF26313F);
const Color _secondary = Color(0xFF9DA9B9);
const Color _accent = Color(0xFF8FC1FF);

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setPreferredOrientations(const [
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);
  runApp(const LauncherApp());
}

class LauncherApp extends StatelessWidget {
  const LauncherApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'BYD Smart Launcher',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: _background,
        colorScheme: const ColorScheme.dark(primary: _accent),
      ),
      home: const LauncherHome(),
    );
  }
}

enum LauncherPage { status, blindSpot, range, automations, telegram, controls, settings }

extension LauncherPageInfo on LauncherPage {
  String get label => switch (this) {
    LauncherPage.status => 'Status',
    LauncherPage.blindSpot => 'Blind Spot',
    LauncherPage.range => 'Real Range',
    LauncherPage.automations => 'Automations',
    LauncherPage.telegram => 'Telegram',
    LauncherPage.controls => 'Controls',
    LauncherPage.settings => 'Settings',
  };

  IconData get icon => switch (this) {
    LauncherPage.status => Icons.speed_outlined,
    LauncherPage.blindSpot => Icons.videocam_outlined,
    LauncherPage.range => Icons.route_outlined,
    LauncherPage.automations => Icons.auto_mode_outlined,
    LauncherPage.telegram => Icons.send_outlined,
    LauncherPage.controls => Icons.directions_car_outlined,
    LauncherPage.settings => Icons.settings_outlined,
  };
}

class LauncherHome extends StatefulWidget {
  const LauncherHome({super.key});

  @override
  State<LauncherHome> createState() => _LauncherHomeState();
}

class _LauncherHomeState extends State<LauncherHome> {
  VehicleGateway _gateway = const PlatformVehicleGateway();
  VehicleSnapshot _snapshot = VehicleSnapshot.unavailable();
  StreamSubscription<VehicleSnapshot>? _subscription;
  LauncherPage _page = LauncherPage.status;
  bool _demo = false;
  String _diagnostics = 'Vehicle diagnostics not requested';
  bool _adbBusy = false;
  final _tgToken = TextEditingController();
  final _tgChat = TextEditingController();
  bool _tgBusy = false;
  bool _tgConfigured = false;
  String _tgMessage = 'No bot credentials are uploaded to GitHub.';

  @override
  void initState() {
    super.initState();
    _connect();
    _loadTelegramStatus();
  }

  Future<void> _connect() async {
    await _subscription?.cancel();
    final active = _gateway;
    _subscription = active.snapshots.listen(
      (snapshot) {
        if (mounted && identical(_gateway, active)) {
          setState(() => _snapshot = snapshot);
        }
      },
      onError: (Object _) {
        if (mounted && identical(_gateway, active)) {
          setState(() => _snapshot = VehicleSnapshot.unavailable());
        }
      },
    );
    final snapshot = await active.getSnapshot();
    if (mounted && identical(_gateway, active)) {
      setState(() => _snapshot = snapshot);
    }
  }

  Future<void> _toggleDemo(bool enabled) async {
    setState(() {
      _demo = enabled;
      _gateway.dispose();
      _gateway = enabled
          ? const DemoVehicleGateway()
          : const PlatformVehicleGateway();
      _snapshot = VehicleSnapshot.unavailable();
    });
    await _connect();
  }

  Future<void> _readDiagnostics() async {
    final values = await _gateway.getDiagnostics();
    if (mounted) {
      setState(() => _diagnostics =
          values.entries.map((e) => '${e.key}: ${e.value}').join(' • '));
    }
  }

  Future<void> _adbAction(String action) async {
    if (_adbBusy || _demo) return;
    setState(() {
      _adbBusy = true;
      _diagnostics = action == 'connect'
          ? 'Connecting to 127.0.0.1:5555. Accept the DiLink ADB prompt if shown…'
          : 'Running $action…';
    });
    try {
      final response = switch (action) {
        'connect' => await _gateway.connectAdb(),
        'disconnect' => await _gateway.disconnectAdb(),
        'soc' => await _gateway.probeSoc(),
        _ => await _gateway.probeAdb(),
      };
      final snapshot = await _gateway.getSnapshot();
      if (mounted) {
        setState(() {
          _snapshot = snapshot;
          _diagnostics =
              response.entries.map((e) => '${e.key}: ${e.value}').join(' • ');
        });
      }
    } catch (error) {
      if (mounted) setState(() => _diagnostics = 'ADB operation failed: $error');
    } finally {
      if (mounted) setState(() => _adbBusy = false);
    }
  }

  Future<void> _loadTelegramStatus() async {
    final status = await _gateway.telegramStatus();
    if (mounted) setState(() => _tgConfigured = status['configured'] == true);
  }

  Future<void> _telegramAction(String action) async {
    if (_tgBusy || _demo) return;
    setState(() {
      _tgBusy = true;
      _tgMessage = 'Processing Telegram request…';
    });
    try {
      final result = switch (action) {
        'save' => await _gateway.telegramSave(_tgToken.text, _tgChat.text),
        'clear' => await _gateway.telegramClear(),
        _ => await _gateway.telegramSendTest(),
      };
      final status = await _gateway.telegramStatus();
      if (mounted) {
        setState(() {
          _tgConfigured = status['configured'] == true;
          _tgMessage = result['ok'] == true
              ? (action == 'save'
                  ? 'Bot credentials encrypted on this device.'
                  : action == 'clear'
                      ? 'Bot configuration cleared.'
                      : 'Test message sent.')
              : 'Telegram: ${result['error'] ?? 'operation_failed'}';
        });
        if (action == 'save' && result['ok'] == true) {
          _tgToken.clear();
          _tgChat.clear();
        }
      }
    } catch (_) {
      if (mounted) {
        setState(() => _tgMessage = 'Telegram operation could not complete.');
      }
    } finally {
      if (mounted) setState(() => _tgBusy = false);
    }
  }

  @override
  void dispose() {
    _tgToken.dispose();
    _tgChat.dispose();
    _subscription?.cancel();
    _gateway.dispose();
    super.dispose();
  }

  String _value(num? value, String unit, {int decimals = 0}) =>
      value == null ? '—' : '${value.toStringAsFixed(decimals)}$unit';

  Widget _metric(String name, String reading, IconData icon) => Expanded(
        child: Container(
          margin: const EdgeInsets.only(right: 9),
          padding: const EdgeInsets.all(15),
          decoration: BoxDecoration(
            color: _tile,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, color: _accent),
              const SizedBox(height: 8),
              Text(reading,
                  style: const TextStyle(
                      fontWeight: FontWeight.bold, fontSize: 22)),
              const SizedBox(height: 3),
              Text(name, style: const TextStyle(color: _secondary)),
            ],
          ),
        ),
      );

  Widget _section(String heading, String description, List<Widget> children) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(heading,
            style: const TextStyle(
                fontSize: 25, fontWeight: FontWeight.w600)),
        const SizedBox(height: 4),
        Text(description,
            style: const TextStyle(color: _secondary, fontSize: 12)),
        const SizedBox(height: 15),
        Expanded(
          child: ListView(
            children: children
                .map((child) => Card(
                      color: _surface,
                      margin: const EdgeInsets.only(bottom: 12),
                      child: Padding(
                        padding: const EdgeInsets.all(18),
                        child: child,
                      ),
                    ))
                .toList(),
          ),
        ),
      ],
    );
  }

  Widget _info(String title, String subtitle, IconData icon) {
    return ListTile(
      leading: Icon(icon, color: _accent, size: 34),
      title: Text(title),
      subtitle: Text(subtitle),
    );
  }

  Widget _content() {
    switch (_page) {
      case LauncherPage.status:
        return _section('Vehicle status',
            'Live data is unavailable until the native transport is validated', [
          if (!_snapshot.hasLiveVehicleData)
            _info(_demo ? 'DEMO VALUES' : 'Vehicle not connected',
                _demo
                    ? 'Synthetic readings only; no commands sent to the car'
                    : 'No vehicle values are guessed or fabricated',
                Icons.info_outline),
          Row(children: [
            _metric('Battery', _value(_snapshot.socPercent, '%'),
                Icons.battery_charging_full),
            _metric('Speed', _value(_snapshot.speedKmh, ' km/h'),
                Icons.speed),
            _metric('Real range',
                _value(_snapshot.rangeEstimateKm, ' km'), Icons.route),
          ]),
          _info('Camera status: inactive',
              'Factory reverse and 360 cameras retain precedence',
              Icons.directions_car_outlined),
        ]);
      case LauncherPage.blindSpot:
        return _section('Blind spot',
            'Native BYDMate-style camera integration is pending', [
          _info('Camera disabled by default',
              'Indicator selects left/right; factory reverse or 360 takes priority',
              Icons.videocam_off_outlined),
          const Text('Planned: firmware-aware FIDs, 150 ms signal response, '
              'persistent window positioning, and no interference with OEM cameras.'),
        ]);
      case LauncherPage.range:
        return _section('Real range & consumption',
            'An estimation engine exists; live BMS inputs are not connected', [
          Row(children: [
            _metric('Remaining', _value(_snapshot.rangeEstimateKm, ' km'),
                Icons.route),
            _metric(
                'Consumption',
                _value(_snapshot.avgKwhPer100km, ' kWh/100', decimals: 1),
                Icons.bolt),
            _metric('SOC', _value(_snapshot.socPercent, '%'),
                Icons.battery_full_outlined),
          ]),
          const Text('Formula: remaining usable energy divided by '
              'validated recent kWh/100 km. Unknown or invalid readings stay unavailable.'),
        ]);
      case LauncherPage.automations:
        return _section('Automations', 'Local WHEN → IF → THEN engine pending', [
          _info('Rules are currently disabled',
              'No commands are executed from this prototype',
              Icons.auto_mode_outlined),
          const Text('Example: WHEN SOC falls below 25% AND the car is on, '
              'THEN queue a Telegram warning once per trip.'),
        ]);
      case LauncherPage.telegram:
        return _section('Telegram bot',
            'Encrypted local bot settings • power-state notifications are not yet enabled', [
          _info(
            _tgConfigured ? 'Bot credentials saved' : 'Bot not configured',
            'Manual test messages only. No car events are transmitted in this build.',
            _tgConfigured ? Icons.lock_outline : Icons.send_outlined,
          ),
          TextField(
            controller: _tgToken,
            obscureText: true,
            enableSuggestions: false,
            autocorrect: false,
            decoration: const InputDecoration(
              labelText: 'Telegram Bot Token',
              hintText: 'Token from BotFather (saved encrypted on this device)',
              prefixIcon: Icon(Icons.vpn_key_outlined),
            ),
          ),
          TextField(
            controller: _tgChat,
            decoration: const InputDecoration(
              labelText: 'Telegram Chat ID',
              hintText: 'Private user / group chat ID',
              prefixIcon: Icon(Icons.chat_bubble_outline),
            ),
          ),
          Wrap(
            spacing: 12,
            runSpacing: 8,
            children: [
              FilledButton.icon(
                onPressed: _tgBusy || _demo ? null : () => _telegramAction('save'),
                icon: const Icon(Icons.save_outlined),
                label: const Text('Save bot settings'),
              ),
              OutlinedButton.icon(
                onPressed: _tgBusy || _demo || !_tgConfigured
                    ? null : () => _telegramAction('test'),
                icon: const Icon(Icons.send_outlined),
                label: const Text('Send test message'),
              ),
              OutlinedButton.icon(
                onPressed: _tgBusy || _demo || !_tgConfigured
                    ? null : () => _telegramAction('clear'),
                icon: const Icon(Icons.delete_outline),
                label: const Text('Clear credentials'),
              ),
            ],
          ),
          Text(_tgMessage),
          if (_tgBusy) const LinearProgressIndicator(),
          const Text('Car ON/OFF notifications need verified BYD power-state events, '
              'a durable queue, and an on-car acceptance test. They remain disabled.'),
        ]);
      case LauncherPage.controls:
        return _section('Vehicle controls', 'Safety gated; no write API yet', [
          _info('Commands unavailable',
              'Car firmware must be verified. Only allowlisted comfort commands '
              'with safe conditions and confirmation may be enabled.',
              Icons.shield_outlined),
          const Text('Planned: climate, windows and supported comfort options. '
              'Driving and safety-critical systems remain out of scope.'),
        ]);
      case LauncherPage.settings:
        return _section('Settings & diagnostics',
            'ADB handshake only; helper and vehicle telemetry are not yet installed', [
          SwitchListTile(
            value: _demo,
            onChanged: _toggleDemo,
            title: const Text('Explicit demo mode'),
            subtitle: const Text('Synthetic data; never controls the vehicle'),
          ),
          _info('ADB: ${_snapshot.adbState}',
              'Helper: ${_snapshot.helperState}', Icons.usb_outlined),
          const Text('Only use ADB setup while the car is safely parked. '
              'Enable the head-unit Wireless ADB setting first. '
              'This prototype will request ADB authorization, but cannot yet access '
              'BYD vehicle controls or camera feeds.'),
          const SizedBox(height: 8),
          Text(_diagnostics),
          if (_adbBusy) const LinearProgressIndicator(),
          const SizedBox(height: 8),
          Wrap(
            spacing: 12,
            runSpacing: 8,
            children: [
              FilledButton.icon(
                onPressed: (_adbBusy || _demo) ? null : () => _adbAction('connect'),
                icon: const Icon(Icons.usb),
                label: const Text('Connect ADB'),
              ),
              OutlinedButton.icon(
                onPressed: (_adbBusy || _demo) ? null : () => _adbAction('probe'),
                icon: const Icon(Icons.info_outline),
                label: const Text('Probe Android version'),
              ),
              OutlinedButton.icon(
                onPressed: (_adbBusy || _demo) ? null : () => _adbAction('soc'),
                icon: const Icon(Icons.battery_full),
                label: const Text('Probe SOC (unverified)'),
              ),
              OutlinedButton.icon(
                onPressed: (_adbBusy || _demo) ? null : () => _adbAction('disconnect'),
                icon: const Icon(Icons.link_off),
                label: const Text('Disconnect ADB'),
              ),
              OutlinedButton.icon(
                onPressed: _adbBusy ? null : _readDiagnostics,
                icon: const Icon(Icons.refresh),
                label: const Text('Refresh diagnostics'),
              ),
            ],
          ),
        ]);
    }
  }

  Widget _navigation() => Row(
        children: LauncherPage.values.map((page) {
          final active = page == _page;
          return Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 3),
              child: Material(
                color: active ? _tile : Colors.transparent,
                shape: RoundedRectangleBorder(
                  side: BorderSide(
                    color: active ? _accent : Colors.transparent,
                    width: 1.5,
                  ),
                  borderRadius: BorderRadius.circular(15),
                ),
                child: InkWell(
                  onTap: () => setState(() => _page = page),
                  borderRadius: BorderRadius.circular(15),
                  child: SizedBox(
                    height: 65,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(page.icon, color: active ? Colors.white : _secondary),
                        const SizedBox(height: 4),
                        Text(page.label,
                            style: TextStyle(
                              fontSize: 10,
                              color: active ? Colors.white : _secondary,
                            )),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      );

  Widget _mediaRail() => Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: _surface,
          borderRadius: BorderRadius.circular(22),
        ),
        child: const Column(
          children: [
            Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              Icon(Icons.play_circle_outline),
              SizedBox(width: 10),
              Text('Now playing',
                  style: TextStyle(fontSize: 19, fontWeight: FontWeight.w500)),
            ]),
            Expanded(
                child: Center(
                    child: Icon(Icons.music_note_outlined,
                        size: 90, color: _secondary))),
            Text('No media session',
                style: TextStyle(fontSize: 19, fontWeight: FontWeight.w600)),
            SizedBox(height: 10),
            Text('Media player integration is planned',
                style: TextStyle(color: _secondary, fontSize: 11)),
            SizedBox(height: 25),
            Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
              Icon(Icons.skip_previous_outlined, color: _secondary),
              Icon(Icons.play_arrow_outlined, color: _secondary),
              Icon(Icons.skip_next_outlined, color: _secondary),
            ]),
            SizedBox(height: 32),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            children: [
              Row(children: [
                const Icon(Icons.auto_awesome, color: _accent),
                const SizedBox(width: 12),
                const Text('BYD Smart Launcher',
                    style:
                        TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                const Spacer(),
                Text(
                  _demo
                      ? 'DEMO — no car access'
                      : _snapshot.hasLiveVehicleData
                          ? 'Live vehicle connected'
                          : 'Vehicle disconnected',
                  style: const TextStyle(color: _secondary),
                ),
                const SizedBox(width: 20),
                const Icon(Icons.settings_input_antenna,
                    color: _secondary),
              ]),
              const SizedBox(height: 18),
              Expanded(child: LayoutBuilder(builder: (context, size) {
                return Row(children: [
                  SizedBox(
                      width: size.maxWidth * .29, child: _mediaRail()),
                  const SizedBox(width: 23),
                  Expanded(
                      child: Column(children: [
                    Expanded(child: _content()),
                    const SizedBox(height: 10),
                    _navigation(),
                  ])),
                ]);
              })),
            ],
          ),
        ),
      ),
    );
  }
}
