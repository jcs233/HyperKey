# 贡献与反馈

[项目首页](README.md) · [实现说明](docs/IMPLEMENTATION.md) · [验证记录](docs/VALIDATION.md)

## 报告问题或补充兼容性

可以在 [Issues](https://github.com/jcs233/HyperKey/issues) 新建反馈。一个能复现的问题通常只需要：

1. 设备和键盘型号、HyperOS 完整版本、Android 版本、LSPosed 和 HyperKey 版本；专属页面问题请补充系统安全组件版本。
2. 出问题的入口（通用键盘、专属键盘、模块首页或具体应用）、键盘焦点在哪里。列出总禁用、相关单项和应用规则的状态即可。
3. 按键顺序、预期与实际结果。例如：「仅在远程客户端启用 Win 透传；按住 Win、按 D、依次松开；平板回到了桌面。」请区分本机行为与实际远端结果。
4. 如有缺失 Hook，附本次启动的状态或一小段相关错误。日志或截图先[脱敏](docs/PRIVACY.md)，无需上传设备镜像或整个日志文件。

## 修改与验证

从 `main` 创建分支。核心配置和优先级在 `app/src/main/java/io/github/hyperkey/core/`，系统拦截与设置集成在同级 `hook/`，独立界面在 `ui/`。新增 UI 文案请同时更新默认英文和 `values-zh-rCN` 的 Android 字符串资源。

需要 JDK 17 和 Android SDK Platform 36；使用仓库自带的 Gradle Wrapper。在根目录运行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows PowerShell 使用 `.\gradlew.bat` 代替 `./gradlew`。单元测试覆盖配置、名单、映射和按键展示；编译成功不能证明新固件的系统 Hook 已适配。

- 更改规则优先级时，检查全局总禁用、应用覆盖和继承；更改映射时，检查同一次按键从按下到松开的结果。
- 更改设置集成时，在通用与专属原生编辑页各切换一次同一单项，并检查另一处是否同步。更改系统 Hook 时，请说明是否使用实体键盘实测，不要用虚拟 `adb shell input keyevent` 代替实测结论。
- PR 描述写明原来如何复现、修改后的行为、验证环境和未测试的部分。只提交与改动相关的源码、测试或说明。请勿提交 APK、签名密钥、`local.properties`、设备提取物、日志、构建缓存或个人截图。

仓库未附项目许可证；请勿提交无权分享的第三方代码、固件或资源。
