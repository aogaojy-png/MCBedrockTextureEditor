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
# MC Bedrock Texture Editor

A mobile tool for creating Minecraft Bedrock Edition resource packs. Supports custom textures, subpack switching, and independent toggles.

---

## 📌 Important Notice / 重要声明

**This software is licensed under AGPL-3.0 with additional restrictions:**

- ❌ **Commercial use is strictly prohibited** (including but not limited to sales, paid distribution, advertising revenue, in-app purchases, commercial services, etc.).
- ❌ This software may not be used as a core component of any commercial product or service.
- ❌ Do not remove or alter copyright notices.
- ✅ Personal learning, research, modification, and non-commercial sharing are permitted (with copyright notice retained).
- 📧 For commercial use, please contact the author for written authorization.

**本软件采用 AGPL-3.0 许可证，并附加以下限制：**

- ❌ **禁止将本软件或其任何修改版本用于商业用途**（包括但不限于销售、付费分发、广告收入、内购、商业服务等）。
- ❌ 禁止将本软件作为任何商业产品或服务的核心组件。
- ❌ 禁止移除或篡改版权声明。
- ✅ 允许个人学习、研究、修改和非商业性分享（需保留版权声明）。
- 📧 如需商业使用，请联系作者获得书面授权。

---

## Features

- Add custom textures for items, blocks, entities, UI, and sky.
- Organize textures into subpacks (switchable in-game).
- Create independent toggles (settings) for specific features.
- Import existing resource packs to edit textures.
- Export as .mcpack file for Minecraft Bedrock Edition.

## License

AGPL-3.0 + Additional Non-Commercial Restriction. See [LICENSE](LICENSE) for details.
