# RAKSHAK 🛡️
### On-Device Crash Detection for Two-Wheeler Riders

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Min SDK](https://img.shields.io/badge/minSdk-26-blue)]()
[![Mode](https://img.shields.io/badge/DEMO%20MODE-enabled-orange)]()

---

## Architecture

```
P0 (Safety-Critical)  ─── sense → detect → log → locate → alert
                               │                            │
                         [Sensor Path]              [Voice-SOS Path]
                         (Accelerometer/            (Independent emergency
                          Gyroscope)                 input — never depends
                                                     on sensor path)

P1 (Enrichment)       ─── Camera / Audio / AI   (starts AFTER alert in flight)
P2 (Reporting)        ─── Dashboard / PDF        (starts AFTER alert in flight)
```

**Invariants enforced in code:**
- P1/P2 failures can never delay or stop a P0 alert
- SMS send is the highest-priority action in P0
- Logging runs in parallel to SMS, never before it
- Voice-SOS is an independent path reusing the same incident/alert pipeline

---

## Operating Modes

| Mode | SMS | Sensors | Use When |
|------|-----|---------|----------|
| **DEMO** | In-memory recorder | Real or mocked | Testing, hackathon demo setup |
| **REAL** | Live `SmsManager` | Real hardware | Production demo, actual deployment |

Debug builds default to **DEMO** mode. Release builds default to **REAL** mode.
The mode switch in MainActivity persists for the session.

---

## System Readiness Screen

On launch, the app checks 6 P0 components:

| Component | What's checked |
|-----------|---------------|
| Accelerometer | Hardware sensor present |
| Gyroscope | Hardware sensor present |
| Location Permission | `ACCESS_FINE_LOCATION` granted |
| SMS Permission | `SEND_SMS` granted |
| Cellular Service | SIM state `READY` |
| Incident Log | Directory writable, file readable |

Each shows **✓ OK** or **⚠ Warning + [FIX] button**.

---

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/rakshak/
│   │   │   ├── RakshakApplication.kt       ← App entry point + exception handler
│   │   │   ├── core/
│   │   │   │   ├── mode/
│   │   │   │   │   └── ModeManager.kt      ← Global DEMO/REAL switch (AtomicReference)
│   │   │   │   ├── readiness/
│   │   │   │   │   ├── ReadinessChecker.kt ← 6-point P0 readiness evaluation
│   │   │   │   │   └── ReadinessStatus.kt  ← Data models
│   │   │   │   └── log/
│   │   │   │       └── IncidentLogIntegrityChecker.kt
│   │   │   └── ui/main/
│   │   │       ├── MainActivity.kt         ← Pure observer, zero business logic
│   │   │       └── MainViewModel.kt        ← State holder, auto-refresh every 5s
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── test/
│       └── java/com/rakshak/
│           ├── RakshakSmokeTest.kt         ← Canary test (always passes)
│           ├── core/mode/ModeManagerTest.kt
│           └── core/readiness/ReadinessStatusTest.kt
```

---

## Building

### Prerequisites
- Android Studio Iguana or later (or Android SDK CLI tools)
- JDK 17+
- Android SDK API 35

### Quick start
```bash
git clone https://github.com/CodeWithRJ006/rakshak.git
cd rakshak
./gradlew assembleDebug
./gradlew test
```

### Run tests
```bash
./gradlew :app:testDebugUnitTest --info
```

---

## Non-Negotiable Engineering Rules

1. Never modify working P0 for P1/P2 convenience
2. SMS latency > all other operations
3. Every background op has a timeout + failure path
4. No new dependency without justification
5. P0_STABLE gate: only additive changes after gate
6. VoiceTrigger is independent — never a dependency of crash detection
7. SMS send ≠ SMS delivered (always distinguished in tests)
8. Every feature: implementation + test + device validation + rollback

---

## License

MIT License — Hackathon submission for RAKSHAK crash-detection system.
