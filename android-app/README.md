# Eligo Android 离线打包工程

## 当前方案

WebView 套壳方式：
- 启动后用 WebView 加载 `assets/h5/index.html`（H5 构建产物内置 APK）
- 所有 UI 资源离线可用，无需网络就能跑起适老化界面
- `/api/*` 请求在 WebView 层被拦截返回 mock 数据，不打扰后端
- 微信登录入口通过 `EligoBridge.loginWithWechat()` 暴露给 H5：当前会弹原生对话框告知"开发模式跳过"，后续接入微信 SDK 时只需替换 `WechatBridge` 实现

## 目录结构

```
android-app/
├── settings.gradle
├── build.gradle              # 根 build
├── gradle.properties
├── sync-assets.ps1           # 同步 H5 产物到 assets
├── build-apk.ps1             # 一键构建 H5 + APK
├── README.md
└── app/
    ├── build.gradle          # 模块 build
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/eligo/app/MainActivity.kt
        ├── assets/h5/        # H5 产物（git ignore，由 sync-assets.ps1 生成）
        └── res/
            ├── values/{strings,colors,themes}.xml
            ├── drawable/ic_launcher_foreground.xml
            ├── mipmap-anydpi-v26/ic_launcher*.xml
            └── xml/network_security_config.xml
```

## 用 Android Studio 打开（推荐）

1. 安装 Android Studio（Hedgehog 2023.1.1 或更新版本）
   下载：https://developer.android.com/studio
2. 启动 Android Studio → "Open" → 选择 `android-app` 目录
3. 等待 Gradle sync 完成（首次会下载 Gradle 8.7 和 Android SDK）
4. 选择模拟器或真机（开发者模式 + USB 调试）
5. 点 Run（Shift+F10）→ APK 安装到目标设备并自动启动

## 命令行打包

前置：本机已装 JDK 17+ 和 Android SDK（设好 `ANDROID_HOME` 环境变量）

首次需要生成 gradle wrapper：
```powershell
cd android-app
gradle wrapper --gradle-version 8.7
```

之后每次重新打包（自动构建 H5 + 同步 + 打 APK）：
```powershell
.\build-apk.ps1
```

APK 输出在：`app/build/outputs/apk/debug/app-debug.apk`

## 安装到老人机

1. 用 USB 连接老人机，老人机开启"开发者模式 → USB 调试"
2. 在 PowerShell 里执行：
   ```powershell
   adb install -r "C:\path\to\android-app\app\build\outputs\apk\debug\app-debug.apk"
   ```
3. 或直接把 APK 文件传到老人机，文件管理器点击安装

## 后续接入微信登录

当前 `WechatBridge.loginWithWechat()` 只弹框提示，不真起微信。
真正接入步骤：

1. 在 [微信开放平台](https://open.weixin.qq.com/) 注册应用，拿到 AppID（与小程序 AppID 不同，是开放平台的）
2. 下载微信开放平台 Android SDK（`wechat-sdk-android-with-mta-x.y.z.aar`）
3. 把 aar 放到 `app/libs/`，在 `app/build.gradle` 加：
   ```gradle
   dependencies {
       implementation files('libs/wechat-sdk-android-with-mta-6.8.28.aar')
   }
   ```
4. 在 `AndroidManifest.xml` 加 `WXEntryActivity`：
   ```xml
   <activity
       android:name=".wxapi.WXEntryActivity"
       android:exported="true"
       android:launchMode="singleTask">
       <intent-filter>
           <action android:name="android.intent.action.VIEW"/>
           <category android:name="android.intent.category.DEFAULT"/>
           <data android:scheme="t eyewitnessYOUR_OPEN_APPID"/>
       </intent-filter>
   </activity>
   ```
5. 在 `WechatBridge` 中替换弹框为真实 `IWXAPI.sendReq()`，并在 `WXEntryActivity` 接收回调把 code 回传 WebView。

## 已知限制（当前阶段）

- 不支持原生组件（map、相机等）——H5 模式下为模拟实现
- API 调用全部被拦截返回 mock，无后端联调
- 微信登录走占位弹窗
- 适老化 UI 已生效，可用于真机看效果
