# HighlightCam

Automatic soccer highlight recorder for Android. Point your phone at the pitch, draw a goal zone, and let the app detect goals using on-device AI — no cloud, no accounts, no internet required.

HighlightCam continuously buffers video in short segments. When it detects a potential goal (ball entering the zone, crowd noise spike, or referee whistle), it stitches the relevant segments into a single MP4 clip and saves it to your gallery.

## Screenshots

<!-- Add screenshots here: setup screen, recording screen, library grid, settings -->

## Requirements

- Android 8.0 (API 26) or higher
- High-end device recommended (GPU delegate used for inference)
- Camera and microphone permissions
- Storage permission for saving clips

## Build Instructions

```bash
git clone https://github.com/your-org/highlight-cam.git
cd highlight-cam
./gradlew assembleDebug
```

The debug build uses the default debug signing key — no keystore configuration required.
Release builds also use the debug key for convenience: `./gradlew assembleRelease`.

> **Note:** This project does not use Firebase or Google Services. No `google-services.json` is needed.

## Model Setup

HighlightCam uses a YOLOv8n model converted to TensorFlow Lite format for on-device object detection.

### Export the model

```bash
pip install ultralytics
yolo export model=yolov8n.pt format=tflite half=True imgsz=320
```

This produces `yolov8n_float16.tflite`.

### Place the model file

```bash
cp yolov8n_float16.tflite app/src/main/assets/
```

If the model file is absent, the app runs in **audio-only detection mode** and displays a "Vision off — audio only" badge on the recording screen.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                │
│  SetupScreen  RecordingScreen  LibraryScreen  Settings│
│       │              │              │            │   │
│  SetupVM      RecordingVM      LibraryVM    SettingsVM│
└───────┬──────────────┬──────────────┬────────────┬──┘
        │              │              │            │
┌───────┴──────────────┴──────────────┴────────────┴──┐
│                  Domain / Data Layer                 │
│  SessionRepository    UserPreferencesRepository     │
│  GoalZone  RecordingConfig  DetectionEvent  Models  │
└───────┬──────────────┬──────────────────────────────┘
        │              │
┌───────┴──────┐ ┌─────┴──────────────────────────────┐
│   Recording  │ │         Detection Pipeline          │
│              │ │                                     │
│ Circular     │ │ TFLiteDetector → GoalEventAnalyzer  │
│ Buffer       │ │         ↓                           │
│ Recorder     │ │ AudioAnalyzer → DetectionStateMachine│
│     ↓        │ │         ↓                           │
│ ClipAssembler│ │ HighlightDetectionEngine             │
│     ↓        │ │                                     │
│ MediaStore   │ └─────────────────────────────────────┘
└──────────────┘
        ↑
┌───────┴──────────────┐
│   RecordingService   │
│  (Foreground Service)│
│  CameraX VideoCapture│
└──────────────────────┘
```

## Project Structure

```
app/src/main/java/com/highlightcam/app/
├── HCApplication.kt          # Hilt application, Timber init
├── MainActivity.kt            # Single activity, NavHost routing
├── camera/
│   └── CameraPreviewManager   # CameraX binding, frame flow
├── data/
│   ├── SessionRepository       # In-memory session state
│   └── UserPreferencesRepository # DataStore persistence
├── detection/
│   ├── AudioAnalyzer           # Mic recording, RMS, Goertzel whistle detection
│   ├── DetectionModels         # BoundingBox, Detection, AudioEvent, DebugInfo
│   ├── DetectionStateMachine   # Explicit states: Idle → Monitoring → Candidate → Triggered
│   ├── GoalEventAnalyzer       # Pure Kotlin zone intersection logic
│   ├── HighlightDetectionEngine # Orchestrator combining visual + audio
│   └── TFLiteDetector          # YOLOv8n inference, NMS, GPU delegate
├── di/
│   └── AppModule               # Hilt module providing DataStore
├── domain/
│   └── Models                  # GoalZone, RecordingConfig, RecorderState, DetectionEvent
├── navigation/
│   └── HCNavHost               # Navigation graph with animated transitions
├── recording/
│   ├── CircularBufferRecorder  # CameraX segment loop, ring buffer
│   └── ClipAssembler           # MediaExtractor + MediaMuxer stitching
├── service/
│   └── RecordingService        # Foreground service, wake lock, detection wiring
└── ui/
    ├── library/
    │   ├── LibraryScreen        # Clip grid, full-screen player, empty state
    │   └── LibraryViewModel     # MediaStore query, sort, delete
    ├── recording/
    │   ├── RecordingScreen      # Camera preview, controls, debug panel
    │   └── RecordingViewModel   # Service lifecycle, storage check, haptics
    ├── settings/
    │   ├── SettingsScreen        # Segmented controls, sliders, switches
    │   └── SettingsViewModel     # Preferences read/write
    ├── setup/
    │   ├── SetupScreen           # Goal zone drawing, camera preview
    │   └── SetupViewModel        # Drag rect, zone persistence
    └── theme/
        ├── Color.kt              # OLED dark palette, green/amber/red accents
        ├── Theme.kt              # Material3 dark theme
        └── Type.kt               # Typography scale with monospace timecode
```

## Detection Pipeline

1. **Visual detection** runs at ~3 fps via CameraX ImageAnalysis
2. Frames are resized to 320×320 and fed to YOLOv8n on GPU (CPU fallback)
3. Detections are filtered for persons (class 0) and sports balls (class 32)
4. GoalEventAnalyzer checks if the ball or player cluster intersects the goal zone
5. **Audio detection** runs at 10 Hz on a separate AudioRecord stream
6. Energy spikes (RMS > 2.8× baseline) and whistle frequencies (Goertzel at 2500/3200 Hz) are detected
7. The **state machine** combines both signals:
   - Ball in zone + audio spike → immediate clip save
   - Ball in zone alone → wait 2.5s for audio confirmation
   - Audio spike alone → wait 1.5s for visual confirmation
   - 12-second cooldown between saves

## Known Limitations

- No video stabilization — designed for tripod use
- YOLOv8n struggles in low light or heavy rain
- Audio detection can false-trigger on loud music or announcements near the mic
- Circular buffer uses disk I/O; sustained recording on budget devices may drop frames
- No cloud backup or sharing beyond the standard Android share sheet
- The Goertzel whistle detector is tuned for standard referee whistles (2500–3200 Hz)

## Troubleshooting

**Model not found / "Vision off" badge appears**
Place `yolov8n_float16.tflite` in `app/src/main/assets/`. Rebuild and reinstall.

**Camera permission denied**
The app shows a permission rationale screen. Grant camera and microphone permissions in system settings if the in-app prompt was dismissed.

**Clips not appearing in gallery**
Clips are saved to `Movies/HighlightCam/` via MediaStore. On Android 10+, they appear immediately. On older versions, a media scan may be needed. Check that storage permission is granted.

**Recording stops unexpectedly**
If the low storage warning appears (< 500 MB free), free up space. The foreground service may be killed by aggressive battery optimization — exclude HighlightCam from battery restrictions.

**Debug panel not accessible**
Enable debug mode in Settings → App → Debug mode. Then long-press the record button on the recording screen.
