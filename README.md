# HyperKey

[English](README.en.md) · [使用手册](docs/USAGE.md) · [故障排查](docs/TROUBLESHOOTING.md) · [实机记录](docs/VALIDATION.md)

HyperKey 让你决定小米平板的实体键盘快捷键在哪里生效。例如，平常按 `Win+D` 仍回到平板桌面，打开远程桌面客户端时则把 Win 键交给客户端。你可以直接在 HyperOS 的**通用实体键盘**和**小米平板专属键盘**设置里操作，也可以打开独立的 Miuix 模块界面。

| 想做什么 | 从哪里开始 |
| --- | --- |
| 临时关闭所有系统快捷键，同时保留原来的选择 | 打开「关闭所有系统快捷键」，用完再关掉 |
| 只关闭截屏、任务栏或其他特定操作 | 在任一键盘设置入口调整对应单项；共 19 项 |
| 给不同软件分配不同的快捷键 | 「应用名单」→ 选应用图标 → 设置单项、Win 透传或专属映射 |
| 把 Caps Lock 当作 Ctrl，或用新组合键截屏 | 「按键映射」→ 录入来源 → 选择目标按键或系统动作 |
| 看清系统交给应用的键值 | 「按键测试」显示键帽、Android 键值、扫描码和修饰键状态 |

## 兼容性与下载

**已验证：** Xiaomi Pad 8 Pro（`piano`），HyperOS `OS4.0.5.0.XPYCNXM`，Android 17 / API 37，LSPosed 2.2.1 (7944)，系统安全组件 `4.4.4.3-4-260914.pad`。模块使用 libxposed API **102.0.0**。小米在不同固件上可能调整键盘实现，其他设备或版本的适配情况请以[验证记录](docs/VALIDATION.md)为准。

仓库提供 **0.2.0 源码**，目前没有发布可直接下载的 APK。以下步骤会在自己的电脑上生成调试签名的安装包。

## 从源码安装

1. 准备 JDK 17、Android SDK Platform 36、Android SDK Platform-Tools（含 `adb`），以及已安装、可用且支持 API 102 的 LSPosed 环境。将 Android SDK 路径配置在 `ANDROID_HOME` 或本机的 `local.properties` 中。
2. 克隆仓库，在项目根目录构建。首次构建需要联网下载依赖。

   ```sh
   git clone https://github.com/jcs233/HyperKey.git
   cd HyperKey
   ```

   Windows PowerShell：`.\gradlew.bat :app:assembleDebug`；macOS/Linux：`./gradlew :app:assembleDebug`。

3. 确认 `adb devices` 显示目标平板为 `device`，然后安装：

   ```sh
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. 在 LSPosed 中启用 HyperKey，勾选以下三个作用域，然后**重启平板**。更新含系统 Hook 的版本后也要重启。

   | 作用域 | 标识 | 用途 |
   | --- | --- | --- |
   | 系统框架 | `system` | 处理实体键盘输入 |
   | 设置 | `com.android.settings` | 通用键盘和专属键盘设置入口 |
   | 系统安全组件 | `com.miui.securitycore` | 专属键盘的原生「键盘快捷键」编辑页 |

5. **只在系统设置调全局开关的用户，到这里就可以使用。** 若要从 HyperKey 独立界面写入全局配置、应用名单或映射，再授权一次。即使从系统设置进入，名单和映射编辑器也属于 HyperKey。

   ```sh
   adb shell pm grant io.github.hyperkey android.permission.WRITE_SECURE_SETTINGS
   ```

**首次检查：** 接上实体键盘，打开 HyperKey 的运行状态，确认系统拦截已加载。先记下「回到桌面」原来的状态，在通用设置里切换它，再到专属键盘的「键盘快捷键」页核对。检查后可以改回原值。在普通应用里验证快捷键；按键测试页会专门接收 Win，行为不同。

## 更多说明

- [使用手册](docs/USAGE.md)：两个设置入口、规则优先级、远程桌面、按键映射和配色。
- [故障排查与停用](docs/TROUBLESHOOTING.md)：开关不同步、无法保存、Win 被拦截、签名不一致和停用后的恢复。
- [隐私与权限](docs/PRIVACY.md)：为什么需要写入系统设置，应用列表和按键测试数据如何处理。
- [实现说明](docs/IMPLEMENTATION.md)与[实机验证记录](docs/VALIDATION.md)：适配逻辑、已验证行为和测试边界。
- [贡献指南](CONTRIBUTING.md)：构建检查、报告新固件和提交修改。

本仓库尚未附项目许可证。第三方依赖各自适用其上游许可证。
