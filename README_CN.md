# ComfyuiSimpleMobileAPP

[English](README.md) | 中文

ComfyUI 的 Android 客户端，支持工作流导入、节点配置、Danbooru 标签自动补全和结果预览。

## 功能

- **工作流管理** - 从本地 JSON 或 ComfyUI 服务器直接导入工作流（无需安装插件）
- **节点配置** - 自动识别带 `(APP)` 后缀的节点作为可配置输入项
- **Danbooru 标签自动补全** - 基于本地 CSV 数据的实时自动补全（无需调用 API）
- **多图结果** - 横向滑动浏览生成的图片
- **生成历史** - 每次运行自动保存输入快照，退出后可恢复轮询
- **保存到相册** - 一键将生成的图片保存到设备相册
- **快速连接** - 首页自动检测服务器状态

## 系统要求

- Android 8.0+ (API 26+)
- 可联网的 ComfyUI 服务器
- ADB 用于安装（也可直接安装 APK）

## 构建

```bash
# 设置环境变量
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME="/path/to/Android/Sdk"

# 构建 Debug APK
./gradlew assembleDebug

# 输出位置: app/build/outputs/apk/debug/app-debug.apk
```

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

### 1. 配置服务器
设置页面 -> 输入 ComfyUI 服务器地址（默认: `http://your-comfyui-server:8188`）

### 2. 导入工作流
- **从服务器导入**: 首页 -> "从服务器" -> 列出 ComfyUI 服务器上的工作流文件
- **从文件导入**: 首页 -> "导入 JSON" -> 粘贴或选择本地 JSON 文件

### 3. 标记节点
在 ComfyUI 工作流 JSON 中，给需要配置的节点标题添加 `(APP)` 后缀：
```json
{"id": 8, "type": "CLIPTextEncode", "title": "Positive Prompt (APP)"}
```

### 4. 生成
编辑输入 -> 点击"生成" -> 结果图片显示在配置卡片上方

### 5. 历史记录
- 每次运行自动保存输入内容和结果
- 点击工作流 -> 历史记录列表 -> 点击条目恢复
- 星标收藏，X 删除单条，扫帚清除非收藏记录

## 项目结构

```
app/src/main/java/com/comfyui/client/
|-- ComfyUIApp.kt              # Application 类
|-- MainActivity.kt            # 单 Activity
|-- data/
|   |-- api/ComfyUIApi.kt      # Retrofit API 接口
|   |-- model/                  # 数据类
|   |-- repository/             # 工作流 + 历史记录仓库
|-- ui/
|   |-- component/              # 可复用组件
|   |-- navigation/NavGraph.kt  # 导航路由
|   |-- screen/                 # 页面
|   |-- theme/                  # Material3 主题
|-- util/                       # WorkflowParser, ImageSaver
```

## 服务端 API

使用 ComfyUI 标准接口（无需自定义插件）：

| 接口 | 用途 |
|----------|---------|
| `/system_stats` | 服务器健康检查 |
| `/object_info` | 节点类型定义 |
| `/prompt` | 提交生成任务 |
| `/history/{id}` | 轮询生成状态 |
| `/view` | 获取输出图片 |
| `/userdata?dir=workflows` | 列出工作流文件 |
| `/userdata/{path}` | 下载工作流 JSON |

## 许可证

MIT