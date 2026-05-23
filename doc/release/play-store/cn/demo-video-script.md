# Accessibility 服务声明 — Demo 视频脚本

## 用途

这段视频是提交给 Google Play 审核员的，专门作为 Accessibility Service Permissions Declaration 的必交证据。审核员需要看到 ClosePaw 实际如何使用 a11y service。

## 格式要求

- MP4, **不超过 30 秒**
- 1080×1920 竖屏优先，或 1280×720 横屏最低
- 英文旁白（中文可能被拒；下面有中英对照旁白）
- 在测试手机或模拟器上录，**用测试 Google 账号 + 不含敏感信息的测试邮件**
- 可以有硬切，但不能贴假 UI 或遮挡权限弹窗

## 设备 / 工具

- 装好 ClosePaw 的安卓设备或模拟器
- USB 线 + `adb`（仅准备阶段用，正式录建议手机自带录屏）
- 录屏方式（任选其一，质量从高到低）：

  **A. 手机自带录屏（最推荐）** — 下拉通知栏 → 屏幕录制，自带收音
  
  **B. scrcpy** — `scrcpy --record doc/release/play-store/a11y-demo.mp4`
  
  **C. adb screenrecord（仅有线 ADB 时勉强可用）**:
  ```bash
  adb shell screenrecord --size 1080x1920 --bit-rate 8000000 --time-limit 30 /sdcard/closepaw-a11y-demo.mp4
  adb pull /sdcard/closepaw-a11y-demo.mp4 doc/release/play-store/a11y-demo.mp4
  adb shell rm /sdcard/closepaw-a11y-demo.mp4
  ```

## 干净设备准备

用一个测试 Google 账号，里面放 3-5 封无害的 seed 邮件。如果一镜到底装不下登录流程，提前把 AI provider 登录配好，最后再录任务那一段。

```bash
export PKG=ai.closepaw
adb devices
adb shell am force-stop "$PKG"
adb shell input keyevent KEYCODE_HOME

# 关动画让画面紧凑
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# 完全重置（仅在录 onboarding 那一段前用，会清空登录态）
adb shell pm clear "$PKG"

# 重置 a11y 让视频体现"用户手动启用"
adb shell settings put secure enabled_accessibility_services ""
adb shell settings put secure accessibility_enabled 0

# 预授权 overlay，让 Smart Capsule 在任务段可见
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow

adb shell am start -n "$PKG/.app.MainActivity"
```

## 演示任务

用：`Catch me up on my emails`

理由：与 Play 商店描述一致 — 描述里把"catch up on emails"列为支持的任务，截图顺序里也有 Gmail 实时视图那一张（`doc/release/play-store/full-description.txt:3`、`doc/release/play-store/README.md:15-22`）。

## 30 秒分镜

| 时间 | 画面 | 旁白（英文 / 中文参考） |
| --- | --- | --- |
| 0:00-0:04 | ClosePaw onboarding 的 Accessibility 步骤。如果"Data & privacy details"是折叠的，展开它。 | EN: "ClosePaw asks for Accessibility so it can read the screen and perform taps only for tasks I start."<br>中: "ClosePaw 申请无障碍权限，仅在我发起任务时读屏与点击。" |
| 0:04-0:10 | 点 "Open Accessibility Settings"，进入安卓的无障碍设置，打开 ClosePaw 服务并开启开关。 | EN: "I enable the service myself in Android Settings after this disclosure."<br>中: "看完说明后，我在安卓系统设置里自己启用服务。" |
| 0:10-0:13 | 回到 ClosePaw，展示权限已启用或进入下一个 onboarding 步骤。 | EN: "The app verifies the service after I return."<br>中: "返回 ClosePaw，app 会校验服务是否已启用。" |
| 0:13-0:17 | 必要时硬切到已配置好的测试设备。输入或语音说："Catch me up on my emails"。 | EN: "Now I ask it to catch me up on my emails."<br>中: "现在让它帮我总结一下最近的邮件。" |
| 0:17-0:25 | Gmail 打开或被切到前台。Smart Capsule 持续可见，agent 在读屏和操作。展示至少一次明显的点击/滑动或状态变化。 | EN: "The Smart Capsule stays visible so I can see and control the task."<br>中: "Smart Capsule 始终可见，方便我查看和接管任务。" |
| 0:25-0:30 | 点 Smart Capsule 上的 "Stop" 按钮，展示 "Stopping..." 或已停止状态。 | EN: "I can stop the task immediately at any time."<br>中: "任何时候我都能立即停下任务。" |

## 录制注意事项

- 任务不用跑完。视频的重点是 **disclosure → 手动启用 → 任务可见运行 → Stop**，不是邮件总结结果。
- **只用测试数据**。不要出现：私人邮件、联系人、金融 app、登录页、一次性验证码。
- a11y 设置页跳转太慢的话照录，后期用硬切剪掉无效时间，但**必须保留实际打开开关那一帧**。
- 安卓系统的 a11y 警告弹窗出现时**留够时间让它看清**，让审核员看到是系统（不是 ClosePaw）在授权。
- **强烈建议**：把上面英文旁白录成一条独立音轨（用手机录音 app 或 QuickTime），后期叠到无声视频上 — 这样质量稳定，不用一次性念对。

## 上传位置

录完保存到：

```
doc/release/play-store/a11y-demo.mp4
```

提审时在 Play Console → App content → Sensitive app permissions → Accessibility Service declaration 里上传。
