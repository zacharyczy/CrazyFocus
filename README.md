# CrazyFocus

**No scrolling on Android.** CrazyFocus interrupts short-video feeds in WeChat Channels and Bilibili Story Mode while keeping ordinary articles and video playback available. It supports Android 10 and later.

## Downloads · 下载

| APK | Edition | SHA-256 |
| --- | --- | --- |
| [CrazyFocus-always.apk](https://github.com/zacharyczy/CrazyFocus/releases/download/v1.3.0/CrazyFocus-always.apk) | 常驻版 · Always-on | `e66a43690708d03dc588a7e4d8c2fbf9f57b497985d58d52a4d8f71e0f7ee1d2` |
| [CrazyFocus-toggle.apk](https://github.com/zacharyczy/CrazyFocus/releases/download/v1.3.0/CrazyFocus-toggle.apk) | 开关版 · Toggle | `4eda0f8cff3f405573e67626b49e097f4499ce91677c2c1976faf4995b44f0d4` |

[Project page and direct downloads](https://crazyczy.com/projects/crazyfocus/) · [项目详情与站内下载](https://crazyczy.com/projects/crazyfocus/zh/)

## Install and update

1. Choose one APK and open it on your Android phone. Follow Android’s prompts to install it.
   - **Always-on** works whenever the accessibility service is enabled. You can revoke access in Android Settings.
   - **Toggle** offers on, off, or a daily schedule. Protection starts enabled and the initial schedule is 22:00–07:00.
2. Open CrazyFocus, tap the shortcut to Accessibility settings, and enable CrazyFocus. If Android restricts the setting, open CrazyFocus’s app info menu and allow restricted settings first.
3. If protection stops when the app goes into the background, use the app info shortcut in CrazyFocus to check your phone’s battery or background settings.
4. Install updates over the existing app when you stay on the same edition. To switch editions, uninstall the current one, install the other APK, and repeat setup. Both editions share the same app ID and have separate signatures.

## 中文说明

适用于 Android 10 及以上。微信视频号可以观看当前视频，确认纵向滑动换片后退出。哔哩哔哩在确认进入 Story Mode 竖屏流时退出。

两版在桌面、应用信息和无障碍设置中都显示为 CrazyFocus。首页跟随系统的浅色或深色模式，顶部显示限制状态。

## 安装

1. 从本页下载一个 APK（自行构建的文件位于 `dist`），复制到手机，使用系统文件管理器点开安装。按系统提示允许当前安装来源。
   - `CrazyFocus-always.apk` 是常驻版。无障碍授权有效时持续拦截，系统设置可撤销授权。
   - `CrazyFocus-toggle.apk` 是开关版。选择「开启」「关闭」或「按时段」。默认开启，初始时段为每天 22:00 至次日 07:00，可以修改。
2. 打开 CrazyFocus，点「打开无障碍设置」，找到 CrazyFocus 并启用。如果系统提示「受限制的设置」，从 CrazyFocus 的应用信息菜单中选择「允许受限制的设置」，再回到无障碍页面授权。菜单名称因厂商而异。[Android 授权说明](https://support.google.com/android/answer/12623953?hl=zh-Hans)
3. 如果换到后台后拦截偶尔停用，请在 CrazyFocus 的「后台运行」卡片点「打开应用信息」，或从系统设置打开「应用 → CrazyFocus」。查找「电池」「应用耗电量」或「省电策略」，按系统提供的选项允许后台运行。部分手机的选项叫「不受限制」或「无限制」。[Android 后台运行说明](https://developer.android.com/topic/performance/background-optimization)
4. 返回首页确认限制状态。两版使用同一应用 ID、各自保留原签名。同版可以直接覆盖安装，换版前先卸载，换版后重新授权和设置。

时段开始时刻含在生效范围内，结束时刻起停止。结束早于开始时，时段跨午夜。定时随手机当地时间生效，配置保存在手机内。

## 工作方式

应用只识别 `com.tencent.mm` 和 `tv.danmaku.bili` 的目标播放器。微信规则要求识别到全屏视频分页列表，并通过纵向位置或停稳的分页索引确认滑动后的换片。连续滑动时会等页面停稳。哔哩哔哩规则由已识别的 Story Mode 页面触发。退出前会核对焦点窗口。文章、搜索、普通播放器、在线会议、分屏和无法确认的页面照常运行。

检测使用 Android 无障碍接口。应用没有申请联网、录屏、录音或存储权限。播放信息只在判断过程中保留在内存里，手机上持久保存的只有生效设置。第三方应用更新或设备系统差异可能改变可用的界面信号，信号不足的入口会放行。

## 1.3.0 验证记录

- Android 10、13、16 原生模拟器分别验证了两版的同版覆盖安装、无障碍授权保留、浅色和深色界面、大字体下滚动访问设置，以及从首页打开系统应用信息。开关版覆盖更新后保留已选的跨午夜时段。
- 三个模拟器上的两种调试版均验证了 Story Mode 示例页的退出规则，开关版在关闭时保留示例页。
- Xiaomi Civi 4 Pro（Android 16、微信 8.0.80、哔哩哔哩 9.17.0）验证了开关版覆盖安装并确认新版服务运行。哔哩哔哩进入竖屏视频流后退出，微信从公众号进入视频号并滑动换片后退出。抽测的普通公众号文章可以滚动阅读，B 站普通视频详情页可以正常打开。
- 真机复测使用了公众号推荐页入口。当前这台手机关闭了微信的独立视频号入口。其他品牌暂以原生模拟器覆盖通用设置流程。

## 从源码构建

准备 JDK 17、Android SDK 36 和 Build Tools 35.0.0，并分别设置 `JAVA_HOME` 和 `ANDROID_HOME`。在项目目录中执行以下命令。

```powershell
.\gradlew.bat :app:testToggleDebugUnitTest :app:testAlwaysDebugUnitTest
.\build-release.ps1
```

首次构建为两个版本分别生成密钥，位于 `%LOCALAPPDATA%\ShortVideoBlocker\signing`。密码由当前 Windows 用户的 DPAPI 保护。后续更新需要对应版本的原密钥和密码文件。脚本构建并验证签名后将 APK 写入 `dist`。

调试版本可单独构建。`StorySampleActivity` 是调试版里的示例分页器，用于在模拟器上验证进入 Story Mode 时的退出以及开关行为。`PagerProbe` 在单独的测试 APK 中输出指定播放器的界面结构与滚动索引，排除可见文字内容。
