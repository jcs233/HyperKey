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

**[下载 HyperKey 0.3.2 正式 APK（2.33 MiB）](https://github.com/jcs233/HyperKey/releases/download/v0.3.2/HyperKey-0.3.2.apk)** · [发布说明与 SHA-256 校验文件](https://github.com/jcs233/HyperKey/releases/tag/v0.3.2)

- 在模块首页、通用实体键盘或小米专属键盘设置中可打开「屏蔽触控板三指下滑截屏」。这个开关默认关闭，独立于键盘总开关。它只针对触控板上的三指下滑全屏截屏，不改变屏幕手势或键盘截屏的设置。
- [108 键选键器](docs/USAGE.md#按键映射)补充了 Fn。来源和目标都支持多个普通键加修饰键，可用键帽点选或实体键录入。比如按住 A、B，最后按 C 触发 `A+B+C`；前置键仍会送给应用。若键盘固件自行处理 Fn，只能绑定 Android 实际收到的键。

0.3.2 已通过 40 项单元测试。上方平板实测记录来自较早版本；新版 Fn 上报、多键映射和触控板截屏拦截尚待目标平板验证。详见[验证记录](docs/VALIDATION.md)。

**安装或升级：** 0.3.2 沿用 0.3.0 起的项目测试版签名。

| 已安装的版本 | 怎么做 |
| --- | --- |
| 尚未安装 | 下载 APK 安装，按下方「启用模块」配置。 |
| GitHub 0.3.0、项目分发的 0.3.1 或其他同签名旧版 | 可直接覆盖安装，或运行 `adb install -r HyperKey-0.3.2.apk`。安装后重启平板以加载新版系统 Hook。 |
| GitHub 0.2.0 正式版，或使用旧正式证书的 0.2.1 | 先记下需要保留的配置，再卸载旧版、安装新版。卸载会清除应用私有数据；重新检查三个 LSPosed 作用域，按需重新授权并重启平板。 |

自行构建的调试包能否覆盖安装取决于实际证书。可用 `apksigner verify --print-certs` 对照发布说明中的证书指纹。

## 从源码安装（可选）

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

## 启用模块

1. 在 LSPosed 中启用 HyperKey，勾选以下三个作用域，然后**重启平板**。更新含系统 Hook 的版本后也要重启。

   | 作用域 | 标识 | 用途 |
   | --- | --- | --- |
   | 系统框架 | `system` | 处理实体键盘输入 |
   | 设置 | `com.android.settings` | 通用键盘和专属键盘设置入口 |
   | 系统安全组件 | `com.miui.securitycore` | 专属键盘的原生「键盘快捷键」编辑页 |

2. **只在系统设置调全局开关的用户，到这里就可以使用。** 若要从 HyperKey 独立界面写入全局配置、应用名单或映射，再授权一次。即使从系统设置进入，名单和映射编辑器也属于 HyperKey。

   ```sh
   adb shell pm grant io.github.hyperkey android.permission.WRITE_SECURE_SETTINGS
   ```

**首次检查：** 接上实体键盘，打开 HyperKey 的运行状态，确认系统拦截已加载。先记下「回到桌面」原来的状态，在通用设置里切换它，再到专属键盘的「键盘快捷键」页核对。检查后可以改回原值。在普通应用里验证快捷键；按键测试页会专门接收 Win，行为不同。

## 更多说明

- [使用手册](docs/USAGE.md)：两个设置入口、规则优先级、远程桌面、按键映射、配色，以及 0.2.1 起的隐藏与恢复桌面图标。
- [故障排查与停用](docs/TROUBLESHOOTING.md)：开关不同步、无法保存、Win 被拦截、签名不一致和停用后的恢复。
- [隐私与权限](docs/PRIVACY.md)：为什么需要写入系统设置，应用列表和按键测试数据如何处理。
- [实现说明](docs/IMPLEMENTATION.md)与[实机验证记录](docs/VALIDATION.md)：适配逻辑、已验证行为和测试边界。
- [贡献指南](CONTRIBUTING.md)：构建检查、报告新固件和提交修改。

本仓库尚未附项目许可证。第三方运行时依赖及许可证已随 APK 打包，见[第三方清单](app/src/main/assets/third_party/NOTICE.txt)。
