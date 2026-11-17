# App Oops

[English](README.md) | **简体中文**

基于 **Shizuku** 与 **Material 3 Expressive** 构建的 Android 敏感权限审计与管理工具 —— 无需 Root 权限，即可在系统底层通过 `IAppOpsService` 精细管控各类应用的敏感行为，并提供实时拦截提醒、审计日志与社区规则订阅能力。

---

## ✨ 核心特性

- **免 Root 底层权限管控（Shizuku 特权 Shell）**
  - 借助 Shizuku 获取特权 Shell（`UID: 2000`）与 `IAppOpsService` 服务代理，无需 Root 即可安全调整应用的 AppOps 模式（`ALLOW` / `IGNORE` / `DENY` / `DEFAULT`）。
  - 涵盖四大类高风险权限：
    - **传感器与运动识别**：运动与身体传感器、高采样率传感器、后台运动传感器、健身与活动识别（有效屏蔽“摇一摇”广告探测）。
    - **剪贴板与隐私防追踪**：读取/写入剪贴板、读取已安装应用列表、使用情况统计。
    - **后台抑制与保流畅**：后台运行、任意后台活动、`WakeLock` 唤醒锁、开机自启。
    - **系统防护与防骚扰**：悬浮窗显示在其他应用上层、修改系统设置、全局通知监听。
- **实时拦截守护与多样化提醒**
  - 前台守护服务实时监听受控权限的拦截命中，并在审计日志与仪表盘展示拦截记录。
  - 支持 **Toast 提示**、**系统通知提醒**，以及适配 **Android 16+ 实时更新（Live Updates）** 的状态栏与锁屏高亮动态通知。
  - 支持 **临时允许权限**：可在拦截通知中一键限时放行，倒计时结束后自动恢复 `IGNORE` 拦截。
- **批量预设与社区规则**
  - 内置常用批量防追踪与省电预设模板（禁用加速度计/陀螺仪、拦截读写剪贴板、禁止读取应用列表、后台限制等），支持快捷筛选与批量应用。
  - 支持订阅 [GraceThings/app-oops-rules](https://github.com/GraceThings/app-oops-rules)（支持 GitHub 官方源、jsDelivr CDN 加速、Gitee 国内镜像及第三方规则仓库），自动匹配本机已安装应用。
  - 支持将已调整的权限配置导出为规则 JSON，并一键跳转 GitHub 提交 Issue，触发自动化工作流创建 PR 贡献社区规则。
- **Material 3 Expressive 视觉与多语言体验**
  - 支持动态取色（Monet）、多套精选色彩方案（翡翠玉绿、深邃海蓝、活力幻紫、温暖日落）与浅色 / 深色 / 跟随系统主题。
  - 原生支持 **跟随系统**、**English**、**简体中文**、**繁體中文（香港）**、**繁體中文（台灣）**、**日本語**、**한국어**、**Español**、**Français**、**Deutsch**、**Italiano**（兼容 Android 13+ 系统级应用语言 `LocaleManager` 与 Android 11–12 应用内持久化切换）。

---

## 📥 下载引导（Google Play 封闭测试）

App Oops 目前正在 **Google Play 封闭测试** 阶段，请使用**同一个 Google 账号**按以下步骤加入测试并下载安装：

1. **加入 Google 测试群组**：[https://groups.google.com/g/gracethings-testing-group](https://groups.google.com/g/gracethings-testing-group)
2. **在 Android 设备上参与测试并下载**：[https://play.google.com/store/apps/details?id=io.github.gracethings.appoops](https://play.google.com/store/apps/details?id=io.github.gracethings.appoops)
3. **或在网页端加入测试**：[https://play.google.com/apps/testing/io.github.gracethings.appoops](https://play.google.com/apps/testing/io.github.gracethings.appoops)

---

## 🛠️ 构建要求与编译

- **Min SDK**：Android 11（API 30）
- **Compile / Target SDK**：API 37
- **核心技术栈**：
  - Kotlin `2.1.10` • AGP `9.1.0`
  - Jetpack Compose BOM `2026.03.01` • Material 3 `1.5.0-alpha23`
  - Shizuku API / Provider `13.1.5` • HiddenApiBypass `4.3`

```bash
# 克隆仓库
git clone https://github.com/GraceThings/app-oops-android.git
cd app-oops-android

# 运行单元测试并构建 Debug APK
./gradlew testDebugUnitTest assembleDebug
```

Windows PowerShell 示例：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

---

## 🔗 相关链接

- **项目主页**：[https://github.com/GraceThings/app-oops-android](https://github.com/GraceThings/app-oops-android)
- **社区规则仓库**：[https://github.com/GraceThings/app-oops-rules](https://github.com/GraceThings/app-oops-rules)
- **开发者**：Grace Chan ([GraceThings](https://github.com/GraceThings))
