<a id="top"></a>

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="SteamNova 图标">
</p>

<h1 align="center">SteamNova</h1>

<p align="center">
  <strong>在 Android 版 Steam 中聊天、装扮个人资料、查看 SteamDB 数据与管理令牌。</strong><br>
  基于现代 libxposed 的 Android Steam 增强模块。
</p>

<p align="center">
  <a href="README.md">English</a> •
  <a href="https://goodjoe-2049.github.io/Steam-Nova/">项目网站</a> •
  <a href="#features">核心功能</a> •
  <a href="#chat">内置聊天</a> •
  <a href="#profile">个人资料</a> •
  <a href="#steam-guard">令牌导出</a> •
  <a href="#mafile">maFile 与恢复</a> •
  <a href="#quick-start">快速开始</a> •
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/issues">问题反馈</a>
</p>

<p align="center">
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/releases/latest"><img src="https://img.shields.io/github/v/release/GoodJoe-2049/Steam-Nova?style=flat-square&amp;label=release&amp;labelColor=334155&amp;color=3B82F6" height="20" alt="最新版本 — 下载 APK"></a>
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/releases"><img src="https://img.shields.io/github/downloads/GoodJoe-2049/Steam-Nova/total?style=flat-square&amp;label=downloads&amp;labelColor=334155&amp;color=0D9488" height="20" alt="Release 附件总下载量"></a>
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/commits/main/"><img src="https://img.shields.io/github/last-commit/GoodJoe-2049/Steam-Nova/main?style=flat-square&amp;label=last%20commit&amp;labelColor=334155&amp;color=8B5CF6" height="20" alt="main 分支最近提交"></a>
  <a href="https://t.me/MillenniumXposed"><img src="https://img.shields.io/badge/Telegram-229ED9?style=flat-square&amp;logo=telegram&amp;logoColor=white" height="20" alt="SteamNova Telegram 频道"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-16A34A?style=flat-square&amp;labelColor=334155&amp;logo=android&amp;logoColor=white" height="20" alt="Android 8.0 或更高版本">
  <img src="https://img.shields.io/badge/libxposed-API%20102-6366F1?style=flat-square&amp;labelColor=334155" height="20" alt="libxposed API 102">
  <img src="https://img.shields.io/badge/ABI-arm64--v8a-64748B?style=flat-square&amp;labelColor=334155" height="20" alt="arm64-v8a">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-0F766E?style=flat-square&amp;labelColor=334155" height="20" alt="MIT 许可证"></a>
</p>

<table align="center">
  <tr>
    <td width="25%" align="center" valign="top">
      <a href="docs/images/panel-witcher3.jpg"><img src="docs/images/panel-witcher3.jpg" width="280" alt="巫师 3 商店页面中的 SteamDB 悬浮面板"></a><br>
      <sub>SteamDB 悬浮面板</sub>
    </td>
    <td width="25%" align="center" valign="top">
      <a href="docs/images/steam-menu-preview.jpg"><img src="docs/images/steam-menu-preview.jpg" width="280" alt="Steam 菜单中的 SteamNova 入口"></a><br>
      <sub>Steam 菜单入口</sub>
    </td>
    <td width="25%" align="center" valign="top">
      <a href="docs/images/module-settings-preview.jpg"><img src="docs/images/module-settings-preview.jpg" width="280" alt="带有液态玻璃底栏的 SteamNova 常规页面"></a><br>
      <sub>模块设置主页</sub>
    </td>
    <td width="25%" align="center" valign="top">
      <a href="docs/images/steam-guard-export-preview.jpg"><img src="docs/images/steam-guard-export-preview.jpg" width="280" alt="已遮挡敏感数据的 Steam 令牌导出弹窗"></a><br>
      <sub>Steam 令牌导出</sub>
    </td>
  </tr>
</table>

<p align="center"><sub>部分截图来自早期版本，项目名称或界面可能与当前版本不同。</sub></p>

---

**SteamNova** 是 Android 版 Steam 的增强模块，提供内置聊天、桌面端个人资料展示柜、本机资料装扮、SteamDB 数据与 Steam 令牌工具。打开 **Steam → 菜单 → SteamNova**，即可从“商店”上方的入口管理模块。

<a id="rename"></a>

> **Millennium 现已更名为 SteamNova。** 项目已更换名称、图标及仓库地址：[GoodJoe-2049/Steam-Nova](https://github.com/GoodJoe-2049/Steam-Nova)。
>
> 本项目与 **[SteamClientHomebrew/Millennium](https://github.com/SteamClientHomebrew/Millennium) 没有任何关系**，不是它的 Android 版本、官方移植版或关联项目。
>
> 独立模块包名已由 `com.millennium.app` 改为 `steam.nova.app`。从旧模块升级时，请在框架中启用 **SteamNova**、勾选 **Steam** 作用域，并停用旧模块，然后强行停止并重新打开 Steam。

<a id="features"></a>

## ✨ 核心功能

| 功能 | 可以查看或完成什么 |
| :--- | :--- |
| **内置聊天** | 支持私聊、群聊、图片、贴纸、表情、表情回应和群管理，无需安装 Steam Chat。 |
| **聊天工具** | 会话置顶、免打扰、消息缓存、草稿保存、好友备注与后台消息提醒。 |
| **聊天室效果** | 使用 Steam 官方特效资源进行原生播放和回放，持久保存资源及解析结果。 |
| **更好的个人资料** | 采用桌面端展示柜排版，在适用展示柜中一排显示两个项目，完整展示顶部信息，并增加左侧资料栏。 |
| **个人资料装扮** | 在本机预览并保存资料背景、头像、头像框、特殊资料和迷你资料背景；保存的头像同步显示到 Steam 原生页面与内置聊天中。 |
| **免登录浏览** | 无需登录即可浏览 Steam 公开页面，账号相关功能仍需登录。 |
| **SteamDB 悬浮面板** | 实时在线人数、24 小时峰值、历史峰值、关注数、历史最低价、SteamDB 评分和最近更新日期。 |
| **价格记录** | 跟随商店币种与定价地区，显示可用的折扣、出现次数与日期；SteamDB 提供时，同时展示两年内最低价。 |
| **悬浮操作** | 拖动悬浮球、贴边并记住位置，手动刷新当前游戏数据，或打开对应的 SteamDB 页面。 |
| **液态玻璃底栏** | 分别控制模块底栏与 Steam 底栏的玻璃效果和交互动画，需要 Android 13 或更高版本。 |
| **游戏库双指缩放** | 双指调整游戏库排版，采用类似 Telegram 媒体页的切换效果。 |
| **Steam 令牌工具** | 设置令牌默认页面，预览并复制下一组验证码，导出令牌信息。 |
| **二维码与直接导入** | 支持 Stratum、Aegis、Monica 身份验证器，可选择对应格式的二维码或通过“添加到”直接导入。 |
| **maFile 备份与恢复** | 导入、导出并恢复账号数据，支持从兼容版本的 Monica Steam 直接授权获取 maFile。 |
| **Steam 管理** | 通过 Shizuku/Sui、Dhizuku 或 Root 保留数据卸载 Steam。 |
| **模块设置与语言** | miuix 设置界面，支持跟随系统、English 和简体中文。 |

<a id="chat"></a>

## 💬 内置聊天与聊天室效果

在 **常规 → 内置聊天** 中开启功能，然后**强行停止并重新打开 Steam**。该功能默认关闭。

- **聊天内容**：私聊、群聊、图片、贴纸、表情、表情回应和群管理。
- **日常使用**：置顶、免打扰、好友备注、消息缓存、草稿保存与后台消息提醒。
- **入口与排版**：底栏增加聊天入口，通知入口移至商店页右上角；进入具体会话后隐藏底栏，并适配键盘。
- **聊天室效果**：支持发送特效，在当前会话中自动播放新收到的特效，也可点击消息再次回放。

聊天室效果根据 Steam 官方 JavaScript、CSS 与图片资源准备原生播放，包含时序、随机变化和尺寸适配。首次准备需要联网，可能耗时较长。已下载的特效资源与解析结果会持久保存，通常不会因 Android 的**清除缓存**而丢失或需要重新完整解析；缓存损坏或不兼容时会重建，更新失败时可回退到已经验证的资源。**清除存储空间／应用数据**，或不保留数据卸载应用，不在此保留范围内。

**暂不支持语音通话。** 后台提醒依赖 Steam 能够继续运行，并受 Android 通知权限与后台限制影响。

<a id="profile"></a>

## 🎨 个人资料排版与装扮

两个功能均位于 **常规 → 界面美化**，开关相互独立，默认关闭；修改开关后需要重启 Steam。

### 更好的个人资料

- 使用桌面端展示柜排版，在桌面端支持的展示柜中一排显示两个项目。
- 将桌面右侧的等级、勋章、在线状态、资料详情及相关链接等内容移至左侧栏，通过左上角按钮展开。
- 顶部信息集中展示，将紧凑头像放在名称和地区旁，无需再点击手机端的“查看更多信息”。

该功能直接适配手机上的资料排版，无需将设备伪装成桌面端。

### 个人资料装扮

开启 **个人资料装扮** 后，打开自己的资料页，点击替换右上角头像的**调色板图标**。

- 从底部展开的面板中浏览**资料背景、头像、头像框、特殊资料和迷你资料背景**。
- 按装扮名称搜索，或按游戏／App ID 筛选，支持刷新目录、展开与收起面板。
- 选择装扮进行预览，点击**保存到本机**或**恢复原装扮**；关闭面板会还原未保存的预览。
- 保存的头像同步应用于商店、新闻、令牌、聊天、菜单和通知等页面。右上角头像保持静态；带有**查看个人资料**按钮的个人页面支持动态头像与动态头像框。

**装扮修改仅影响本机显示。** 不会在 Steam 服务器上购买或装备物品，其他用户看到的资料不会因此改变。

<a id="interface"></a>

## 📱 界面与交互

模块设置采用 [miuix](https://github.com/compose-miuix-ui/miuix) 的卡片、开关、弹窗与选择菜单，可以点击底栏或左右滑动切换页面。

在 **常规 → 界面美化 → 液态玻璃底栏** 中，分别控制**模块底栏**与 **Steam 底栏**。该效果需要 Android 13 或更高版本，较早系统使用普通底栏。**游戏库双指缩放**默认开启，修改后需重启 Steam。

免登录浏览默认生效，不提供单独开关。无需登录即可浏览商店、社区等公开内容，也可随时进入登录流程；账号相关功能仍需登录。

**SteamDB 悬浮面板**开关即时生效。**设置 → 语言**仅切换模块语言，不改变 Steam 自身的语言。设置界面会立即刷新；聊天、下一组验证码等注入界面的语言需要重启 Steam 后更新。

**主题**和**日志**目前仍为占位页面。

<a id="steam-guard"></a>

## 🔐 Steam 令牌工具与导出

在**常规**页面中，可设置打开令牌时默认显示**验证码页**或**扫码页**，并开启**显示下一个验证码**，预览和复制下一组验证码。修改这些选项后需重启 Steam。

导出入口为 **Steam → 菜单 → SteamNova → 常规 → Steam 令牌导出**。

- **轻触复制**：查看类型、用户名、服务商和密钥；存在多个账号时可切换账号。
- **扫码导入**：选择 Stratum、Aegis 或 Monica，显示对应格式的二维码，切换时带有过渡动画。
- **直接添加**：点击**添加到 Stratum**、**添加到 Aegis** 或**添加到 Monica**，打开对应身份验证器并带入账号信息。

| 身份验证器 | 直接导入时的预期流程 |
| :--- | :--- |
| [Stratum](https://github.com/stratumauth/app) | 打开应用并添加 Steam 令牌；如有应用锁，需要先解锁。 |
| [Aegis](https://github.com/beemdevelopment/aegis) | 打开已填好数据的页面，核对后保存；如有提示，先完成解锁。 |
| Monica 身份验证器 | 打开 Monica 并带入 Steam 令牌信息。此功能与下文 Monica Steam 的 maFile 对接不同。 |

SteamNova 会为二维码和直接导入选择对应的 Steam 链接格式。请先安装并完成身份验证器初始化；具体确认流程可能随其版本变化。

二维码在设备本地生成，模块不会在 Steam 启动时自动将令牌信息复制到剪贴板。如果暂未获取到数据，请先打开 Steam 的**令牌**页面，再返回导出弹窗。

<a id="mafile"></a>

## 📦 maFile 导入、导出与恢复

打开 **Steam → 菜单 → SteamNova → 常规 → maFile 导入与导出**。

### 导出与兼容性

- 存在多个账号时先选择账号，再点击**导出文件**保存 `.maFile`。
- 显示已获取的用户名、SteamID、令牌密钥、设备标识、撤销码和登录凭据。**点击字段即可复制完整内容。** 长数据以单行省略号显示，复制和导出均保留完整原文。
- 导出时按 SteamID 匹配令牌数据与登录凭据，导入和再次导出时保留 maFile 中的额外字段。
- 兼容 **Monica Steam** 和 **Steam Desktop Authenticator（SDA）** 使用的单账号明文 maFile 格式。支持导入不超过 **1 MB** 的文件，暂不支持 ZIP 压缩包和加密备份。
- **早期版本已验证与 Monica Steam 互通及同账号多设备恢复**，包括生成一致的 Steam 动态验证码，以及在有效会话下进行登录批准。向 Monica Steam 导入时，可先导出 maFile，再在 Monica Steam 的导入界面选择文件。

### 从 Monica Steam 直接导入

在**导入**标签页点击**从 Monica Steam 获取**。兼容版本的 Monica Steam 会让你选择账号并确认授权，随后返回 SteamNova 预览并导入 maFile，无需手动转移文件。

如果所安装的 Monica Steam 版本尚未支持此对接，请先从中导出 maFile，再使用**选择文件**导入。对接说明见 [Monica Steam maFile 授权交接](docs/monica-mafile-handoff.md)。

**Monica Steam** 用于 Steam 账号与 maFile 管理，与二维码导出中的 **Monica 身份验证器**是不同的应用。

### 在官方 Steam 中恢复

1. 在来源手机打开 Steam 的**令牌**页面，再通过 SteamNova 导出该账号的 maFile。
2. 在已启用 SteamNova 的目标手机上打开**导入**标签页，选择文件，核对账号后点击**导入**。
3. 导入会覆盖相同 **SteamID** 的已保存数据；宿主与凭据支持恢复时，会自动安排恢复。同一账号在列表中只显示一次。
4. 在 **Android 设置中强行停止 Steam，再重新打开**。仅切到后台再返回不会执行恢复。
5. 查看恢复状态和 Steam 的**令牌**页面。在线登录批准需要该账号的有效登录会话。

如果令牌数据可用，但登录凭据缺失、过期或无效，SteamNova 会恢复令牌并保留当前登录会话。只包含可用登录凭据的文件可以恢复会话，但无法补齐缺失的令牌密钥。登录凭据是否有效由 Steam 联网验证。

恢复会保留其他账号记录；文件中的设备标识还会更新 Steam 的全局设备标识。支持重启前连续导入多个账号、取消待执行的恢复，也可点击**恢复账号数据**再次安排恢复。

已导入账号和恢复备份加密保存在应用私有目录。写入失败时会尝试回滚，操作中断后不会自动重复执行。**撤销**仅在当前数据仍与恢复记录匹配时还原之前的状态。恢复成功后，导出会使用 Steam 当前的登录凭据。

<a id="steam-management"></a>

## 🧰 Steam 管理

在 **设置 → 工作模式** 中选择并授权一种模式：

| 模式 | 准备工作 |
| :--- | :--- |
| **Shizuku / Sui** | 启动服务，并向 Steam 宿主授予使用权限。 |
| **Dhizuku** | 完成 Dhizuku 激活并启动服务，再确认授权请求。 |
| **Root** | 通过 Magisk、KernelSU 等 Root 管理器向 Steam 授予超级用户权限。 |

工作模式仅用于需要特权的管理操作，不能替代 Xposed 框架；使用普通模块功能无需开启这些模式。

在 **常规 → Steam 管理** 中，**保留数据卸载 Steam** 可卸载 Steam，同时保留当前 Android 用户的应用数据。操作开始后 Steam 会关闭。重新安装时需要使用相同签名的安装包；官方版与 NPatch 修改版可能使用不同签名。保留应用数据不能代替 maFile 备份。

<a id="quick-start"></a>

## 🚀 快速开始

### 环境要求

| 项目 | 要求 |
| :--- | :--- |
| Android | Android 8.0 / API 26 或更高版本，同时满足框架与 Steam 自身的要求。 |
| 架构 | `arm64-v8a`。 |
| 独立模块所需框架 | **核心支持现代 libxposed API 102** 的 LSPosed 或兼容框架。 |
| 目标应用 | Android 版 Steam：`com.valvesoftware.android.steam.community`。 |
| 当前 Steam 适配 | Steam **3.10.11（10928360）**。依赖 Steam 内部界面或程序包的功能，可能需要针对其他版本重新适配。 |
| 可选效果 | 液态玻璃需要 Android 13 或更高版本。 |
| 可选管理模式 | 执行特权管理操作时，使用已授权的 Shizuku/Sui、Dhizuku 或 Root。 |

### 版本下载

[GitHub Releases](https://github.com/GoodJoe-2049/Steam-Nova/releases/latest) 提供最新文件和更新说明。[v1.4.7 版本](https://github.com/GoodJoe-2049/Steam-Nova/releases/tag/v1.4.7) 包含：

| 文件 | 用途 |
| :--- | :--- |
| `SteamNova-v1.4.7-arm64-v8a.apk` | 独立模块，按下方框架安装步骤使用。 |
| `Steam-1.4.7-npatched.apk` | 经 NPatch 修补的 Steam 安装包，与独立模块分开提供，请按对应 Release 说明使用。 |

### 通过框架安装

1. 从 [GitHub Releases](https://github.com/GoodJoe-2049/Steam-Nova/releases) 下载并安装**独立模块 APK**。
2. 在 Xposed 框架管理器中启用 **SteamNova**。
3. 在模块作用域中勾选 **Steam**；从 Millennium 升级时，请停用旧模块。
4. **强行停止 Steam，再重新打开**，让框架加载模块。
5. 打开 Steam 底栏最右侧的**菜单**，点击“商店”上方的 **SteamNova**。
6. 按需开启**内置聊天**、**更好的个人资料**或**个人资料装扮**，然后重启 Steam。这些功能默认关闭。
7. 进入游戏商店详情页，点击悬浮球查看 SteamDB 数据；令牌与 maFile 工具位于模块的**常规**页面。

悬浮面板附着在 Steam 自身的窗口中，无需申请 Android 的“显示在其他应用上层”权限。

仓库更名后，已有本地 Git 仓库可在**该仓库目录内**执行：

```bash
git remote set-url origin https://github.com/GoodJoe-2049/Steam-Nova.git
git remote -v
```

本地文件夹名称无需修改。

<a id="data"></a>

## 📊 数据来源

- **实时在线人数**：优先使用 Steam 官方接口，SteamDB 作为后备。
- **峰值、关注数与最近更新**：来自 SteamDB 游戏信息。
- **历史最低价**：来自当前商店币种与定价地区对应的 SteamDB 价格记录。
- **SteamDB 评分**：读取商店页面的好评与差评数量，在本地按 SteamDB 公式计算。
- **聊天室效果**：从 Steam 官方特效脚本、样式和图片资源中准备原生播放所需内容。
- **个人资料装扮**：使用 Steam 装扮目录及媒体资源，在本机应用选择。

数据可用性取决于上游服务和当前 Steam 页面；缺失字段会显示为不可用，不会当作零值。

<a id="faq"></a>

## 💬 常见问题

<details>
<summary><strong>为什么看不到菜单入口或悬浮球？</strong></summary>

确认模块已启用、作用域选择了 Steam，且框架核心支持 API 102，然后强制停止并重新打开 Steam。已有 SteamNova 入口时，请检查 SteamDB 悬浮面板开关。

当前菜单识别支持中文“商店”和英文“Store”。切换模块语言不会扩展 Steam 菜单语言的兼容范围；Steam 更新也可能需要相应适配。

</details>

<details>
<summary><strong>为什么部分数据不可用，或者与 Steam 显示的不一样？</strong></summary>

Steam 和 SteamDB 是不同的数据来源，可用性与更新时间可能不同。价格跟随商店的币种和定价地区，而非手机语言。SteamDB 评分会考虑评价样本量，因此不等于 Steam 的好评百分比。

</details>

<details>
<summary><strong>为什么直接导入时无法打开身份验证器？</strong></summary>

请在与 Steam 相同的 Android 用户或工作资料中安装并启用对应软件，并检查系统是否限制应用间启动。如果身份验证器已锁定，请完成密码或指纹验证。也可以使用二维码或逐项复制数据。

</details>

<details>
<summary><strong>为什么导入 maFile 后没有立即恢复 Steam 令牌？</strong></summary>

恢复会在下次完整启动 Steam 时执行。请确认弹窗显示已安排恢复，再到 Android 设置中强行停止 Steam 并重新打开。再次导入相同 SteamID 的文件，会替换该账号已保存的数据和待执行的请求。

若提示无法恢复，仍可保存文件并再次导出。只有登录凭据的文件无法恢复缺失的令牌密钥；登录批准还需要该账号的有效会话。反馈问题时请附上恢复状态和 Steam 版本。

</details>

<details>
<summary><strong>两种 Monica 对接有什么区别？</strong></summary>

令牌导出中的**添加到 Monica**会打开 Monica 身份验证器并带入 Steam 令牌。maFile 导入中的**从 Monica Steam 获取**则会在授权后，从兼容的 Monica Steam 应用获取 maFile。反向导入到 Monica Steam 时，仍可先导出文件，再在 Monica Steam 中选择导入。

</details>

<details>
<summary><strong>为什么没有聊天入口，或收不到后台提醒？</strong></summary>

请先开启 **常规 → 内置聊天**，然后强行停止并重新打开 Steam。检查 Steam 版本是否兼容，以及 Android 通知权限与后台运行限制。Steam 被强行停止后无法继续发送后台聊天通知。

</details>

<details>
<summary><strong>为什么准备聊天室效果需要一段时间？</strong></summary>

首次准备会下载并解析 Steam 特效资源，后续启动可复用持久保存且验证有效的资源与解析结果。Android 的**清除缓存**通常会保留这些文件，清除应用数据则不会。上游资源变化或缓存失效时，可能需要重新准备。

</details>

<details>
<summary><strong>其他人能看到我修改的个人资料装扮吗？</strong></summary>

不能。保存的装扮仅改变这台设备上的显示，不会修改 Steam 账号实际装备的物品。点击**恢复原装扮**即可回到原始外观。

</details>

<details>
<summary><strong>为什么部分名称或链接中还有 Millennium？</strong></summary>

项目现已更名为 SteamNova，但 Telegram 频道保留原地址，部分历史截图、内部存储名称和诊断标签仍沿用 Millennium。这不代表本项目与 SteamClientHomebrew/Millennium 有关联。

</details>

<a id="build"></a>

## 🛠️ 从源码构建

```bash
git clone https://github.com/GoodJoe-2049/Steam-Nova.git
cd Steam-Nova
```

在 Android Studio 中打开项目并同步 Gradle。当前配置使用 **Android SDK 37**、**JDK 25 Gradle Daemon 工具链**，Java 源码与目标编译级别为 **21**。请使用仓库自带的 Gradle Wrapper，依赖版本见 `gradle/libs.versions.toml`。

<details>
<summary><strong>构建命令与 APK 输出</strong></summary>

Windows / PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Linux / macOS：

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

产物分别位于 `app/build/outputs/apk/debug/` 和 `app/build/outputs/apk/release/`。Release 文件名遵循 `SteamNova-v<versionName>-<ABI>.apk`，例如 `SteamNova-v1.4.7-arm64-v8a.apk`。

Release 构建启用 R8 与资源缩减，请保留项目中的 ProGuard 规则。未签名的 Release APK 需要签名后才能安装，也可以通过 Android Studio 的 **Generate Signed App Bundle or APK → APK** 生成签名 APK。

</details>

<a id="contributing"></a>

## 🤝 反馈与贡献

欢迎通过 [Issues](https://github.com/GoodJoe-2049/Steam-Nova/issues) 和 [Pull Requests](https://github.com/GoodJoe-2049/Steam-Nova/pulls) 报告问题、提出建议或贡献改进。请附上设备与 Android 版本、框架与模块版本、Steam 版本、复现步骤及相关日志。商店数据问题请同时提供游戏链接或 AppID。

欢迎关注 [Telegram 频道](https://t.me/MillenniumXposed)，获取版本发布与项目进展。

导出的 maFile 包含明文凭据，会话诊断日志也可能包含登录令牌。分享截图或日志前，请遮挡账号信息、令牌密钥、access/refresh token 和二维码。请勿在 Issue 中附上未脱敏的 maFile。

## 🙏 致谢

- [SteamDB Browser Extension](https://github.com/SteamDatabase/BrowserExtension) — 数据字段、区域价格与评分算法参考。
- [libxposed](https://github.com/libxposed/api) — 现代 Xposed 模块 API。
- [miuix](https://github.com/compose-miuix-ui/miuix) — 模块设置的界面组件与交互。
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) — 液态玻璃效果。
- [Stratum](https://github.com/stratumauth/app) 与 [Aegis](https://github.com/beemdevelopment/aegis) — 开源身份验证器。
- [Steam Desktop Authenticator](https://github.com/Jessecar96/SteamDesktopAuthenticator) — Steam maFile、Steam Guard 与交易确认参考。
- [steamguard-cli](https://github.com/dyc3/steamguard-cli) — Steam Guard 登录、令牌迁移与确认协议参考。
- [AnotherVaporAuth](https://github.com/freefrank/AnotherVaporAuth) — Steam 移动验证器与登录批准参考。
- [Monica Steam](https://github.com/JoyinJoester/Monica-Steam) 与 [Monica 身份验证器](https://github.com/Monica-Pass/Monica) — Steam 账号互通与令牌导入支持。
- [Steam Profile Modifier](https://chromewebstore.google.com/detail/steam-profile-modifier/lfjdaphbejfpncjfebkbcganmkinhhfl) — 本机个人资料装扮功能参考。
- [Lottie Web](https://github.com/airbnb/lottie-web) — 资料装扮面板中的动画。

## 📄 开源协议

SteamNova 采用 **[MIT License](LICENSE)**。第三方代码、依赖和资源仍分别遵循其原始许可证。

本项目由社区独立开发，与 Valve、Steam 或 SteamDB 无官方隶属关系。

---

<p align="center">
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/releases">版本下载</a> •
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/issues">问题反馈</a> •
  <a href="https://t.me/MillenniumXposed">Telegram</a> •
  <a href="#top">返回顶部 ↑</a>
</p>
