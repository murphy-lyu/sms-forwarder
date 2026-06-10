# SMS Forwarder — CLAUDE.md

## 项目概述

Android App，监听设备收到的短信验证码，通过 Telegram Bot API 自动转发到指定 Chat ID。

- **包名**: `com.murphy.smsforwarder`
- **Min SDK**: API 26 (Android 8.0)
- **Target SDK**: API 35 (Android 15)
- **语言**: Kotlin
- **UI**: Jetpack Compose (Material3)

## 技术栈

| 组件 | 用途 |
|------|------|
| Jetpack Compose | UI 框架 |
| ForegroundService | 后台常驻服务 |
| BroadcastReceiver | 接收 SMS 广播（动态注册）|
| OkHttp 4.12.0 | Telegram Bot API HTTP 请求 |
| Gson 2.11.0 | 转发记录 JSON 序列化 |
| SharedPreferences | 配置与日志持久化 |

## 已实现模块

### `SmsReceiver.kt`
- `BroadcastReceiver`，**不**在 Manifest 静态注册（API 26+ 对隐式广播有限制）
- 由 `ForwardService.onCreate()` 动态注册，`RECEIVER_EXPORTED` flag 以接收系统广播
- `onDestroy()` 时注销
- 读取 `KEY_FORWARD_ALL` 开关；关闭时走 OTP 过滤逻辑
- **OTP 过滤规则**：短信同时满足以下两条才触发转发
  - 包含 4–8 位连续数字
  - 包含任一关键词：`验证码`、`验证`、`码`、`动态密码`、`一次性密码`、`code`、`otp`、`verify`、`verification`、`activation`、`password`

### `ForwardService.kt`
- `Service`，`foregroundServiceType="dataSync"`
- `onCreate()`：创建通知 Channel（`IMPORTANCE_HIGH`）、调用 `startForeground()`、申请 `PARTIAL_WAKE_LOCK`、动态注册 `SmsReceiver`
- `onStartCommand(ACTION_FORWARD)`：接收 sender + message，启动后台线程调用 `TelegramForwarder.send()`，完成后保存日志
- 日志截取前 30 字存入 `ForwardLog`，最多保留 20 条，写入 SharedPreferences
- 包含 `ForwardLog` data class（`timestamp`, `sender`, `message`, `success`）
- **SharedPreferences 键常量集中在此**（见下方）

**所有 SharedPreferences 键**:

| 常量 | 键名 | 类型 | 说明 |
|------|------|------|------|
| `KEY_BOT_TOKEN` | `bot_token` | String | Telegram Bot Token |
| `KEY_CHAT_ID` | `chat_id` | String | Telegram Chat ID |
| `KEY_LOGS` | `forward_logs` | String (JSON) | 最近 20 条 ForwardLog |
| `KEY_FORWARD_ALL` | `forward_all` | Boolean | 是否转发所有短信 |

### `TelegramForwarder.kt`
- `object` 单例，持有 `OkHttpClient`
- 从 SharedPreferences 读取 Bot Token 和 Chat ID（任一为空则直接返回 `false`）
- POST 到 `https://api.telegram.org/bot{token}/sendMessage`
- 消息格式（无 Markdown，避免特殊字符转义问题）：
  ```
  📱 SMS Forwarder
  发件人：{sender}
  时间：yyyy-MM-dd HH:mm:ss
  内容：{body}
  ```

### `BootReceiver.kt`
- 监听 `BOOT_COMPLETED` 和 `MY_PACKAGE_REPLACED`
- 仅在 Bot Token 已配置时才启动 `ForwardService`

### `MainActivity.kt`
- `ComponentActivity` + Compose UI
- `onCreate()` 自动检测 Bot Token，已配置则调用 `startForegroundService()`
- 运行时权限申请：`RECEIVE_SMS`、`READ_SMS`；Android 13+（API 33+）额外申请 `POST_NOTIFICATIONS`
- **UI 组件**（从上到下）：
  1. **电池优化警告 Banner**：检测 `PowerManager.isIgnoringBatteryOptimizations()`，未加白名单则显示红色卡片 + "去设置"按钮（跳转 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）；从系统设置返回后自动刷新（`DefaultLifecycleObserver.onResume`）
  2. **Telegram Configuration**：Bot Token 输入框、Chat ID 输入框、Save 按钮
  3. **Forwarding Service 开关**：手动启停 `ForwardService`
  4. **转发所有短信开关**：持久化到 `KEY_FORWARD_ALL`
  5. **Recent Forwards 列表**：最多 20 条，通过 `OnSharedPreferenceChangeListener` 实时刷新

## 权限清单

```xml
RECEIVE_SMS / READ_SMS          — 接收和读取短信
INTERNET                        — Telegram API 网络请求
FOREGROUND_SERVICE              — 启动前台服务
FOREGROUND_SERVICE_DATA_SYNC    — dataSync 类型前台服务（API 34+）
WAKE_LOCK                       — 保持 CPU 唤醒完成网络请求
RECEIVE_BOOT_COMPLETED          — 开机自启
POST_NOTIFICATIONS              — 前台服务通知（API 33+）
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS — 引导用户加入白名单
```

## 已知问题 / 待办

- `serviceRunning` 状态在 Activity 重建后不持久（Switch 外观与实际服务状态可能不同步）
- Android 14+（API 34+）从后台启动 `dataSync` 类型前台服务有限制；目前依赖 Service 已在运行时 SmsReceiver 直接触发 `onStartCommand`，冷启动场景存在风险
- 仅在 TECNO LJ9 Android 15 上验证过，其他品牌厂商电池优化策略未测试
- 无多转发通道支持（目前仅 Telegram）
