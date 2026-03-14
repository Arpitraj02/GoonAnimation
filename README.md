# GoonAnimation 🎨

A production-ready, frame-by-frame animation editor for Android — inspired by Flipaclip.

[![Android Build](https://github.com/Arpitraj02/GoonAnimation/actions/workflows/android-build.yml/badge.svg)](https://github.com/Arpitraj02/GoonAnimation/actions/workflows/android-build.yml)

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🎞️ Frame Editor | Full frame-by-frame animation with unlimited frames |
| 🧅 Onion Skin | See previous/next frames as transparent overlays |
| 🖌️ Drawing Tools | Pen, Brush (soft), Eraser, Fill Bucket |
| 🎨 Color Picker | HSL color picker with presets and opacity |
| 🗂️ Layers | Multiple layers per frame with visibility controls |
| ↩️ Undo/Redo | 30-step undo/redo history per layer |
| ▶️ Preview Player | Play animation in real-time at set FPS |
| 📤 Export GIF | Export animation as animated GIF |
| 📤 Export MP4 | Export animation as MP4 video via MediaCodec |
| 💾 Autosave | Projects autosave 3 seconds after each change |
| 🔍 Zoom & Pan | Pinch-to-zoom + drag to pan the canvas |

---

## 📱 Screenshots

> Open the app, tap **+** to create a project, draw on the canvas, and tap ▶️ to preview!

---

## 🚀 Running Locally

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK (min API 24, target API 35)

### Steps
```bash
# Clone the repository
git clone https://github.com/Arpitraj02/GoonAnimation.git
cd GoonAnimation

# Open in Android Studio, or build from CLI:
./gradlew assembleDebug

# Install on a connected device:
./gradlew installDebug
```

The debug APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ How the Animation Engine Works

### Drawing Engine (`engine/DrawingEngine.kt`)
- Maintains a `Path` object for the current stroke
- Uses **quadratic Bézier curves** between touch points for smooth lines
- Each tool type modifies a `Paint` object differently:
  - **Pen**: `STROKE` style, round caps, no blur
  - **Brush**: `STROKE` style with `BlurMaskFilter` for soft edges
  - **Eraser**: `PorterDuff.Mode.CLEAR` to erase pixels
  - **Fill**: Iterative flood-fill with stack-based BFS

### Frame/Layer System
- Each `Frame` contains one or more `Layer` objects
- Each `Layer` wraps a mutable `Bitmap` (ARGB_8888)
- Compositing renders layers bottom-to-top with alpha
- Onion skin blends previous/next frame composites with colour tints

### Undo/Redo
- Before each stroke, a **full bitmap snapshot** is copied to the undo stack (max 30 steps)
- Undo restores the previous snapshot, redo moves forward
- Memory efficient: bitmap copies are recycled when cleared

### Export Pipeline
- **GIF**: Pure Kotlin NeuQuant colour quantizer + LZW encoder, no native libraries needed
- **MP4**: Android `MediaCodec` + `MediaMuxer` hardware-accelerated H.264 encoder

### Persistence
- Projects stored in `filesDir/projects/<id>/`
- Frame layers saved as individual PNG files
- Metadata saved as JSON (no database needed)

---

## 📥 Downloading the APK from GitHub Actions

1. Go to the **[Actions](../../actions)** tab
2. Click on the latest **Android Build** workflow run
3. Scroll to the **Artifacts** section at the bottom
4. Download **GoonAnimation-debug.apk**
5. Enable "Install from unknown sources" on your Android device
6. Install and enjoy! 🎉

---

## 🏗️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.0 |
| UI Framework | Jetpack Compose + Material Design 3 |
| Architecture | MVVM + StateFlow |
| Navigation | Compose Navigation |
| Async | Kotlin Coroutines |
| Drawing | Android Canvas API |
| Persistence | Internal file storage + Gson |
| Image Loading | Coil |
| Export | MediaCodec (MP4) + Pure Kotlin GIF encoder |

---

## 📂 Project Structure

```
app/src/main/kotlin/com/goonanimation/
├── GoonAnimationApp.kt          # Application class
├── MainActivity.kt              # Single-activity host + NavGraph
├── data/
│   ├── model/
│   │   ├── AnimationProject.kt  # Top-level project model
│   │   ├── Frame.kt             # Frame model with layer composition
│   │   └── Layer.kt             # Layer model wrapping a Bitmap
│   └── repository/
│       └── ProjectRepository.kt # Save/load projects to disk
├── engine/
│   ├── DrawingEngine.kt         # Core drawing / stroke engine
│   ├── DrawingTool.kt           # Tool enum
│   └── ExportEngine.kt          # GIF + MP4 export
└── ui/
    ├── components/
    │   ├── DrawingCanvas.kt     # Zoomable drawing canvas composable
    │   ├── ToolbarPanel.kt      # Drawing tools + undo/redo toolbar
    │   ├── Timeline.kt          # Bottom frame timeline
    │   ├── LayersPanel.kt       # Layers side panel
    │   └── ColorPicker.kt       # HSL color picker dialog
    ├── screen/
    │   ├── home/
    │   │   ├── HomeScreen.kt    # Project library screen
    │   │   └── HomeViewModel.kt
    │   └── editor/
    │       ├── EditorScreen.kt  # Main editor screen
    │       └── EditorViewModel.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.
