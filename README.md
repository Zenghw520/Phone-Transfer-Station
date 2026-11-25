# 多模型 AI

面向 Android 手机的多模型 AI 对话应用。配置自己的模型服务商 API，即可在一个应用中浏览模型、切换模型并管理本机对话。

## 功能

- 接入 OpenAI、Anthropic Claude、Google Gemini、DeepSeek，以及自定义 OpenAI Chat Completions 兼容接口。
- 获取服务商模型目录，按模型能力显示图片输入和思考强度选项，也可手动填写模型 ID。
- 以流式方式接收回答，并在消息中查看服务商、模型、请求用时和可用的思考摘要。
- 创建、重命名、删除和重新打开历史会话。会话按最近更新时间排列。
- 从系统照片选择器选图或使用相机拍照，预览图片后随消息发送。
- 在不同会话中运行生成任务，查看排队、生成、完成、失败或中断状态，并接收后台任务通知。
- 选择跟随系统、浅色或深色外观。

## 开始使用

1. 使用 Android Studio 打开项目并安装应用。运行设备需 Android 13 或更新版本。
2. 打开会话侧边栏中的服务商设置，添加服务商连接并填写 API Key。自定义接口还可填写 Base URL。
3. 刷新模型目录并选择模型。目录无法获取时，可填写模型 ID。
4. 新建会话并发送消息。首次向某服务商发送内容时，查看并确认传输说明。

模型调用由手机直接发送至所选服务商，使用前请准备相应服务商的 API Key，并了解其模型、计费和数据处理政策。API Key 使用 Android Keystore 加密后保存在应用私有数据中。会话和图片保存在设备本地，系统云备份已关闭。

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- 单一 Android `:app` 模块，按 UI、Presentation、Domain 和 Data 职责组织
- 服务商 HTTP API 与 Server-Sent Events 流式响应
- SQLite 本地数据存储、Android Keystore 密钥保护
- Android 前台服务运行后台生成任务

## 构建

- Android Studio 与 Android SDK
- JDK 11
- Android SDK Platform 37
- 最低 Android 版本为 Android 13（API 33）

在项目根目录执行：

```bash
./gradlew assembleDebug
./gradlew test
```

Windows PowerShell 可使用：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

## 项目结构

```text
app/src/main/java/com/example/transferstation/
├── data/       本地存储、服务商 API、会话仓库和后台任务
├── domain/     会话、服务商、模型能力与任务数据模型
├── presentation/ 页面状态及用户操作编排
├── ui/         Jetpack Compose 对话界面
└── core/       Android 平台能力
```

