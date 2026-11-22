# OREFRONT source-code verification report

Test run: 2026-11-23. All commands run against generated Java files in this project.

## Passed

- `javac` of pure Java `GameCore`, `LanRoom` and all three test programs.
- `StrategySmokeTest`: 6 human slots, reject seventh, 3v3 start, resources, mine/barracks construction, soldier purchase, movement command, unauthorized unit control rejected, serialization and reconstruction, disconnected base preserved, same-token recovery, and 105 AI simulations (35 per difficulty).
- `SixPlayerLanTest`: one real `ServerSocket` host + five separate real TCP `Socket` client connections using localhost, six identities, ready synchronization, 3v3 starting, seventh-join rejection, construction, unit purchase, unit movement, shared state, temporary disconnect, host-selected AI takeover, and same-slot reconnection.
- `BattleResultsTest`: team conquest victory, winner snapshot replication, and timed match results.
- Android UI Java class and all project Java sources passed `javac` type/syntax check against temporary handcrafted Android API stubs. **This is NOT a real Android SDK compilation.**

## Not performed / requires user verification

- `gradle :app:assembleDebug` cannot run locally because Android SDK / Gradle are unavailable in this runtime. Use included GitHub Actions.
- **Six physical Android devices on one hotspot / Wi-Fi have NOT been tested.** Such physical acceptance cannot be simulated or honestly certified here.
- The official six-device acceptance requirement is therefore **PENDING**.
- Background restrictions, phone hotspot client count, broadcast discovery, real FPS, physical UI sizing and connectivity require hands-on tests on compatible hardware.

## Architecture note

The host owns all player objects and mutates them only under a lock. Remote clients send requests, never arbitrary world snapshots. TCP guarantees reliable in-order commands. Broadcast UDP discovery is best-effort; manual host IP plus six-digit room code is the fallback. When game state changes, a coalesced authoritative snapshot is sent; timer-only ticks can be sent separately. This is a working baseline for a six-player classroom game, but is not the full delta/event-stream protocol of a production MMO.
