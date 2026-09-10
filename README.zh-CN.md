# Bileebilee

[English](README.md) | [简体中文](README.zh-CN.md)

一款轻量、遥控器优先的 Android TV 哔哩哔哩客户端，初期主要适配运行 Android 5.1（API 22）的小米盒子 3 增强版。

## 当前版本（1.0.1）

当前版本提供以下功能：

- 以推荐内容为首页，并提供常驻的电视端导航标签
- 聚焦即切换的紧凑导航，无需额外点击确认
- 从顶部标签按下方向键时，稳定跳转到各页面当前可见的顶部控件
- 导航栏右侧的简洁下划线搜索框，支持电视软键盘和实体键盘输入
- 支持搜索结果分页、播放，以及返回时保留焦点
- 切换不同页面时保留焦点和已加载内容
- 完整展示两行内容，并按行对齐滚动
- 根据焦点位置无缝预加载并追加下一页内容
- 历史、直播、关注和 UP 主视频均采用每页 20 项、提前两行预加载的分页方式
- 提供明确的刷新推荐操作，可从第一页重新获取移动端推荐流
- 统一的电视安全区边距和舒适的内容卡片间距
- 支持方向键焦点导航
- 支持通过系统或应用语言设置切换英语和简体中文
- 支持哔哩哔哩二维码登录，登录状态仅保存在设备的应用私有存储中
- 已登录账户页面显示昵称和 UID
- 可在账户页面分页浏览已关注的 UP 主
- 可浏览并播放各个已关注 UP 主的近期视频
- 从播放页返回 UP 主视频、关注列表或账户页面时保留焦点
- 适合遥控器操作的热门直播间分页浏览
- 登录后可浏览已关注且正在直播的主播
- 使用方向键在“关注”和“热门”直播之间切换
- 展示直播间封面、主播名称、分区和人气
- 结束播放后返回此前选中的直播间
- 支持 AVC 直播播放，优先使用 HLS，并以 FLV 作为后备
- 获取哔哩哔哩移动端的个性化推荐
- 持久保存移动端推荐流标识，使用应用端请求上下文和游标分页刷新
- 四列电视端视频网格，展示封面、元数据和遥控器焦点状态
- 支持推荐视频的渐进式播放
- 播放控件适配过扫描安全区，并提供适合遥控器操作的倍速和音轨设置
- 与账户同步、使用游标分页的观看历史
- 显示已保存的播放进度，并支持从历史进度续播
- 定期及播放结束时发送播放心跳，更新哔哩哔哩观看历史
- 按返回键退出播放后，回到来源页面和此前选中的卡片

以上功能已在运行 Android 5.1 的小米盒子 3 增强版上测试。

## 环境要求

- JDK 17
- Android SDK Platform 35
- Android TV 设备或模拟器

项目使用 API 35 编译，并支持 Android 5.1/API 22。Media3 特意固定在最后一个兼容 API 22 的版本系列。

## 构建

```bash
./gradlew assembleDebug lintDebug
```

调试 APK 将生成于 `app/build/outputs/apk/debug/app-debug.apk`。

如需生成文件名整洁且包含版本号、便于旁加载的 APK，请运行：

```bash
./gradlew packageTvApk
```

打包后的 APK 将生成于 `app/build/outputs/distribution/Bileebilee-TV-v<version>.apk`。

## 在已配置的模拟器上运行

```bash
$ANDROID_HOME/emulator/emulator -avd Bileebilee_TV_API_34
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
$ANDROID_HOME/platform-tools/adb shell am start -n com.bileebilee.tv/.CrashReportActivity
```

该模拟器为 ARM64 Android TV API 34，分辨率为 1920×1080，内存为 2 GB。

## 安全边界

请勿提交哔哩哔哩密码、Cookie、访问令牌、刷新令牌、二维码授权码或 HAR 文件。二维码登录仅将会话数据保存在设备的应用私有存储中。
