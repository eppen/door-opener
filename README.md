# Door Opener — 联掌门户 × 华为 Fit 4

将联掌门户智慧社区门禁系统与华为 Fit 4 智能手表连接，实现**门口呼叫 → 手表通知 → 一键开门**的便捷体验。

## 工作原理

```
联掌门户门禁 ──呼叫事件──→ Android 桥接 App ──Wear Engine──→ 华为 Fit 4
                                                              ↓
                                                           点击"开门"
                                                              ↓
联掌门户门禁 ←──HTTP 开门指令── Android 桥接 App ←──按钮回调──┘
```

## 技术栈

- **语言**: Kotlin
- **平台**: Android (minSdk 28, targetSdk 34)
- **穿戴通信**: Huawei Wear Engine SDK — 手表通知 & 按钮回调
- **门禁接入**: MQTT / HTTP Polling / Notification Listener
- **构建**: Gradle (Kotlin DSL)

## 项目结构

```
door-opener/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/java/com/dev2026/dooropener/
│   │   ├── App.kt                         # Application 入口
│   │   ├── wear/                          # 华为 Wear Engine 层
│   │   │   ├── WearEngineManager.kt       # 穿戴设备发现 & 连接
│   │   │   ├── WearNotificationManager.kt # 手表通知发送 & 按钮回调
│   │   │   └── WearDeviceInfo.kt          # 设备信息模型
│   │   ├── door/                          # 联掌门户接入层
│   │   │   ├── DoorEvent.kt               # 门禁事件模型
│   │   │   ├── DoorEventService.kt        # 事件监听前台服务
│   │   │   ├── DoorEventParser.kt         # 事件解析器
│   │   │   ├── DoorEventListener.kt       # 事件监听接口
│   │   │   └── DoorControlManager.kt      # 开门指令管理
│   │   ├── bridge/                        # 事件桥接层
│   │   │   ├── DoorBridgeService.kt       # 核心桥接服务
│   │   │   └── CallSession.kt             # 呼叫会话管理
│   │   ├── ui/                            # 用户界面
│   │   │   ├── MainActivity.kt            # 主界面
│   │   │   └── SettingsActivity.kt        # 设置界面
│   │   └── util/                          # 工具类
│   │       ├── PreferencesManager.kt      # 本地配置
│   │       ├── NotificationHelper.kt      # 手机端通知辅助
│   │       └── BootReceiver.kt           # 开机自启
│   └── src/test/                          # 单元测试
└── README.md
```

## 快速开始

### 前置条件

1. **华为开发者账号** — 在 [AppGallery Connect](https://developer.huawei.com) 创建项目
2. **华为手机** (HarmonyOS 4.2 及以下) + **华为运动健康 App**
3. **华为 Fit 4** 手表已与手机配对
4. **联掌门户** 账号及服务器信息

### 配置步骤

1. 将 `agconnect-services.json` 放入 `app/` 目录
2. 在 `app/build.gradle.kts` 中确认 Wear Engine SDK 版本
3. 用 Android Studio 打开项目
4. 运行到华为手机上
5. 在 App 设置中配置联掌门户服务器地址和账号
6. 刷新手表连接状态

### 联掌门户配置

在 Settings 中输入：
- **Server URL**: 联掌门户 API 服务器地址
- **Username / Password**: 门禁账号
- **Event Listener Mode**: 选择 HTTP Polling 或 MQTT

## 注意事项

- **HarmonyOS NEXT 暂不支持** Fit 4 调试，需要 HarmonyOS 4.2 及以下手机
- Fit 4 是轻量级智能穿戴，不支持安装第三方 App，本方案通过 Wear Engine 通知实现交互
- 华为手机需要关闭对 Door Opener 的电池优化以确保后台可靠运行

## License

MIT
