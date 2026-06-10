# 开发环境配置

## 工具要求

| 工具 | 版本 |
|------|------|
| Android Studio | Meerkat 2024.3.2+（推荐最新稳定版）|
| Android SDK | API 35（Build Tools 35+）|
| JDK | 11（项目 `compileOptions` 设定）|
| Gradle | 9.4.1（`gradle-wrapper.properties` 指定）|
| AGP | 9.2.1 |
| Kotlin | 2.2.10 |

## 克隆与构建

```bash
# 克隆项目
git clone <repo-url>
cd SMSForwarder

# 构建 Debug APK
./gradlew assembleDebug

# 安装到已连接设备
./gradlew installDebug
```

## Gradle 阿里云镜像（国内网络加速）

在项目根目录 `settings.gradle.kts` 的 `dependencyResolutionManagement` 块中替换仓库地址：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}
```

在 `build.gradle.kts`（根目录）的 `pluginManagement` 块中同步替换：

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}
```

## 关键依赖版本（`gradle/libs.versions.toml`）

```toml
[versions]
kotlin        = "2.2.10"
composeBom    = "2026.02.01"
okhttp        = "4.12.0"
gson          = "2.11.0"
```

## 运行前配置

1. 打开 App → 输入 **Bot Token** 和 **Chat ID** → 点击 Save
2. 允许弹出的权限对话框（RECEIVE_SMS、READ_SMS、POST_NOTIFICATIONS）
3. 点击顶部 Banner 的"去设置"，将 App 加入**电池优化白名单**
4. 开启"Forwarding Service"开关

## 调试

```bash
# 过滤 App 日志
adb logcat -s SMSForwarder

# 关键日志标记
# ForwardService created, registering SmsReceiver  — 服务启动
# SmsReceiver.onReceive triggered                  — 广播到达
# SMS received from: <sender>, body: <body>        — 短信解析
# forwardAll=false, isOtp=true/false               — 过滤结果
```

## 已验证设备

| 设备 | Android 版本 | API |
|------|-------------|-----|
| TECNO LJ9 | Android 15 | 35 |
