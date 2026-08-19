# MC 基岩版材质编辑器（Termux 修复版）

这是仅针对 Minecraft Bedrock Edition 的民用版材质编辑器工程。

本修复版重点解决：
- 启动 Activity 使用完整类名，避免 Manifest 解析歧义
- 使用原生 `android.app.Activity`，移除 AppCompat 外部依赖
- AGP 8.6.1 + Gradle 8.10.2 固定组合
- Debug/Release 都关闭代码压缩，避免启动类被错误裁剪
- Termux 构建脚本自动准备 Java / Android SDK / Gradle
- 提供 `verify_apk.sh`，编译后检查 `MainActivity` 是否真的进入 DEX

### Termux 一键编译

```bash
cd "/storage/emulated/0/你的工程目录"
bash install_and_build.sh
```

如果 SDK 已经准备好：

```bash
bash build_termux.sh
```

APK 会输出到：

```text
output/MC材质编辑器-debug.apk
```

编译后可执行：

```bash
bash verify_apk.sh output/MC材质编辑器-debug.apk
```
