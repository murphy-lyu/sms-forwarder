# Architecture

## 模块一览

```
app/src/main/java/com/murphy/smsforwarder/
├── MainActivity.kt        — Compose UI 入口，权限申请，自动启服务
├── ForwardService.kt      — 前台服务主体，动态注册 SmsReceiver，驱动转发
│   └── ForwardLog         — 转发记录 data class（定义在同文件）
├── SmsReceiver.kt         — 接收 SMS 广播，OTP 过滤，触发转发
├── TelegramForwarder.kt   — Telegram Bot API HTTP 客户端
├── BootReceiver.kt        — 开机/包替换后自动启服务
└── ui/theme/              — Material3 主题（Color / Theme / Type）
```

## 各文件职责

| 文件 | 类型 | 核心职责 |
|------|------|---------|
| `MainActivity.kt` | `ComponentActivity` | UI 配置、权限、电池白名单引导、服务手动开关 |
| `ForwardService.kt` | `Service` (foreground) | 后台常驻、WakeLock、动态注册 SmsReceiver、调用转发、保存日志 |
| `SmsReceiver.kt` | `BroadcastReceiver` | 解析 SMS PDU、OTP 过滤判断、向 ForwardService 发 Intent |
| `TelegramForwarder.kt` | `object` singleton | OkHttp POST 到 Telegram Bot API，读取 Token/ChatID |
| `BootReceiver.kt` | `BroadcastReceiver` | 开机和包更新后，有配置时自动重启服务 |

## 完整数据流

```
┌─────────────────────────────────────────────────────────────┐
│                       系统短信框架                            │
│  android.provider.Telephony.SMS_RECEIVED (有序广播)           │
└────────────────────────┬────────────────────────────────────┘
                         │ (动态注册，RECEIVER_EXPORTED)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  SmsReceiver.onReceive()                                     │
│                                                             │
│  1. 解析 SmsMessage[]，拼接 body，取 originatingAddress      │
│  2. 读 KEY_FORWARD_ALL                                       │
│     ├─ true  → 直接进入第 3 步                               │
│     └─ false → isOtpMessage()                               │
│                 ├─ 含 4-8 位数字 AND 关键词 → 进入第 3 步     │
│                 └─ 不满足 → return（丢弃）                    │
│  3. startForegroundService(ACTION_FORWARD, sender, body)     │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  ForwardService.onStartCommand(ACTION_FORWARD)               │
│                                                             │
│  1. wakeLock.acquire(30s)                                   │
│  2. Thread { TelegramForwarder.send(sender, body) }         │
│  3. saveLog(ForwardLog(timestamp, sender, body.take(30),    │
│             success))  → SharedPreferences KEY_LOGS         │
│  4. wakeLock.release()                                      │
└────────────────────────┬────────────────────────────────────┘
                         │ OkHttp POST
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  TelegramForwarder.send()                                    │
│                                                             │
│  POST https://api.telegram.org/bot{token}/sendMessage       │
│  Body: chat_id, text（📱 SMS Forwarder / 发件人 / 时间 / 内容）│
│  返回: HTTP 200 → true，异常/非 200 → false                  │
└─────────────────────────────────────────────────────────────┘
                         │ SharedPreferences 变更通知
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  MainActivity（OnSharedPreferenceChangeListener）            │
│  KEY_LOGS 变更 → 重新加载列表，UI 实时更新                    │
└─────────────────────────────────────────────────────────────┘
```

## 开机自启流程

```
设备开机 / App 更新完成
    └─> BootReceiver.onReceive()
            └─> Bot Token 非空？
                    ├─ 是 → startForegroundService(ForwardService)
                    └─ 否 → 不启动（等用户配置后手动开启）
```

## SharedPreferences 结构

所有数据存于 `"sms_forwarder_prefs"`（`MODE_PRIVATE`）：

| 键 | 值类型 | 说明 |
|----|--------|------|
| `bot_token` | String | Telegram Bot Token |
| `chat_id` | String | Telegram Chat ID |
| `forward_all` | Boolean | 转发所有短信开关（默认 false）|
| `forward_logs` | String (JSON Array) | 最近 20 条 `ForwardLog` 序列化结果 |

`ForwardLog` 结构：
```kotlin
data class ForwardLog(
    val timestamp: Long,   // System.currentTimeMillis()
    val sender: String,    // 发件人号码
    val message: String,   // 短信内容前 30 字
    val success: Boolean   // Telegram 发送是否成功
)
```
