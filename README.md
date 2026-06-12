# ComfyuiSimpleMobileAPP

An Android client for [ComfyUI](https://github.com/comfyanonymous/ComfyUI) that supports workflow import, node configuration, Danbooru tag autocomplete, and result preview.

## Features

- **Workflow Management** - Import workflows from local JSON or directly from ComfyUI server (no plugin needed)
- **Node Configuration** - Auto-detects nodes marked with `(APP)` suffix as user-configurable inputs
- **Danbooru Tag Autocomplete** - Real-time autocomplete with local CSV data (no API calls)
- **Multi-Image Results** - Horizontal pager for browsing generated images
- **Generation History** - Auto-saves each run with input snapshots, resume polling on exit
- **Save to Gallery** - One-tap save generated images to device gallery
- **Quick Connect** - Auto-detects server status on homepage

## Requirements

- Android 8.0+ (API 26+)
- ComfyUI server with network access
- ADB for installation (or direct APK install)

## Build

```bash
# Set environment
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME="/path/to/Android/Sdk"

# Build debug APK
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Usage

### 1. Configure Server
Settings -> enter ComfyUI server URL (default: `http://your-comfyui-server:8188`)

### 2. Import Workflows
- **From Server**: Home -> "From Server" -> lists workflows from ComfyUI server
- **From File**: Home -> "Import JSON" -> paste or select local JSON

### 3. Mark Nodes for App
Add `(APP)` to node titles in ComfyUI workflow JSON to make them configurable:
```json
{"id": 8, "type": "CLIPTextEncode", "title": "Positive Prompt (APP)"}
```

### 4. Generate
Edit inputs -> tap "Generate" -> results appear above config cards

### 5. History
- Each run auto-saved with inputs and results
- Click workflow -> history list -> click entry to restore
- Star to favorite, X to delete individual, broom to clear non-favorites

## Project Structure

```
app/src/main/java/com/comfyui/client/
|-- ComfyUIApp.kt              # Application class
|-- MainActivity.kt            # Single activity
|-- data/
|   |-- api/ComfyUIApi.kt      # Retrofit API interface
|   |-- model/                  # Data classes
|   |-- repository/             # Workflow + History repository
|-- ui/
|   |-- component/              # Reusable composables
|   |-- navigation/NavGraph.kt  # Navigation routes
|   |-- screen/                 # Screens
|   |-- theme/                  # Material3 theme
|-- util/                       # WorkflowParser, ImageSaver
```

## Server API

Uses standard ComfyUI endpoints (no custom plugin required):

| Endpoint | Purpose |
|----------|---------|
| `/system_stats` | Server health check |
| `/object_info` | Node type definitions |
| `/prompt` | Submit generation |
| `/history/{id}` | Poll generation status |
| `/view` | Fetch output images |
| `/userdata?dir=workflows` | List workflow files |
| `/userdata/{path}` | Download workflow JSON |

## License

MIT
