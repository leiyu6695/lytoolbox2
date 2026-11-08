# LY工具箱（LY Toolbox）

一个 Android 快捷动作工具：**录一个快捷键 → 绑定一串动作 → 随时随地一键执行**。

典型用法：

- 刷抖音/快手/Reels 时，不用手指划屏幕，轻点悬浮球就切下一个视频；
- 听歌时双击音量键，直接停止播放；
- 把"下一个视频"放到桌面快捷方式或 1×1 小组件，点一下就执行。

> 设计说明见 [`docs/设计方案.md`](docs/设计方案.md)。

---

## 功能一览

| 模块 | 说明 |
| --- | --- |
| 主界面 | 功能入口网格：悬浮窗 / 广告跳过 / 2 个预留位，下方"更多功能开发中..." |
| 设置页 | 权限状态、悬浮球开关与透明度、快捷键绑定列表（点首页「悬浮窗」进入） |
| 悬浮球 | 可拖动，支持单击 / 双击 / 长按 / 上滑 / 下滑 / 左滑 / 右滑 七种手势 |
| 音量键 | 音量+双击、音量−双击、两个音量键同时按（无障碍服务过滤按键） |
| 桌面入口 | 长按 App 图标出现 3 个静态快捷方式 + 1×1 桌面小组件 |
| 动作库 | 上/下滑视频、左右滑、点屏幕中心、播放暂停、停止播放、上/下一首、音量±、返回/桌面/最近任务、打开应用、打开网页、等待 |
| 动作序列 | 一条快捷键可串联多个动作，适合"暂停 → 返回 → 打开某 App"这类组合 |
| 配置 | 本地 SharedPreferences + JSON，无网络、无账号、无广告 |

## 目录结构

```
lytoolbox/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/ly/toolbox/
│   │   ├── LyApp.kt                     # Application / 通知渠道
│   │   ├── data/
│   │   │   ├── ActionStep.kt            # 动作类型 + 动作步骤
│   │   │   ├── Trigger.kt               # 触发方式枚举
│   │   │   ├── Binding.kt               # 触发器 → 动作序列
│   │   │   └── ConfigStore.kt           # 配置读写 + 默认绑定
│   │   ├── service/
│   │   │   ├── LyAccessibilityService.kt# 手势 / 全局动作 / 音量键过滤
│   │   │   ├── OverlayService.kt        # 悬浮球前台服务
│   │   │   ├── ActionRunner.kt          # 动作执行引擎
│   │   │   ├── MediaController.kt       # 媒体按键控制
│   │   │   └── BootReceiver.kt          # 开机恢复悬浮球
│   │   ├── ui/
│   │   │   ├── MainActivity.kt          # 首页：功能入口网格
│   │   │   ├── SettingsActivity.kt      # 设置页：权限 / 悬浮球 / 快捷键
│   │   │   └── EditBindingActivity.kt   # 编辑页：录制按键 + 编排动作
│   │   └── widget/LyWidgetProvider.kt   # 桌面小组件
│   └── res/                             # 布局、主题、无障碍配置、快捷方式、小组件
├── docs/设计方案.md
└── .github/workflows/build-apk.yml      # 云端打包
```

## 构建

环境要求：**JDK 17**、Android SDK **34**、Android Gradle Plugin 8.2.2 / Gradle 8.2。

> ⚠️ 本工程在无 Android SDK 的离线环境中生成，**未在本机实际编译过**（已做 XML、资源引用、ViewBinding ID 的静态校验）。
> 仓库也没有附带 `gradle-wrapper.jar`（二进制文件），请按下面任一方式构建。

### 方式 A：GitHub Actions 云端打包（最省事）

1. 新建一个 GitHub 仓库，把 `lytoolbox/` 里的全部文件推上去；
2. 打开仓库 **Actions → Build APK → Run workflow**；
3. 构建完成后在 Artifacts 里下载 `LYToolbox-debug-apk`，解压得到 `app-debug.apk`。

工作流位于 `.github/workflows/build-apk.yml`，会自动安装 JDK 17、Android SDK 和 Gradle 8.2。

### 方式 B：Android Studio

1. 安装 Android Studio（Hedgehog 及以上）与 SDK 34；
2. `File → Open` 选择 `lytoolbox` 目录；
3. 如果提示缺少 Gradle Wrapper，在项目根目录执行一次：

   ```bash
   gradle wrapper --gradle-version 8.2
   ```

   或者在 `Settings → Build, Execution, Deployment → Build Tools → Gradle` 里选择
   **Use Gradle from: Specified location**，指向本机安装的 Gradle 8.2。
4. `Build → Build Bundle(s)/APK(s) → Build APK(s)`。

### 方式 C：命令行

```bash
# 需要本机已安装 Gradle 8.2+ 与 Android SDK 34，并配置 ANDROID_HOME
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 首次使用

1. 安装后打开 **LY工具箱**，首页是一组工具卡片；
2. 点「悬浮窗」卡片进入设置页；
3. 点「无障碍服务 → 去开启」，在系统列表里找到 **LY工具箱 手势服务** 并打开；
4. 点「悬浮窗权限 → 去开启」，允许显示在其他应用上层；
5. 回到设置页打开「悬浮球」开关，可按需拖动「透明度」滑杆（20%–100%）；
6. 默认绑定已经可用：
   - 悬浮球上滑 → 下一个视频
   - 悬浮球双击 → 停止播放音乐
   - 音量+ 双击 → 下一个视频
7. 点任意一条绑定进入编辑页，可以改名、换触发方式（含「录制」音量键）、增删动作。

> 悬浮球操作：快速上/下滑 = 触发动作；**按住约 0.2 秒**（球会放大提示）再拖动 = 移动位置。

## 已知限制

- **滑动/点击需要无障碍服务**，且 `dispatchGesture` 从 Android 7.0（API 24）开始提供；
- 部分国产 ROM 会在系统层吞掉音量键，导致「音量键组合」收不到事件，此时请用悬浮球；
- 个别音乐 App 没有注册 MediaSession，媒体按键无效；
- 从后台直接「打开应用」可能被 Android 10+ 的后台启动限制拦截；
- 悬浮球依赖前台服务，被系统省电策略清理后需要重新打开 App 恢复；
- 悬浮球「滑动」与「拖动」靠时间区分：必须**快速滑动**才会触发动作，按住约 0.2 秒后再移动才是拖动位置（球会放大提示）。

## 隐私

App 不申请网络权限，不做任何上传；配置只存在本机 `SharedPreferences`。
