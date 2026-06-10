# 开发进度

## 已完成

### 核心转发链路
- [x] `SmsReceiver` 动态注册（`ForwardService.onCreate()`），监听 `SMS_RECEIVED` 广播（`RECEIVER_EXPORTED`，API 26+ 兼容）
- [x] `ForwardService` 前台服务常驻，`PARTIAL_WAKE_LOCK` 保证网络请求不被中断
- [x] `TelegramForwarder` 通过 OkHttp POST Telegram Bot API，Bot Token / Chat ID 存 SharedPreferences
- [x] `ForwardService.onDestroy()` 注销 `SmsReceiver`

### OTP 过滤
- [x] 关键词列表：`验证码`、`验证`、`码`、`动态密码`、`一次性密码`、`code`、`otp`、`verify`、`verification`、`activation`、`password`
- [x] 过滤逻辑：关键词 **AND** 4–8 位数字同时匹配才转发
- [x] "转发所有短信"开关，开启后跳过 OTP 过滤，持久化到 SharedPreferences

### 转发记录
- [x] `ForwardLog`（`timestamp`、`sender`、`message` 前 30 字、`success`）
- [x] SharedPreferences JSON 存储，最多 20 条，按时间倒序
- [x] `OnSharedPreferenceChangeListener` 实时更新 MainActivity 列表，重启后恢复

### 消息格式
- [x] Telegram 消息格式：`📱 SMS Forwarder / 发件人 / 时间（yyyy-MM-dd HH:mm:ss）/ 内容`
- [x] 纯文本（无 Markdown），避免特殊字符转义问题

### 可靠性 / 生命周期
- [x] `BootReceiver` 监听 `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`，Bot Token 非空时自动启服务
- [x] `MainActivity.onCreate()` 检测 Bot Token，有配置则自动启服务
- [x] 电池优化白名单检测 Banner（`PowerManager.isIgnoringBatteryOptimizations`），从系统设置返回后自动刷新

### 权限
- [x] 运行时申请：`RECEIVE_SMS`、`READ_SMS`、`POST_NOTIFICATIONS`（API 33+）
- [x] Manifest 声明：`FOREGROUND_SERVICE_DATA_SYNC`（API 34+）、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

### 通知
- [x] NotificationChannel `IMPORTANCE_HIGH`，`NotificationCompat.PRIORITY_HIGH`，解决 "Suppressing notification" 压制问题

---

## 待完成

### 稳定性验证
- [ ] **长时间后台保活测试**：运行 12h+ 确认服务不被系统杀死
- [ ] **各品牌厂商兼容性**：小米（MIUI/HyperOS）、华为（EMUI/HarmonyOS）、OPPO（ColorOS）、vivo（OriginOS）的电池优化策略各不相同，需逐一验证
  - 目前仅在 TECNO LJ9 Android 15（API 35）验证通过

### 已知缺陷
- [ ] **服务状态 UI 不同步**：`serviceRunning` Switch 状态在 Activity 重建后重置，不反映实际服务运行状态（需绑定服务或用 ActivityManager 查询）
- [ ] **API 34+ 冷启动风险**：`dataSync` 类型前台服务从后台启动在 Android 14+ 受限，若服务未运行时来短信，`SmsReceiver` 调用 `startForegroundService` 可能失败

### 功能扩展
- [ ] 多转发通道支持（邮件 SMTP、短信回传等）
- [ ] 转发规则自定义（白名单号码、自定义关键词）
- [ ] 转发失败重试机制
- [ ] UI 视觉优化（深色模式适配、转发统计数字）
- [ ] Widget 或快捷方式：一键开关转发服务
