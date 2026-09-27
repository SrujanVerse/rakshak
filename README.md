# RAKSHAK — On-Device AI Two-Wheeler Safety Companion

RAKSHAK is an on-device AI safety system designed for two-wheeler riders. It combines continuous 50Hz sensor analysis, SisFall-trained 1D CNN crash classification, multi-signal safety gates, multi-sensor verification (Camera, Microphone, GPS), on-device `Qwen2.5-0.5B-Instruct` reasoning, and automated emergency SOS alerts.

---

## 🌟 Key Features

- **Continuous 50Hz Sensor Monitoring**: Foreground service reading Accelerometer & Gyroscope sensors.
- **On-Device 1D CNN Classifier**: SisFall dataset model running real-time TFLite inference every 500ms on 2-second windows.
- **Deterministic Multi-Signal Gate (`IncidentDecisionEngine`)**: Filters out ordinary phone movements (shaking, picking up, walking) to prevent false alarms.
- **Interactive Emergency Countdown**: 10-second "ARE YOU OKAY?" UI with instant `[ I'M GOOD ]` rider cancellation.
- **Multi-Sensor Verification**: Automated post-countdown probing for rider movement (Camera), voice presence (Microphone), and location coordinates (GPS).
- **On-Device Local LLM Reasoning**: Native `llama.cpp` JNI execution of `Qwen2.5-0.5B-Instruct` GGUF in under 3 seconds with **zero cloud APIs or internet dependency**.
- **Deterministic Safety Enforcement (`SafetyValidator`)**: Hard rules validate LLM recommendations before triggering emergency alerts.
- **Emergency SOS Alert**: Automated SMS dispatch with Google Maps location links and local integrity logging.
- **Incident History & Timelines**: Full timeline visualization of recorded safety events and Qwen AI reports.

---

## 📁 Repository Architecture

```text
app/src/main/java/com/rakshak/
├── RakshakApplication.kt
├── core/
│   ├── ai/                      # Local LLM reasoning & safety validator
│   │   ├── LocalLlmReportGenerator.kt
│   │   ├── PromptBuilder.kt
│   │   ├── SafetyValidator.kt
│   │   └── llm/LlamaAndroidEngine.kt
│   ├── classifier/              # SisFall TFLite classifier & preprocessor
│   │   ├── SensorPreprocessor.kt
│   │   └── TFLiteCrashClassifier.kt
│   ├── detector/                # Real-time state machine & decision gate
│   │   ├── CrashDetector.kt
│   │   └── IncidentDecisionEngine.kt
│   ├── incident/                # Structured incident data & repository
│   │   ├── IncidentData.kt
│   │   └── IncidentRepository.kt
│   └── sensor/                  # 50Hz foreground sensor service & buffers
│       ├── SensorRingBuffer.kt
│       └── SensorService.kt
└── ui/
    ├── main/MainActivity.kt     # Main 4-tab protection dashboard
    ├── emergency/EmergencyCountdownActivity.kt  # Emergency countdown & verification
    └── incident/IncidentDetailActivity.kt        # Timeline & Qwen breakdown
```

---

## 🚀 Model Setup Instructions

The local reasoning engine requires the `Qwen2.5-0.5B-Instruct-Q4_K_M.gguf` model file (468 MB).

To place the GGUF model file into assets:

1. Download `Qwen2.5-0.5B-Instruct-Q4_K_M.gguf` from HuggingFace:
   [https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF)
2. Place the downloaded `.gguf` file in:
   `app/src/main/assets/models/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf`

---

## 🛠️ Build & Installation

### Build Debug APK:
```bash
./gradlew assembleDebug
```

### Install via ADB to Connected Device:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Copyright © 2026 RAKSHAK Team.
