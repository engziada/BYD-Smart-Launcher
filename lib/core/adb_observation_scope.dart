/// Whether UI observations must be cleared after an ADB command.
///
/// An already connected native session can be re-used without invalidating
/// the current speed/gear or charging diagnostic. A genuine reconnect or
/// disconnect MUST clear the live UI values (the native export may retain
/// older observations only with sessionValid=false).
bool shouldClearAdbObservations(
  String action,
  Map<Object?, Object?> reply,
) =>
    action == 'disconnect' ||
    (action == 'connect' && reply['sessionReused'] != true);
