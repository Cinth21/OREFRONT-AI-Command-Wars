# OREFRONT — AI COMMAND WARS

A new **native Android 2D real-time strategy classroom prototype**. Built with pure Java, Android Canvas, and local Java TCP/UDP sockets; no third-party engine, paid service, internet gameplay, Android Studio or Mac host required.

**Important provenance:** No original OREFRONT source was supplied or found. This is a new implementation of the requested game features, **not a patch** to an existing OREFRONT application.

## Features implemented in source code

- Play vs AI offline. Easy/Medium/Hard (2/3/5 AI factions), based on building priorities, resource evaluation, hero/infantry/tank production and BFS routing.
- Phone-hosted LAN with **maximum six humans** (one host plus five joins) on same Wi-Fi/hotspot: TCP port **38417**. Server exclusively runs on host **Android phone**.
- Six-digit join code, UDP discovery (port **38418**), manual room code+IP fallback.
- Lobby ready flags, player list, team and faction assignment, host-only start/config, leave.
- Match configurations: 1v1 (2 humans), 2v2 (4), 3v3 (6), FFA (2-6), and custom teams (2-6).
- Six fixed, separate colored HQ locations and player resources; unique faction naming/assignment.
- Authoritative economic simulation: gold/oil/iron/energy; mines/refineries/generators; barracks/factory/airfield; upgrades, soldiers/tanks/aircraft/hero; unit movement, auto-attack, sector capture, timed and conquest victories.
- Recover same slot with device token; host may optionally assign AI to a disconnected faction. A disconnect never automatically triggers victory.
- Event-driven state snapshots (state changes only); client submits commands, not official world state. Each active connection receives its own intelligence notification.

## Limitations and acceptance status

- **The requested six-physical-Android-device acceptance test has NOT been run.** The included automated test creates a genuine TCP host and five real socket client connections **on one machine**, proving protocol and simulation logic, not actual hotspot interoperability.
- Full matching game rules and synchronization are implemented as a **basic 2D strategy prototype**, not a production RTS (e.g. no full fog-of-war, persistent accounts, advanced unit animations, NAT traversal, or guaranteed background networking).
- 6-player WLAN stability and hotspot client-to-host connectivity depend on Android model/network policy; some devices disallow five tethering clients or block peer traffic. Try private Wi-Fi if hotspot limits clients.
- If host phone exits or is terminated, there is no host migration. Host must remain foreground/unlocked; all clients disconnect if host closes the game.
- Build uses an Android debug signing key; suitable for educational sideload testing, not Google Play production.
- Automatic room discovery can fail on multicast/broadcast restricted networks; **IP+code** is the documented workaround.
- Android 8.0 (API 26) or newer; target SDK 28 for local socket compatibility. Future Android versions may require additional local-network permissions.
- Game UI is landscape and uses English controls. Does not directly read or modify another APK.

## How to build an Android APK WITHOUT Android Studio on MacBook

1. Extract the ZIP.
2. Create a **new** public GitHub repository `OREFRONT-AI-Command-Wars` (do not overwrite Snake or Rift).
3. Upload the **contents of this extracted folder at the repository root**, including hidden `.github/`, `app/`, `tests/`, `build.gradle`, `settings.gradle`, `gradle.properties`.
4. GitHub → **Actions** → **Build OREFRONT Android APK** (automatically triggers after commit; or `Run workflow`).
5. The first stage runs the pure Java engine, 6-client TCP loopback, and victory tests. Next it builds a debug APK using Android SDK in GitHub's virtual machine.
6. When green, open the workflow **Summary → Artifacts → OREFRONT-Android-APK**. Download ZIP and extract `OREFRONT-AI-Command-Wars.apk`.
7. GitHub repository → **Releases** → **Draft a new release**. Tag `v1.0.0`, title `OREFRONT AI Command Wars v1.0`, upload the extracted APK, then **Publish release**. Copy the release URL into Messenger. Classmates can download/install on Android devices; they may need to enable "Install unknown apps" for their browser.

**If .github is hidden in Finder:** Press `Command + Shift + .`; or create/edit `.github/workflows/build-apk.yml` directly in the GitHub browser, copying the provided workflow code. Do not upload the ZIP itself in place of sources.

## Host and join over hotspot (NO INTERNET)

1. Install **the same APK** on up to six Android phones. Choose **one phone as hotspot/host** (not MacBook).
2. Connect up to five other phones to the same hotspot or Wi-Fi. Some hotspots limit client counts; check device capabilities.
3. Host opens game → **CREATE LAN ROOM** → enters username. Host sees six-digit room code and local host IP.
4. Other phones open game → **SEARCH LAN ROOMS**, choose room; or **JOIN BY CODE + IP** → enter six-digit code. If discovery fails, copy Host IP from lobby screen.
5. Host selects max players 2–6, match type, map, duration, starting resources, optional AI, etc. Select roster player and change faction or team (CUSTOM mode).
6. Every human taps **I'M READY**. Host (Player 1) taps **START MATCH**.
7. During match: tap your base-adjacent free tile then tap MINE/REFINERY/GENERATOR/BARRACKS/FACTORY/AIRFIELD **before** tapping tile? **Actual sequence:** tap a build button, then tap the target free tile within construction radius. Tap SOLDIER, TANK, AIRCRAFT or HERO to purchase if prerequisite building/resources available. Tap your colored unit, then destination to move. The units auto-fight and capture yellow sectors.
8. For disconnects, host gets notification. Base stays. Host can open **AI CONTROL** and select a disconnected faction for takeover. The disconnected player may rejoin with the **same phone / same app token**, IP+code, to reclaim their original slot.
9. Time limit or conquest determines results. All sockets receive the authoritative result.

## Automated tests, on Mac (only JDK required; GitHub runs for you)

```bash
mkdir -p out
javac -d out app/src/main/java/com/student/orefront/{GameCore,LanRoom}.java tests/*.java
java -cp out StrategySmokeTest
java -cp out SixPlayerLanTest
java -cp out BattleResultsTest
```

These tests cannot substitute for real six-phone acceptance.

## Six-device manual acceptance checklist (run before submitting as fully working)

- [ ] Install same APK and launch on **six separate Android phones**.
- [ ] One phone hosts; five phones join with room code on same hotspot/Wi-Fi; **seventh joining device is rejected**.
- [ ] All six show correct names, ready flags, unique factions, distinct colors and HQs.
- [ ] Host starts a six-human 3v3 match; each phone sees the same match timer.
- [ ] All six independently build a base structure, see resource changes, recruit a unit, move it and see the action on all devices.
- [ ] Capture sectors, fight and destroy bases; all six report the same winning team.
- [ ] Simulate one client disconnect; no automatic winner, base remains; host assigns AI; player reconnects to same slot.
- [ ] Repeat with FFA and custom teams; test 2, 3, 4, 5, 6 human counts as applicable.
- [ ] Monitor frame rate, Wi-Fi stability, resynchronization and background behavior.

## File guide

- `app/src/main/java/com/student/orefront/GameCore.java` — pure Java strategy rules, BFS, AI and snapshots.
- `app/src/main/java/com/student/orefront/LanRoom.java` — real host TCP, UDP discovery, client, reconnect and authoritative processing.
- `app/src/main/java/com/student/orefront/MainActivity.java` — native Android screens, lobby and tactical map touch controls.
- `tests/` — engine, network and result automated tests.
- `.github/workflows/build-apk.yml` — GitHub cloud builder and artifact.
