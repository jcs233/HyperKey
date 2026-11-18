# HyperKey 实现方案

## 按应用名单（0.2.0）

`hyperkey_app_profiles_v1` 单条 Secure 设置保存不可变的包名映射；每项包含启用状态、覆盖位和禁用位。未覆盖位继承全局最新值，最高优先级的全局 blockAll 保留。解析拒绝未知版本、重复包名、未知位和超限内容，损坏时回退全局。

RuntimeConfig 通过窗口焦点通知缓存包名，ContentObserver 更新全局和名单快照；按键路径没有数据库查询或前台进程轮询。分发线程使用 ThreadLocal 快照，KeyRoute 为相同设备/键值/downTime 保存规则，按下、重复和抬起保持一致。

原生 manager 启动和队列中的规则变更后，补齐已禁用的已知快捷键规则，仅保存在内存中。MiuiKeyboardShortcutInfo.isEnable 只在按键分发上下文中读取应用覆盖；存盘、原生列表和同步仍读取原生值。shouldInterceptKeys 排除仍禁用的规则，避免补齐规则吞掉按键。应用切换不触发原生 XML 写入。

名单 UI 使用 Compose Miuix，应用枚举只查询 MAIN/LAUNCHER，无 QUERY_ALL_PACKAGES 权限。可以手动添加包名；暂停、删除和逐项继承不影响全局配置。两个系统设置宿主提供名单入口，原有单项继续编辑全局默认值。

## 映射、透传与外观扩展（0.2.0）

`hyperkey_mappings_v1` 保存全局/包名所有者、来源键和修饰、目标键和修饰或系统动作。最多 256 条，重复来源替换，应用规则先于全局规则。系统动作调用固件原生 ShortCutActionsUtils；只触发按下首帧，总禁用期间不执行动作映射。

InputManagerService 最早 queueing 入口拦截映射来源并异步注入成对目标事件；当前按键的目标在抬起前保持不变。维护修饰键映射的按下状态，为同设备后续键调整 meta。输出事件使用签名表识别并在 queueing/dispatching/unhandled 阶段放行，不递归。不会安装/替换系统 InputFilter 或无障碍服务。

原始事件透传在 InputManagerService 入口直接放行，避免只禁止动作但后续 AOSP/厂商分发仍吞掉 Meta。应用配置的 rawWin 标志、总禁用或全部任务键禁用可启用此行为；在 KeyRoute 中固定按下到抬起的判定。

图标通过 PackageManager 在后台线程读取并按显示密度栅格化，支持自适应图标及缺失占位。应用列表不请求 QUERY_ALL_PACKAGES。

语言使用英文默认和 values-zh-rCN，模块 Activity 在 attachBaseContext 中应用语言选择。宿主使用模块资源的宿主语言配置副本；不更改宿主语言。主题通过统一 ThemedActivity/HyperTheme 应用 Miuix 明暗配色及强调色，SharedPreferences 保存语言、模式和颜色；应用重建时保留名单编辑与导航状态。

## 目标

为 HyperOS 实体键盘提供总开关和独立快捷键开关；在系统设置中直接操作，模块主页使用开源 Compose Miuix。关闭表示系统不执行快捷键，并尽量将原始按键传给前台应用。应用内部 Ctrl+C 等编辑快捷键不属于系统快捷键。

## 已核实的基线

- 设备：Xiaomi Pad 8 Pro / piano / 25091RP04C。
- 系统：OS4.0.5.0.XPYCNXM，Android 17，API 37。
- 项目固定使用 libxposed API 102.0.0。使用无参数 XposedModule、onSystemServerStarting、onPackageReady 和 HookBuilder/Chain，禁止混用旧版 XposedBridge。

## 分阶段实施

1. 提取当前固件 Settings.apk、miui-services.jar、services.jar；定位真实方法，记录兼容范围。
2. 建立 API 102 工程、独立快捷键目录、原子配置和纯逻辑测试。
3. system_server：组合键规则入口、MIUI 按键策略、Meta 通知、Alt+Tab 分别拦截；保持按下/抬起一致。
4. 设置：在实体键盘页面注入宿主原生 miuix 风格偏好控件；对旧版键盘快捷键 Fragment 同样提供开关。动态重建页面后避免重复插入。
5. 模块主页：Compose Miuix、总开关、分组独立开关、恢复默认、运行状态。两处使用同一份每用户配置。
6. 编译、单元测试、lint、检查 APK 元数据与 API 不打包；无线 ADB 安装及界面检查。只有日志证实 Hook 安装且实测按键后才标记完整通过。

## 配置与权限

配置以单条版本化字符串存储在 Settings.Secure，避免多个界面维护不同副本。系统设置自带写入权限；独立模块界面需要 ADB 授予 WRITE_SECURE_SETTINGS。配置变更监听后即时更新。

原生 type 0–14 还存于系统快捷键库。首次加载导入原生启用状态，保留模块的任务键、Esc 和总开关；单项修改通过系统自带 updateKeyboardShortcut MODIFY 接口同步，仅修改 active 和必要的 history 字段，保留组合键、包名、类名、自定义标记。不直接编辑系统 XML，不调用恢复原生默认组合键。系统原生修改通过同一接口的 Hook 反向导入。原生列表指纹变化才发布刷新通知，避免原生控件绑定时的重复保存造成刷新循环。

总开关独立拦截，不批量覆盖原生单项选择；单项在总开关开启时保留勾选并禁用操作。打开应用是聚合开关，关闭/启用会统一修改所有现有应用快捷键的启用状态。

## 设备上确认的 Hook 点

- com.android.server.policy.KeyboardCombinationRule.shouldInterceptKeys(int,int) / execute()：type 0–14。
- com.android.server.policy.MiuiKeyInterceptExtend.getKeyInterceptTypeBeforeQueueing / BeforeDispatching：返回 1 表示将按键传给应用。
- com.android.server.policy.BaseMiuiPhoneWindowManager.handleAltTab(KeyEvent)：独立 Alt+Tab 状态机。
- com.android.server.input.InputManagerServiceStubImpl.notifyKeyboardShortcutKey(String,String)：KEY_META 的 SINGLE_CLICK/DOUBLE_CLICK。
- BaseMiuiPhoneWindowManager.showKeyboardShortcutsMenu：长按 Meta 的快捷键面板。
- com.android.settings.stylus.MiuiPhysicalKeyboardFragment：系统实体键盘页面。当前 APK 仍保留旧快捷键 Fragment 的 XML 引用，但该类不在 APK 中，因此不能仅依赖旧类名。
- 通用入口：com.android.settings.inputmethod.PhysicalKeyboardFragment。
- 平板专属键盘入口：com.android.settings.inputmethod.LargeScreenKeyboardPadFragment。
- 真正的专属快捷键编辑页：com.miui.securitycore/com.miui.miinput.keyboard.MiuiKeyboardShortcutActivity，处于另一进程。通过具名 MiuiKeyboardCustomPreference 的 Fragment 字段发现当前原生编辑 Fragment；监听配置与原生列表版本，重新读取原生仓库。原有十项沿用系统控件，其余选项使用宿主 Miuix 控件补充。
- 原生同步：com.miui.server.input.util.MiuiCustomizeShortCutUtils.updateKeyboardShortcut；使用系统已有对象，保留原生结构。

## 作用域

API 102 的 system_server 作用域为 `system`，不是 Android System APK 的 `android`。模块推荐作用域为 `system`、`com.android.settings`、`com.miui.securitycore`。调整作用域和系统 Hook 后需重启。

## 构建验证

0.2.0：assembleDebug 成功，17 项单元测试通过；lintDebug 为 0 错误、18 警告、2 提示。最初版本的 7 项测试及后续实体键盘、页面同步与 Win 透传的验证结果见[实机验证记录](VALIDATION.md)。

## 参考

- https://github.com/libxposed/api/releases/tag/102.0.0
- https://github.com/libxposed/api/tree/102.0.0
- https://github.com/libxposed/example
- https://github.com/compose-miuix-ui/miuix/tree/v0.8.7

系统提取文件仅用于本机分析，位于忽略目录 device/；不随模块分发。
