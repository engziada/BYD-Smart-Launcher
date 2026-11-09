import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'core/adb_observation_scope.dart';
import 'core/adb_stream_recovery.dart';
import 'core/planning_range.dart';
import 'core/vehicle_gateway.dart';
import 'core/vehicle_snapshot.dart';
import 'widgets/charging_flow_scene.dart';
import 'widgets/measured_trip_range.dart';
import 'widgets/comfort_controls.dart';

final ValueNotifier<bool> _lightMode = ValueNotifier<bool>(true);
const MethodChannel _voice = MethodChannel('com.ziada.byd_smart_launcher/voice');
const MethodChannel _vehicleMethods = MethodChannel('com.ziada.byd_smart_launcher/vehicle');

Color get _background => _lightMode.value ? const Color(0xFFF4F7FA) : const Color(0xFF151C24);
Color get _surface => _lightMode.value ? Colors.white : const Color(0xFF1C2530);
Color get _tile => _lightMode.value ? const Color(0xFFE5EDF5) : const Color(0xFF26313F);
Color get _secondary => _lightMode.value ? const Color(0xFF526173) : const Color(0xFF9DA9B9);
Color get _accent => _lightMode.value ? const Color(0xFF175CA8) : const Color(0xFF8FC1FF);
Color get _navigationActive => _lightMode.value ? const Color(0xFF143D68) : Colors.white;

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
    return ValueListenableBuilder<bool>(
      valueListenable: _lightMode,
      builder: (context, light, _) => MaterialApp(
        title: 'BYD Smart Launcher',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          useMaterial3: true,
          brightness: light ? Brightness.light : Brightness.dark,
          scaffoldBackgroundColor: _background,
          colorScheme: ColorScheme.fromSeed(
            seedColor: _accent,
            brightness: light ? Brightness.light : Brightness.dark,
          ),
        ),
        home: const LauncherHome(),
      ),
    );
  }
}

enum LauncherPage { status, apps, blindSpot, range, automations, telegram, controls, settings }

extension LauncherPageInfo on LauncherPage {
  String get label => switch (this) {
    LauncherPage.status => 'Status',
    LauncherPage.apps => 'Apps',
    LauncherPage.blindSpot => 'Blind Spot',
    LauncherPage.range => 'Range Planner',
    LauncherPage.automations => 'Automations',
    LauncherPage.telegram => 'Telegram',
    LauncherPage.controls => 'Controls',
    LauncherPage.settings => 'Settings',
  };

  IconData get icon => switch (this) {
    LauncherPage.status => Icons.speed_outlined,
    LauncherPage.apps => Icons.apps_outlined,
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

class _LauncherHomeState extends State<LauncherHome> with WidgetsBindingObserver {
  VehicleGateway _gateway = const PlatformVehicleGateway();
  VehicleSnapshot _snapshot = VehicleSnapshot.unavailable();
  StreamSubscription<VehicleSnapshot>? _subscription;
  LauncherPage _page = LauncherPage.status;
  bool _demo = false;
  String _diagnostics = 'Vehicle diagnostics not requested';
  Map<Object?, Object?> _chargingCandidate = {};
  Map<Object?, Object?> _motionCandidate = {};
  Map<Object?, Object?> _ownerSensors = {};
  Map<Object?, Object?> _comfortMappings = {};
  bool _adbBusy = false;
  final _tgToken = TextEditingController();
  final _tgChat = TextEditingController();
  bool _tgBusy = false;
  bool _tgConfigured = false;
  bool _tgAutoRetry = false;
  int _tgPending = 0;
  String _tgMessage = 'No bot credentials are uploaded to GitHub.';
  final _batteryCapacity = TextEditingController();
  final _planningSoc = TextEditingController();
  final _planningConsumption = TextEditingController();
  double? _planRangeKm;
  String _planRangeOrigin = 'Enter values to calculate a planning estimate.';
  bool _rangeBusy = false;
  Map<Object?, Object?> _rangeCandidate = {};
  StreamSubscription<Map<Object?, Object?>>? _nativeStreamSubscription;
  bool _chargeDesignPreview = false;
  bool _streamSessionArmed = false;
  bool _streamRecoveryUsed = false;
  bool _operatorDisconnected = false;
  bool _authorizationNeedsRetry = false;
  bool _appInForeground = true;
  int _dashboardSession = 0;
  Timer? _dashboardExpiry;
  DateTime? _lastIntegrationRefresh;
  bool _dashboardEnabled = false;
  bool _dashboardStarting = false;
  DateTime? _dashboardSampledAt;
  String _dashboardNotice = 'Connecting to the vehicle…';
  String _rangeMessage = 'Press Sample while safely parked. These values are UNVERIFIED.';
  Map<Object?, Object?> _betaStatus = {};
  Map<Object?, Object?> _cameraStatus = {};
  List<Map<Object?, Object?>> _installedApps = [];
  bool _cameraBusy = false;
  bool _rulesBusy = false;
  bool _rulesMasterEnabled = false;
  List<Map<Object?, Object?>> _storedRules = [];
  String _newRuleKind = 'SOC_BELOW';
  final _socRuleThreshold = TextEditingController(text: '25');
  String _rulesMessage = 'Enable the rules you want and configure Telegram delivery.';

  @override
  void initState() {
    super.initState();
    _lightMode.addListener(_refreshThemeColors);
    WidgetsBinding.instance.addObserver(this);
    _connect();
    _loadTelegramStatus();
    _loadCapacityStatus();
    _loadPlanningConsumption();
    _loadAutomationStatus();
    unawaited(_readBetaStatus());
    unawaited(_loadTheme());
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_startDashboard());
    });
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
    _stopDashboard(reason: 'Dashboard updates stopped on mode change.');
    await _voiceAction('demo', enabled);
    if (!mounted) return;
    setState(() {
      _demo = enabled;
      _gateway.dispose();
      _gateway = enabled
          ? const DemoVehicleGateway()
          : const PlatformVehicleGateway();
      _snapshot = VehicleSnapshot.unavailable();
      _chargingCandidate = {};
      _motionCandidate = {};
      _ownerSensors = {};
      _comfortMappings = {};
      _rangeCandidate = {};
      _dashboardSampledAt = null;
    });
    await _connect();
    await _loadAutomationStatus();
    if (!enabled && mounted && !_operatorDisconnected) unawaited(_startDashboard());
  }

  Future<void> _voiceAction(String method, [Object? argument]) async {
    try {
      await _voice.invokeMethod<void>(method, argument);
    } on MissingPluginException {
      if (mounted && method != 'demo') _showVoiceUnavailable();
    } on PlatformException catch (error) {
      if (!mounted || method == 'demo') return;
      if (error.code == 'settings_unavailable') {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('This head unit does not expose Android Accessibility Settings.'),
        ));
      } else {
        _showVoiceUnavailable();
      }
    }
  }

  void _showVoiceUnavailable() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Voice assistant is unavailable on this device.')),
    );
  }

  Future<void> _typeVoiceCommand() async {
    String command = '';
    final result = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Ask the assistant'),
        content: TextField(
          autofocus: true,
          maxLength: 120,
          decoration: const InputDecoration(hintText: 'Battery level / نسبة البطارية'),
          onChanged: (value) => command = value,
          onSubmitted: (value) => Navigator.pop(context, value),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, command), child: const Text('Ask')),
        ],
      ),
    );
    if (mounted && !_demo && result != null && result.trim().isNotEmpty) {
      await _voiceAction('text', result.trim());
    }
  }


  /// Only an explicit operator action can start firmware-derived GET sampling.
  /// Android owns the five-second clock and stops on app background or missing
  /// native listener. This Flutter view never authorizes any vehicle writes.
  void _stopDashboard({
    String reason = 'Native stream stopped. Readings cleared.',
    bool notifyNative = true,
  }) {
    _dashboardSession++;
    _dashboardExpiry?.cancel();
    _streamSessionArmed = false;
    _nativeStreamSubscription?.cancel();
    _nativeStreamSubscription = null;
    if (notifyNative) {
      unawaited(_gateway.stopNativeCandidateStream());
    }
    if (mounted) {
      setState(() {
        _dashboardEnabled = false;
        _dashboardStarting = false;
        _rangeCandidate = {};
        _betaStatus = {};
        _dashboardSampledAt = null;
        _dashboardNotice = reason;
      });
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Keep the connection attempt alive while Android's ADB approval dialog has focus.
    if (state == AppLifecycleState.inactive) return;
    _appInForeground = state == AppLifecycleState.resumed;
    if (_appInForeground) {
      unawaited(_readBetaStatus());
      if (!_operatorDisconnected) unawaited(_startDashboard());
    } else if (_dashboardEnabled) {
      _stopDashboard(reason: 'Readings paused while the launcher is in the background.');
    }
  }

  void _handleNativeCandidate(int session, Map<Object?, Object?> event) {
    if (!mounted || _demo || session != _dashboardSession ||
        !_dashboardEnabled) {
      return;
    }
    final status = event['streamState'];
    if (status == 'initializing' || status == 'idle') {
      if (status == 'initializing') _streamSessionArmed = true;
      setState(() => _dashboardNotice = 'Connecting to vehicle data…');
      return;
    }
    if (status == 'candidate') {
      _streamSessionArmed = true;
      final soc = event['socCandidatePercent'];
      final mileage = event['odometerCandidateKm'];
      final sampleEpoch = event['capturedAtEpochMs'];
      if (soc is! num || mileage is! num || sampleEpoch is! num ||
          !soc.toDouble().isFinite || soc < 0 || soc > 100 ||
          !mileage.toDouble().isFinite || mileage < 0 ||
          event['firmwareVerified'] != false ||
          event['vehicleWriteAuthorized'] != false) {
        _stopDashboard(reason: 'Invalid candidate event; stream disabled.');
        return;
      }
      final readAt = DateTime.fromMillisecondsSinceEpoch(sampleEpoch.toInt());
      if (DateTime.now().difference(readAt).inSeconds.abs() > 15) {
        _stopDashboard(reason: 'Stale event; stream disabled.');
        return;
      }
      setState(() {
        _rangeCandidate = event;
        _dashboardSampledAt = readAt;
        _dashboardNotice = 'Live readings';
      });
      _dashboardExpiry?.cancel();
      _dashboardExpiry = Timer(const Duration(seconds: 3), () {
        if (mounted && session == _dashboardSession) {
          setState(() {
            _rangeCandidate = {};
            _betaStatus = {};
            _dashboardSampledAt = null;
            _dashboardNotice = 'Waiting for fresh readings…';
          });
        }
      });
      final now = DateTime.now();
      if (_lastIntegrationRefresh == null ||
          now.difference(_lastIntegrationRefresh!) >= const Duration(seconds: 2)) {
        _lastIntegrationRefresh = now;
        unawaited(_readBetaStatus());
      }
      return;
    }
    if (status == 'stale') {
      _dashboardExpiry?.cancel();
      setState(() {
        _rangeCandidate = {};
        _dashboardSampledAt = null;
        _betaStatus = {};
        _dashboardNotice = 'Waiting for fresh readings…';
      });
      return;
    }
    if (status == 'stopped') {
      // EventChannel broadcasts its previous stopped status to every new
      // listener. Ignore that OLD session until this Start initializes.
      if (ignoreUnarmedNativeStop(
          seenCurrentSessionInitialization: _streamSessionArmed)) {
        return;
      }
      final reason = event['reason'] as String?;
      if (shouldAttemptAdbStreamRecovery(
        reason: reason,
        inForeground: _appInForeground,
        operatorDisconnected: _operatorDisconnected,
        recoveryAlreadyTried: _streamRecoveryUsed,
      )) {
        _retryUnexpectedAdbLoss();
        return;
      }
      _stopDashboard(
          reason: 'Vehicle readings unavailable. Connection details are in Settings → Advanced.',
          notifyNative: false);
    }
  }

  /// One bounded recovery from an UNEXPECTED loss only. The old values are
  /// cleared immediately and the native FID/identity gate must run again.
  /// A second loss leaves the stream stopped for explicit user action.
  void _retryUnexpectedAdbLoss() {
    _streamRecoveryUsed = true;
    _stopDashboard(
      reason: 'Reconnecting to the vehicle…',
      notifyNative: false,
    );
    final stoppedSession = _dashboardSession;
    Future<void>.delayed(const Duration(seconds: 2), () {
      if (!mounted || !_appInForeground || _operatorDisconnected || _demo ||
          stoppedSession != _dashboardSession) {
        return;
      }
      unawaited(_startDashboard(recovery: true));
    });
  }

  Future<void> _startDashboard({bool recovery = false, bool manual = false}) async {
    if (!manual && (_authorizationNeedsRetry || _operatorDisconnected)) return;
    if (!_appInForeground || _demo || _dashboardEnabled || _dashboardStarting) {
      return;
    }
    if (!recovery) {
      _streamRecoveryUsed = false;
      _operatorDisconnected = false;
    }
    _authorizationNeedsRetry = false;
    _streamSessionArmed = false;
    final session = ++_dashboardSession;
    setState(() {
      _dashboardStarting = true;
      _rangeCandidate = {};
      _dashboardSampledAt = null;
      _lastIntegrationRefresh = null;
      _dashboardNotice = 'Connecting to the vehicle…';
    });

    var refreshControls = false;
    try {
      final connection = await _gateway.connectAdb();
      if (!mounted || !_appInForeground || _demo || session != _dashboardSession) {
        return;
      }
      if (connection['adb'] != 'connected') {
        _authorizationNeedsRetry = connection['adbFailure'] == 'authorization_rejected_or_timeout';
        setState(() {
          _dashboardNotice = connection['adbFailure'] == 'authorization_rejected_or_timeout'
              ? 'Vehicle connection needs approval on the head unit.'
              : 'Vehicle connection unavailable.';
          _diagnostics = 'ADB: ${connection['adbFailure'] ?? connection['error'] ?? connection['adb'] ?? 'unknown'}';
        });
        return;
      }
      final snapshot = await _gateway.getSnapshot();
      if (!mounted || !_appInForeground || _demo || session != _dashboardSession) return;
      setState(() => _snapshot = snapshot);
      if (connection['sessionReused'] != true) {
        setState(() {
          _motionCandidate = {};
          _chargingCandidate = {};
          _comfortMappings = {};
        });
      }
      refreshControls = true;

      _nativeStreamSubscription = _gateway.candidateStream.listen(
        (event) => _handleNativeCandidate(session, event),
        onError: (Object _) {
          if (mounted && session == _dashboardSession) {
            _stopDashboard(reason: 'Native stream transport unavailable.');
          }
        },
        onDone: () {
          if (mounted && session == _dashboardSession) {
            _stopDashboard(reason: 'Native event stream ended.');
          }
        },
      );
      if (!mounted || session != _dashboardSession) {
        return;
      }
      setState(() {
        _dashboardEnabled = true;
        _dashboardNotice = 'Connecting to vehicle data…';
      });

      // EventChannel "listen" messages are asynchronous. A bounded retry
      // handles the case where the native listener attaches just after start.
      var start = await _gateway.startNativeCandidateStream();
      if (start['reason'] == 'no_active_stream_listener') {
        await Future<void>.delayed(const Duration(milliseconds: 300));
        if (!mounted || session != _dashboardSession) {
          return;
        }
        start = await _gateway.startNativeCandidateStream();
      }
      if (!mounted || session != _dashboardSession) {
        return;
      }
      if (start['ok'] != true) {
        setState(() => _diagnostics = 'Vehicle stream: ${start['reason'] ?? start['error'] ?? 'unknown'}');
        _stopDashboard(reason: 'Vehicle readings unavailable. Connection details are in Settings → Advanced.');
      }
    } catch (_) {
      if (mounted && session == _dashboardSession) {
        _stopDashboard(reason: 'Unable to start the native read-only stream.');
      }
    } finally {
      if (mounted && session == _dashboardSession) {
        setState(() => _dashboardStarting = false);
        if (refreshControls) unawaited(_adbAction('comfortMappings'));
      }
    }
  }

  Future<void> _readBetaStatus() async {
    if (_demo) return;
    try {
      final status = await _vehicleMethods.invokeMapMethod<Object?, Object?>('betaStatus') ?? {};
      if (!mounted || _demo) return;
      setState(() {
        _betaStatus = status;
        final camera = status['camera'];
        _cameraStatus = camera is Map ? camera.cast<Object?, Object?>() : {};
      });
    } on PlatformException {
      if (mounted) setState(() => _betaStatus = {});
    } on MissingPluginException {
      if (mounted) setState(() => _betaStatus = {});
    }
  }

  Future<void> _setSideCamera(bool enabled) async {
    if (_cameraBusy || _demo) return;
    setState(() => _cameraBusy = true);
    try {
      final camera = await _vehicleMethods.invokeMapMethod<Object?, Object?>(
        'sideCameraSet', {'enabled': enabled},
      );
      if (mounted && camera != null) setState(() => _cameraStatus = camera);
    } on PlatformException {
      if (mounted) setState(() => _cameraStatus = {'state': 'camera_unavailable'});
    } on MissingPluginException {
      if (mounted) setState(() => _cameraStatus = {'state': 'camera_unavailable'});
    } finally {
      if (mounted) setState(() => _cameraBusy = false);
    }
  }

  Future<void> _loadInstalledApps() async {
    try {
      final apps = await _vehicleMethods.invokeListMethod<Object?>('installedApps');
      if (mounted) {
        setState(() => _installedApps =
            (apps ?? []).whereType<Map>().map((app) => app.cast<Object?, Object?>()).toList());
      }
    } on PlatformException {
      if (mounted) setState(() => _installedApps = []);
    } on MissingPluginException {
      if (mounted) setState(() => _installedApps = []);
    }
  }

  Future<void> _openSystemTarget(String method, [String? package]) async {
    try {
      final opened = await _vehicleMethods.invokeMethod<bool>(method, package);
      if (opened == true) return;
    } on PlatformException {
      // Older DiLink builds may not expose the system chooser.
    } on MissingPluginException {
      // Native launch actions are available on Android.
    }
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('This shortcut is unavailable on the head unit.')),
      );
    }
  }

  String _betaValue(String name, String unit, {int decimals = 0}) {
    final value = _demo ? null : _betaStatus[name];
    return value is num && value.isFinite ? '${value.toStringAsFixed(decimals)} $unit' : '—';
  }

  Future<void> _readDiagnostics() async {
    final values = await _gateway.getDiagnostics();
    if (mounted) {
      setState(() => _diagnostics =
          values.entries.map((e) => '${e.key}: ${e.value}').join(' • '));
    }
  }

  Future<void> _exportDiagnostics() async {
    if (_adbBusy || _demo) return;
    setState(() => _adbBusy = true);
    try {
      final response = await _gateway.exportDiagnostics();
      if (!mounted) return;
      setState(() {
        _diagnostics = response['ok'] == true
            ? 'Sanitized JSON saved in Downloads/BYD-Smart-Launcher. '
              'Review its contents before sharing.'
            : 'Diagnostics export failed: ${response['error'] ?? 'unknown'}';
      });
    } catch (_) {
      if (mounted) {
        setState(() => _diagnostics = 'Diagnostics export unavailable.');
      }
    } finally {
      if (mounted) setState(() => _adbBusy = false);
    }
  }

  Future<void> _adbAction(String action) async {
    if (_adbBusy || _demo || _dashboardStarting) return;
    if (action == 'disconnect') {
      _operatorDisconnected = true;
      setState(() => _betaStatus = {});
      _stopDashboard(reason: 'ADB disconnected by operator; dashboard stopped.');
    }
    if (action == 'connect' && _dashboardEnabled) {
      // A manually requested connection change is not an unexpected loss.
      _streamRecoveryUsed = true;
    }
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
        'firmware' => await _gateway.probeFirmware(),
        'fids' => await _gateway.probeFidCatalog(),
        'charging' => await _gateway.sampleChargingCandidates(),
        'motion' => await _gateway.probeMotionCandidates(),
        'ownerSensors' || 'comfortMappings' =>
          await _vehicleMethods.invokeMapMethod<Object?, Object?>(action) ?? {},
        _ => await _gateway.probeAdb(),
      };
      final snapshot = await _gateway.getSnapshot();
      if (mounted) {
        setState(() {
          _snapshot = snapshot;
          if (action == 'charging') {
            _chargingCandidate = response;
          } else if (action == 'motion') {
            _motionCandidate = response;
          } else if (action == 'ownerSensors') {
            _ownerSensors = response;
          } else if (action == 'comfortMappings') {
            _comfortMappings = response;
          } else if (shouldClearAdbObservations(action, response)) {
            _chargingCandidate = {};
            _motionCandidate = {};
            _ownerSensors = {};
            _comfortMappings = {};
            _rangeCandidate = {};
            _dashboardSampledAt = null;
          }
          _diagnostics = action == 'motion'
              ? response['ok'] == true
                  ? 'Raw speed/gear values sampled. Units and gear enum are '
                    'UNVERIFIED. These cannot authorize vehicle controls.'
                  : 'Motion probe unavailable: '
                    '${response['status'] ?? response['error'] ?? 'unknown'}.'
              : action == 'charging'
              ? response['ok'] == true
                  ? 'Charging candidate sampled. Not verified; '
                    'no charge-complete event or Telegram automation is enabled.'
                  : 'Charging diagnostic unavailable: '
                    '${response['status'] ?? response['error'] ?? 'unknown'}. '
                    'No charging state was confirmed.'
              : response.entries.map((e) => '${e.key}: ${e.value}').join(' • ');
        });
      }
    } catch (error) {
      if (mounted) setState(() => _diagnostics = 'ADB operation failed: $error');
    } finally {
      if (mounted) setState(() => _adbBusy = false);
    }
  }

  void _consumeRules(Map<Object?, Object?> values) {
    final entries = values['rules'];
    setState(() {
      _rulesMasterEnabled = values['masterEnabled'] == true;
      _storedRules = entries is List
          ? entries.whereType<Map>().map((e) => e.cast<Object?, Object?>()).toList()
          : [];
    });
  }

  Future<void> _loadAutomationStatus() async {
    final result = await _gateway.automationStatus();
    if (mounted && result['ok'] == true) _consumeRules(result);
  }

  Future<void> _automationAction(String action, {String? id, bool? enabled}) async {
    if (_rulesBusy || _demo) return;
    double? threshold;
    if (action == 'add' && _newRuleKind == 'SOC_BELOW') {
      threshold = double.tryParse(_socRuleThreshold.text.trim());
      if (threshold == null || !threshold.isFinite || threshold < 1 || threshold > 99) {
        setState(() => _rulesMessage = 'Enter an SOC threshold from 1 to 99 percent.');
        return;
      }
    }
    setState(() {
      _rulesBusy = true;
      _rulesMessage = 'Saving notification rules…';
    });
    try {
      final response = switch (action) {
        'add' => await _gateway.automationAdd(_newRuleKind, threshold),
        'toggle' => await _gateway.automationToggle(id ?? '', enabled ?? false),
        'remove' => await _gateway.automationRemove(id ?? ''),
        'master' => await _gateway.automationMasterSet(enabled ?? false),
        _ => <Object?, Object?>{'ok': false, 'error': 'unsupported_action'},
      };
      if (mounted) {
        if (response['ok'] == true) {
          _consumeRules(response);
          setState(() => _rulesMessage =
              'Rule settings saved. Enable Telegram delivery to send matching alerts.');
        } else {
          setState(() => _rulesMessage =
              'Rule update refused: ${response['error'] ?? 'unknown'}');
        }
      }
    } catch (_) {
      if (mounted) setState(() => _rulesMessage = 'Could not update local rules.');
    } finally {
      if (mounted) setState(() => _rulesBusy = false);
    }
  }

  String _ruleLabel(Map<Object?, Object?> rule) => switch (rule['kind']) {
    'SOC_BELOW' => 'SOC at or below ${rule['thresholdPercent']}%',
    'POWER_ON' => 'Car power ON',
    'POWER_OFF' => 'Car power OFF',
    'CHARGE_STARTED' => 'Charging started',
    'CHARGE_FINISHED' => 'Charging finished',
    _ => 'Unknown trigger',
  };

  Future<void> _loadCapacityStatus() async {
    final info = await _gateway.rangeCapacityStatus();
    final value = info['usableCapacityKwh'];
    if (mounted && value is num) {
      setState(() => _batteryCapacity.text = value.toStringAsFixed(1));
    }
  }

  Future<void> _saveCapacity() async {
    if (_rangeBusy || _demo) return;
    final textValue = _batteryCapacity.text.trim();
    final value = textValue.isEmpty ? null : double.tryParse(textValue);
    if (textValue.isNotEmpty && (value == null || !value.isFinite ||
        value < 5 || value > 200)) {
      setState(() => _rangeMessage =
          'Enter a usable battery capacity between 5 and 200 kWh, or clear it.');
      return;
    }
    setState(() => _rangeBusy = true);
    try {
      final res = await _gateway.setRangeCapacity(value);
      if (mounted) {
        setState(() => _rangeMessage = res['ok'] == true
          ? 'Capacity saved as operator-provided (not vehicle-verified).'
          : 'Capacity could not be saved: ${res['error'] ?? 'unknown'}');
      }
    } finally {
      if (mounted) setState(() => _rangeBusy = false);
    }
  }


  Future<void> _loadPlanningConsumption() async {
    final info = await _gateway.planningConsumptionStatus();
    final avg = info['averageKwhPer100Km'];
    if (mounted && avg is num) {
      setState(() => _planningConsumption.text = avg.toStringAsFixed(1));
    }
  }

  Future<void> _savePlanningConsumption() async {
    if (_rangeBusy || _demo) return;
    final raw = _planningConsumption.text.trim();
    final avg = raw.isEmpty ? null : double.tryParse(raw);
    if (raw.isNotEmpty &&
        (avg == null || !avg.isFinite || avg < 5 || avg > 45)) {
      setState(() => _planRangeOrigin =
          'Enter an average consumption between 5–45 kWh/100 km.');
      return;
    }
    setState(() => _rangeBusy = true);
    try {
      final result = await _gateway.setPlanningConsumption(avg);
      if (!mounted) return;
      setState(() => _planRangeOrigin = result['ok'] == true
          ? 'Your consumption assumption is saved locally, not measured by BYD.'
          : 'Save failed: ${result['error'] ?? 'unknown'}');
    } finally {
      if (mounted) setState(() => _rangeBusy = false);
    }
  }

  void _calculatePlanningRange() {
    final candidate = _rangeCandidate['socCandidatePercent'];
    final freshness = _dashboardSampledAt == null
        ? null
        : DateTime.now().difference(_dashboardSampledAt!);
    final hasFreshVehicleSoc = !_demo &&
        _rangeCandidate['ok'] == true &&
        _rangeCandidate['catalogMatched'] == true &&
        candidate is num &&
        freshness != null &&
        !freshness.isNegative &&
        freshness.inSeconds <= 15;
    final manualSoc = double.tryParse(_planningSoc.text.trim());
    final num? soc = hasFreshVehicleSoc ? candidate : manualSoc;
    final result = calculatePlanningRange(
      socPercent: soc,
      usableCapacityKwh: double.tryParse(_batteryCapacity.text.trim()),
      averageKwhPer100Km: double.tryParse(_planningConsumption.text.trim()),
    );
    setState(() {
      _planRangeKm = result?.rangeKm;
      _planRangeOrigin = result == null
          ? 'Enter a current SOC (0–100%), battery capacity (5–200 kWh) '
            'and average consumption (5–45 kWh/100 km).'
          : 'Planning estimate only • SOC: '
            '${hasFreshVehicleSoc ? 'fresh car read candidate' : 'user entered'} '
            '• Capacity and consumption: user provided • '
            'not measured BMS Real Range.';
    });
  }

  Future<void> _confirmManualStatus() async {
    if (_tgBusy || _demo || !_tgConfigured) return;
    final sample = _rangeCandidate;
    final soc = sample['socCandidatePercent'];
    final odo = sample['odometerCandidateKm'];
    final age = _dashboardSampledAt == null
        ? null : DateTime.now().difference(_dashboardSampledAt!);
    final fresh = _dashboardEnabled &&
        sample['streamState'] == 'candidate' &&
        sample['catalogMatched'] == true &&
        soc is num && odo is num && age != null &&
        !age.isNegative && age.inSeconds <= 15;
    if (!fresh) {
      setState(() => _tgMessage =
          'Start the live read-only stream on Status and wait for a fresh '
          'SOC/odometer sample before sending.');
      return;
    }
    final approved = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Send current vehicle status?'),
        content: Text(
          'Send a private Telegram message to your configured chat with '
          'SOC ${soc.toStringAsFixed(0)}% and odometer '
          '${odo.toStringAsFixed(1)} km?\\n\\n'
          'These are fresh, read-only candidate values, not independently '
          'OEM-certified. Nothing is sent automatically.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Send once'),
          ),
        ],
      ),
    );
    if (approved == true && mounted) {
      await _telegramAction('status');
    }
  }

  /// Explicit one-shot dashboard read without an energy-counter dependency.
  Future<void> _sampleDashboardOnce() async {
    if (_dashboardEnabled) {
      return;
    }
    if (_rangeBusy || _demo || _dashboardStarting) {
      return;
    }
    setState(() => _rangeBusy = true);
    try {
      final result = await _gateway.sampleDashboardCandidates();
      if (!mounted || _demo) {
        return;
      }
      setState(() {
        _rangeCandidate = result['ok'] == true ? result : {};
        _dashboardSampledAt = result['ok'] == true ? DateTime.now() : null;
        _dashboardNotice = result['ok'] == true
            ? 'Manual read-only candidates captured. Updates are OFF.'
            : 'Could not read dashboard candidates: '
              '${result['reason'] ?? result['error'] ?? 'unknown'}.';
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _rangeCandidate = {};
          _dashboardSampledAt = null;
          _dashboardNotice = 'Read-only sample unavailable.';
        });
      }
    } finally {
      if (mounted) {
        setState(() => _rangeBusy = false);
      }
    }
  }

  Future<void> _sampleTelemetry() async {
    if (_dashboardEnabled) {
      return;
    }
    if (_rangeBusy || _demo) return;
    setState(() {
      _rangeBusy = true;
      _rangeMessage = 'Reading SOC, cumulative energy and mileage once…';
    });
    try {
      final values = await _gateway.sampleTelemetry();
      if (mounted) {
        setState(() {
          _rangeCandidate = values['ok'] == true ? values : {};
          _dashboardSampledAt = values['ok'] == true ? DateTime.now() : null;
          _rangeMessage = values['ok'] == true
              ? 'Candidate values received; NOT verified on Song Plus.'
              : 'Unable to read complete telemetry: ${values['reason'] ?? 'unknown'}';
        });
      }
    } finally {
      if (mounted) setState(() => _rangeBusy = false);
    }
  }

  String _candidateValue(String key, String suffix, {int precision = 1}) {
    final value = _rangeCandidate[key];
    if (value is! num) return '—';
    return '${value.toStringAsFixed(precision)} $suffix';
  }

  Future<void> _loadTelegramStatus() async {
    final status = await _gateway.telegramStatus();
    if (mounted) {
      setState(() {
        _tgConfigured = status['configured'] == true;
        _tgAutoRetry = status['automaticRetryEnabled'] == true;
        _tgPending = (status['pendingMessages'] as num?)?.toInt() ?? 0;
      });
    }
  }

  Future<void> _telegramAction(String action, {bool? enabled}) async {
    if (_tgBusy || _demo) return;
    setState(() {
      _tgBusy = true;
      _tgMessage = 'Processing Telegram request…';
    });
    try {
      final result = switch (action) {
        'save' => await _gateway.telegramSave(_tgToken.text, _tgChat.text),
        'clear' => await _gateway.telegramClear(),
        'retry' => await _gateway.telegramRetry(),
        'dropQueue' => await _gateway.telegramClearPending(),
        'status' => await _gateway.telegramSendCurrentStatus(),
        'automaticRetry' => await _vehicleMethods.invokeMapMethod<Object?, Object?>(
            'telegramSetAutomaticRetry', {'enabled': enabled}) ?? {},
        _ => await _gateway.telegramSendTest(),
      };
      final status = await _gateway.telegramStatus();
      if (mounted) {
        setState(() {
          _tgConfigured = status['configured'] == true;
          _tgAutoRetry = status['automaticRetryEnabled'] == true;
          _tgPending = (status['pendingMessages'] as num?)?.toInt() ?? 0;
          _tgMessage = result['ok'] == true
              ? (action == 'save'
                  ? 'Bot credentials encrypted on this device.'
                  : action == 'clear'
                      ? 'Bot configuration cleared.'
                      : action == 'dropQueue'
                          ? 'Pending tests removed.'
                          : action == 'status'
                              ? 'Current car-status message accepted by Telegram.'
                              : action == 'automaticRetry'
                                  ? (_tgAutoRetry ? 'Background retries enabled.' : 'Background retries disabled.')
                                  : result['sent'] == false
                                  ? 'No pending messages to retry.'
                                  : 'Test message confirmed by Telegram.')
              : 'Telegram: ${result['error'] ?? 'operation_failed'}${_tgPending > 0 ? ' (pending: $_tgPending)' : ''}';
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

  Future<void> _loadTheme() async {
    try {
      final value = await _vehicleMethods.invokeMethod<Object?>('getLightTheme');
      if (mounted && value is bool) _lightMode.value = value;
    } on MissingPluginException {
      // Use the default appearance outside Android.
    } on PlatformException {
      // Appearance remains usable if preferences are unavailable.
    }
  }

  Future<void> _setTheme(bool light) async {
    _lightMode.value = light;
    try {
      await _vehicleMethods.invokeMethod<void>('setLightTheme', light);
    } on MissingPluginException {
      // The selection still applies to this session.
    } on PlatformException {
      // The selection still applies to this session.
    }
  }

  void _refreshThemeColors() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _dashboardSession++;
    _dashboardExpiry?.cancel();
    _nativeStreamSubscription?.cancel();
    unawaited(_gateway.stopNativeCandidateStream());
    _lightMode.removeListener(_refreshThemeColors);
    _tgToken.dispose();
    _tgChat.dispose();
    _batteryCapacity.dispose();
    _planningSoc.dispose();
    _planningConsumption.dispose();
    _socRuleThreshold.dispose();
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
              Text(name, style: TextStyle(color: _secondary)),
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
            style: TextStyle(color: _secondary, fontSize: 12)),
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
        final soc = _demo ? _snapshot.socPercent : _rangeCandidate['socCandidatePercent'];
        return _section('Vehicle overview', _demo ? 'Demo readings' : _dashboardNotice, [
          Row(children: [
            _metric('Speed',
                _demo ? _value(_snapshot.speedKmh, ' km/h')
                    : _candidateValue('speedKmh', 'km/h', precision: 0),
                Icons.speed_outlined),
            _metric('Battery',
                _demo ? _value(_snapshot.socPercent, '%')
                    : _candidateValue('socCandidatePercent', '%', precision: 0),
                Icons.battery_full_outlined),
            _metric('Odometer', _candidateValue('odometerCandidateKm', 'km'),
                Icons.route_outlined),
            _metric('Electric range',
                _demo ? _value(_snapshot.rangeEstimateKm, ' km') : _betaValue('rangeKm', 'km'),
                Icons.ev_station_outlined),
          ]),
          ChargingFlowScene(
            previewEnabled: false,
            chargingState: _demo ? null : _betaStatus['chargeState'] as String?,
            socCandidate: soc is num ? soc.toDouble() : null,
          ),
        ]);
      case LauncherPage.apps:
        return _section('Apps', 'Open apps installed on this head unit', [
          if (_installedApps.isEmpty) const Text('No launchable apps found.'),
          Wrap(spacing: 12, runSpacing: 8, children: [
            for (final app in _installedApps)
              OutlinedButton.icon(
                onPressed: () => _openSystemTarget('openInstalledApp', app['package'] as String?),
                icon: const Icon(Icons.launch_outlined),
                label: Text(app['label'] as String? ?? ''),
              ),
          ]),
        ]);
      case LauncherPage.blindSpot:
        return _section('Blind spot cameras',
            'Side preview follows the turn signal while the launcher is in focus', [
          SwitchListTile(
            value: _cameraStatus['enabled'] == true,
            title: const Text('Enable side camera preview'),
            subtitle: const Text('Starts at 30 km/h in Drive. Reverse and loss of focus close the preview.'),
            onChanged: _cameraBusy || _demo || _cameraStatus['supportedFirmware'] != true
                ? null : _setSideCamera,
          ),
          _info('Preview status',
              _cameraStatus['supportedFirmware'] == true
                  ? (_cameraStatus['state'] as String? ?? 'idle').replaceAll('_', ' ')
                  : 'The side-camera profile is unavailable for this firmware.',
              Icons.videocam_outlined),
        ]);
      case LauncherPage.range:
        return _section('EV range',
            'Automatic estimate from vehicle energy and recent electric driving', [
          _info('Estimated electric range: ${_betaValue('rangeKm', 'km')}',
              _betaStatus['active'] != true
                  ? 'Connect ADB to start measuring.'
                  : 'Consumption: ${_betaValue('consumption', 'kWh/100 km', decimals: 1)}. '
                    'Available energy: ${_betaValue('remainingKwh', 'kWh', decimals: 1)} '
                    '(${_betaStatus['bmsEnergy'] == true ? 'vehicle reading' : 'saved capacity × SOC'}). '
                    'An estimate appears after sufficient unplugged, electric-only distance.',
              Icons.route_outlined),
          _info('Trip planning calculator',
              'Use the fresh SOC read from your car when available, otherwise enter '
              'SOC manually. Your battery capacity and average consumption are '
              'always user-entered assumptions — no invented BMS values.',
              Icons.route_outlined),
          if (_planRangeKm != null)
            _info('Planning estimate: ${_planRangeKm!.toStringAsFixed(0)} km',
                _planRangeOrigin, Icons.route_outlined),
          TextField(
            controller: _planningSoc,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(
              labelText: 'Current SOC (%) — enter when live car SOC is unavailable',
              helperText: 'The planner prefers a fresh read-only car SOC sample, if present.',
              prefixIcon: Icon(Icons.battery_charging_full_outlined),
            ),
          ),
          TextField(
            controller: _batteryCapacity,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(
              labelText: 'Usable battery capacity (kWh) — user provided',
              helperText: '5–200 kWh. Not inferred from vehicle model.',
              prefixIcon: Icon(Icons.battery_full_outlined),
            ),
          ),
          TextField(
            controller: _planningConsumption,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(
              labelText: 'Your average consumption (kWh/100 km)',
              helperText: '5–45. Use your verified trip average; not measured by this app.',
              prefixIcon: Icon(Icons.bolt_outlined),
            ),
          ),
          Wrap(spacing: 12, runSpacing: 10, children: [
            FilledButton.icon(
              onPressed: _calculatePlanningRange,
              icon: const Icon(Icons.calculate_outlined),
              label: const Text('Calculate estimated km'),
            ),
            OutlinedButton.icon(
              onPressed: (_rangeBusy || _demo) ? null : _saveCapacity,
              icon: const Icon(Icons.save_outlined),
              label: const Text('Save capacity'),
            ),
            OutlinedButton.icon(
              onPressed: (_rangeBusy || _demo) ? null : _savePlanningConsumption,
              icon: const Icon(Icons.save_as_outlined),
              label: const Text('Save consumption'),
            ),
          ]),
          if (_rangeBusy) const LinearProgressIndicator(),
          Text(_planRangeOrigin),
          const Divider(),
          if (!_demo) const MeasuredTripRange(),
          if (!_demo) const Divider(),
        ]);
      case LauncherPage.automations:
        return _section('Automations',
            'Send Telegram notifications when vehicle state changes', [
          _info(_betaStatus['active'] == true ? 'Vehicle monitor running' : 'Vehicle monitor idle',
              'Enable Telegram delivery and the rules you want to receive.',
              Icons.shield_outlined),
          SwitchListTile(
            value: _rulesMasterEnabled,
            onChanged: _rulesBusy || _demo
                ? null : (value) => _automationAction('master', enabled: value),
            title: const Text('Enable automatic vehicle alerts'),
            subtitle: const Text('Allows enabled rules to send to your saved Telegram chat.'),
          ),
          Row(children: [
            const Text('WHEN  '),
            DropdownButton<String>(
              value: _newRuleKind,
              items: const [
                DropdownMenuItem(value: 'SOC_BELOW', child: Text('SOC below threshold')),
                DropdownMenuItem(value: 'POWER_ON', child: Text('Vehicle power ON')),
                DropdownMenuItem(value: 'POWER_OFF', child: Text('Vehicle power OFF')),
                DropdownMenuItem(value: 'CHARGE_STARTED', child: Text('Charging started')),
                DropdownMenuItem(value: 'CHARGE_FINISHED', child: Text('Charging finished')),
              ],
              onChanged: _rulesBusy || _demo ? null : (value) {
                if (value != null) setState(() => _newRuleKind = value);
              },
            ),
          ]),
          if (_newRuleKind == 'SOC_BELOW')
            TextField(
              controller: _socRuleThreshold,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(labelText: 'SOC threshold 1–99%'),
            ),
          FilledButton.icon(
            onPressed: _rulesBusy || _demo ? null : () => _automationAction('add'),
            icon: const Icon(Icons.add),
            label: const Text('Add notification rule (initially OFF)'),
          ),
          if (_rulesBusy) const LinearProgressIndicator(),
          Text(_rulesMessage),
          if (_storedRules.isEmpty)
            const Text('No saved rules yet.'),
          ..._storedRules.map((rule) {
            final id = rule['id'] as String? ?? '';
            final enabled = rule['enabled'] == true;
            return ListTile(
              title: Text(_ruleLabel(rule)),
              subtitle: const Text('THEN: send Telegram notification'),
              leading: const Icon(Icons.notifications_none_outlined),
              trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                Switch(
                  value: enabled,
                  onChanged: _rulesBusy || _demo ? null
                      : (value) => _automationAction('toggle', id: id, enabled: value),
                ),
                IconButton(
                  onPressed: _rulesBusy || _demo
                      ? null : () => _automationAction('remove', id: id),
                  icon: const Icon(Icons.delete_outline),
                  tooltip: 'Remove saved rule',
                ),
              ]),
            );
          }),
        ]);
      case LauncherPage.telegram:
        return _section('Telegram bot',
            'Encrypted credentials • manual status and automatic notifications', [
          _info(
            _tgConfigured ? 'Bot credentials saved' : 'Bot not configured',
            'Send current status manually or enable delivery and choose rules in Automations.',
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
                    ? null : _confirmManualStatus,
                icon: const Icon(Icons.directions_car_outlined),
                label: const Text('Send current car status'),
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
          Text('Pending messages: $_tgPending'),
          SwitchListTile(
            title: const Text('Enable automatic delivery and retries'),
            subtitle: const Text('Uses the saved Telegram chat when a network is available.'),
            value: _tgAutoRetry,
            onChanged: _tgBusy || _demo || !_tgConfigured
                ? null : (value) => _telegramAction('automaticRetry', enabled: value),
          ),
          Wrap(spacing: 12, runSpacing: 8, children: [
            OutlinedButton.icon(
              onPressed: _tgBusy || _demo || !_tgConfigured || _tgPending == 0
                  ? null : () => _telegramAction('retry'),
              icon: const Icon(Icons.refresh_outlined),
              label: const Text('Retry pending message'),
            ),
            OutlinedButton.icon(
              onPressed: _tgBusy || _demo || _tgPending == 0
                  ? null : () => _telegramAction('dropQueue'),
              icon: const Icon(Icons.delete_sweep_outlined),
              label: const Text('Clear pending messages'),
            ),
          ]),
          Text(_tgMessage),
          if (_tgBusy) const LinearProgressIndicator(),
          const Text('Manual status asks for confirmation. Enabled alerts are queued during network loss.'),
        ]);
      case LauncherPage.controls:
        return _section('Vehicle controls', 'Read vehicle state and confirm changes while parked', [
          ComfortControls(
            readings: _comfortMappings,
            busy: _adbBusy,
            demo: _demo,
            refresh: () => _adbAction('comfortMappings'),
            write: (id, value) async {
              final result = await _vehicleMethods.invokeMapMethod<Object?, Object?>(
                'comfortWrite', {'id': id, 'value': value},
              ) ?? {};
              if (mounted && result['readback'] is int) {
                setState(() => _comfortMappings = {
                  ..._comfortMappings, '${id}_state': result['readback'],
                });
              }
              return result;
            },
          ),
        ]);
      case LauncherPage.settings:
        return _section('Settings',
            'Launcher, ADB and offline voice settings', [
          OutlinedButton.icon(
            onPressed: () => _openSystemTarget('chooseHome'),
            icon: const Icon(Icons.home_outlined),
            label: const Text('Choose default launcher'),
          ),
          SwitchListTile(
            value: _lightMode.value,
            onChanged: _setTheme,
            title: const Text('Light scene mode'),
            subtitle: const Text('Switch between light and dark appearance'),
            secondary: Icon(_lightMode.value ? Icons.light_mode : Icons.dark_mode),
          ),
          ExpansionTile(
            title: const Text('Advanced'),
            leading: const Icon(Icons.build_outlined),
            children: [
              SwitchListTile(
                value: _demo,
                onChanged: _toggleDemo,
                title: const Text('Explicit demo mode'),
                subtitle: const Text('Synthetic data; never controls the vehicle'),
              ),
              _info('ADB: ${_snapshot.adbState}',
                  'Helper: ${_snapshot.helperState}', Icons.usb_outlined),
              OutlinedButton.icon(
                onPressed: (_adbBusy || _demo) ? null : _exportDiagnostics,
                icon: const Icon(Icons.file_download_outlined),
                label: const Text('Export sanitized diagnostics JSON'),
              ),
              const Text('Only use ADB setup while the car is safely parked. '
                  'Enable the head-unit Wireless ADB setting first. '
                  'Approve the ADB prompt on the factory screen. Open Vehicle Controls '
                  'to review the controls available on your firmware.'),
              const SizedBox(height: 8),
              Text(_diagnostics),
              if (_ownerSensors.isNotEmpty)
                _info(
                  'Sensor candidates',
                  'Tyres, kPa (FL / FR / RL / RR): '
                  '${_ownerSensors['frontLeftKpa'] ?? '—'} / ${_ownerSensors['frontRightKpa'] ?? '—'} / '
                  '${_ownerSensors['rearLeftKpa'] ?? '—'} / ${_ownerSensors['rearRightKpa'] ?? '—'}\n'
                  'Cabin / outside: ${_ownerSensors['cabinCelsius'] ?? '—'} / '
                  '${_ownerSensors['outsideCelsius'] ?? '—'} °C • Raw energy: ${_ownerSensors['energyCounterRaw'] ?? '—'}',
                  Icons.sensors_outlined,
                ),
              if (_motionCandidate.isNotEmpty)
                _info(
                  'Motion firmware diagnostic — RAW ONLY',
                  'Speed: ${_motionCandidate['speedRawFloatCandidate'] ?? '—'} '
                  '(unit unknown) • Gear: '
                  '${_motionCandidate['gearRawIntCandidate'] ?? '—'} '
                  '(enum unknown) • Raw diagnostic only.',
                  Icons.speed_outlined,
                ),
              if (_chargingCandidate.isNotEmpty)
                _info(
                  'Charging diagnostic — UNVERIFIED',
                  'Connector: ${_chargingCandidate['gunKindCandidate'] ?? 'unknown'} '
                  '(raw ${_chargingCandidate['gunStateRaw'] ?? '—'}). '
                  'BMS: ${_chargingCandidate['bmsPhaseCandidate'] ?? 'unknown'} '
                  '(raw ${_chargingCandidate['bmsStateRaw'] ?? '—'}). '
                  'Hypothesis: ${_chargingCandidate['chargeStateCandidate'] ?? 'unknown'}. '
                  'No completion alerts are authorized.',
                  Icons.battery_charging_full_outlined,
                ),
              if (_adbBusy) const LinearProgressIndicator(),
              const SizedBox(height: 8),
              Wrap(
                spacing: 12,
                runSpacing: 8,
                children: [
                  FilledButton.icon(
                    onPressed: (_adbBusy || _demo || _dashboardStarting || _dashboardEnabled)
                        ? null : () => _startDashboard(manual: true),
                    icon: const Icon(Icons.usb),
                    label: const Text('Retry vehicle connection'),
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
                    onPressed: (_adbBusy || _demo) ? null : () => _adbAction('firmware'),
                    icon: const Icon(Icons.developer_board_outlined),
                    label: const Text('Probe firmware identity (read-only)'),
                  ),
                  OutlinedButton.icon(
                    onPressed: (_adbBusy || _demo) ? null : () => _adbAction('fids'),
                    icon: const Icon(Icons.manage_search),
                    label: const Text('Probe vehicle helper and firmware FIDs'),
                  ),
                  OutlinedButton.icon(
                    onPressed: (_adbBusy || _demo) ? null : () => _adbAction('charging'),
                    icon: const Icon(Icons.ev_station_outlined),
                    label: const Text('Read charging state once (read-only candidate)'),
                  ),
                  OutlinedButton.icon(
                    onPressed: (_adbBusy || _demo) ? null : () => _adbAction('ownerSensors'),
                    icon: const Icon(Icons.sensors_outlined),
                    label: const Text('Read tyre and temperature candidates'),
                  ),
                  OutlinedButton.icon(
                    onPressed: (_adbBusy || _demo || _dashboardStarting || _dashboardEnabled)
                        ? null : () => _adbAction('motion'),
                    icon: const Icon(Icons.speed_outlined),
                    label: const Text('Probe raw speed & gear (GET-only)'),
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
              const SizedBox(height: 8),
              Wrap(spacing: 12, runSpacing: 8, children: [
                OutlinedButton(
                  onPressed: _rangeBusy || _demo || _dashboardEnabled ? null : _sampleDashboardOnce,
                  child: const Text('Read dashboard once'),
                ),
                OutlinedButton(
                  onPressed: _rangeBusy || _demo || _dashboardEnabled ? null : _sampleTelemetry,
                  child: const Text('Read raw energy once'),
                ),
              ]),
              Text(_rangeMessage),
              SwitchListTile(
                title: const Text('Preview animated charging cable'),
                value: _chargeDesignPreview,
                onChanged: (value) => setState(() => _chargeDesignPreview = value),
              ),
              if (_chargeDesignPreview) const ChargingFlowScene(previewEnabled: true),
            ],
          ),
          const Text('Offline voice assistant'),
          Wrap(spacing: 8, children: [
            OutlinedButton(
              onPressed: _demo ? null : () => _voiceAction('language', 'ar'),
              child: const Text('صوت عربي'),
            ),
            OutlinedButton(
              onPressed: _demo ? null : () => _voiceAction('language', 'en'),
              child: const Text('English voice'),
            ),
            OutlinedButton(
              onPressed: _demo ? null : _typeVoiceCommand,
              child: const Text('Type an assistant command'),
            ),
            OutlinedButton(
              onPressed: _demo ? null : () => _voiceAction('wheelSettings'),
              child: const Text('Steering-wheel voice settings'),
            ),
          ]),
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
                  onTap: () {
                    setState(() => _page = page);
                    if (page == LauncherPage.apps) unawaited(_loadInstalledApps());
                    if (page == LauncherPage.controls && !_dashboardStarting &&
                        _snapshot.adbState == 'connected') {
                      unawaited(_adbAction('comfortMappings'));
                    }
                    unawaited(_readBetaStatus());
                  },
                  borderRadius: BorderRadius.circular(15),
                  child: SizedBox(
                    height: 65,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(page.icon, color: active ? _navigationActive : _secondary),
                        const SizedBox(height: 4),
                        Text(page.label,
                            style: TextStyle(
                              fontSize: 10,
                              color: active ? _navigationActive : _secondary,
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            children: [
              Row(children: [
                Icon(Icons.auto_awesome, color: _accent),
                const SizedBox(width: 12),
                const Text('BYD Smart Launcher',
                    style:
                        TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                const Spacer(),
                IconButton(
                  onPressed: _demo ? null : () => _voiceAction('start'),
                  tooltip: 'Talk to the offline assistant',
                  icon: const Icon(Icons.mic),
                ),
                const SizedBox(width: 8),
                Text(
                  _demo ? 'Demo mode' : _dashboardSampledAt != null
                      ? 'Vehicle online' : _dashboardStarting ? 'Connecting…' : 'Vehicle offline',
                  style: TextStyle(color: _secondary),
                ),
                const SizedBox(width: 20),
                Icon(Icons.settings_input_antenna,
                    color: _secondary),
              ]),
              const SizedBox(height: 18),
              Expanded(child: _content()),
              const SizedBox(height: 10),
              _navigation(),
            ],
          ),
        ),
      ),
    );
  }
}
