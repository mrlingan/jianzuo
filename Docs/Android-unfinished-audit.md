# Android 未完成功能检查与完善

日期：2026-10-11（Asia/Shanghai）。当前交付 Android 1.12.0，versionCode 13；文稿库格式保持 4。1.7 完成导入与生命周期修复，1.8 完善扫描和找回，1.9 补齐 PDF／PNG Markdown 排版，1.10 增加内置配色与横线稿纸，1.11 完成安卓中英文界面与语言切换，1.12 接入用户自填 Key 的 DeepSeek AI 续写。

## 检查依据

检查了 `Android/app/src/main` 的实现、Manifest、现有单元／设备测试、Android README 和竞品功能对照。源码没有显式 TODO／未实现异常；未完成功能主要记录在 [功能对照](PureWriter-analysis.md) 的「部分／未接入」条目中，不能只凭入口或文档断言行为可用。

基础版本可构建，现有 JVM 测试与 Lint 通过。按现有离线架构逐步补齐导入、找回及视觉导出，并修复相关生命周期与文件保护问题；没有接入新的运行时依赖、网络服务或存储权限，也未修改 Apple 端源码。

## 已完成

| 检查发现 | 完善结果 | 验证方式 |
| --- | --- | --- |
| 只有 ZIP／多文件导入，没有系统目录扫描 | 系统文件夹选择，递归读取 UTF-8 Markdown／TXT；第一层目录归入笔记本，根目录归入当前笔记本 | JVM 模拟目录、真实系统 DocumentsProvider、Activity 返回结果到导入预览 |
| 外部应用无法用简作打开／分享文本 | 注册 SEND／SEND_MULTIPLE／VIEW，文本与文件均先进入导入预览，重复 URI 去重 | 分享文字、取消、打开文件、多文件解析、Manifest 匹配 |
| 外部打开可创建另一份可写页面 | 主页面使用 singleTask，外部内容通过 onNewIntent 处理，保留已有编辑位置 | 已打开应用接收分享时核对 Activity 身份 |
| 导入预览重建后丢失 | 旋转保留已保存的标题、排序、勾选及候选正文；文件夹扫描跨配置重建继续交付结果 | 预览编辑／取消勾选后重建，文件夹返回后立即重建 |
| 导出正文／整库直接放进 Bundle | 后台原子写入私有临时快照，Bundle 仅保存 ID；成功／取消清理，阻止并发打开多个导出选择器 | 数 MB Unicode 正文快照往返、恢复状态体积、页面重建及取消清理 |
| 小屏预览按每篇 64 dp 固定估算，长标题裁切 | 单篇列表至少 120 dp，列表标题限制三行，详情仍可查看／编辑完整标题 | 手机导入预览勾选及旋转验证 |
| 导入后仍沿用旧标签／分卷筛选 | 完成导入时清除旧筛选，进入首篇新文稿 | 实现路径核查与导入设备测试 |
| 扫描没有进度／取消，章节按字面排序 | 1.8 显示条目、文稿和容量，旋转保留任务；取消丢弃候选并请求系统中止读取；数字自然排序 | JVM 读取中取消／资源关闭／进度／超长数字，设备旋转取消与迟到结果拒绝 |
| 只能逐篇查看历史或整库恢复，无法按片段找回 | 1.8 搜索当前、废纸篓、历史及最近 24 份自动快照；相同版本去重、完整预览并保存独立副本 | JVM 多来源／损坏快照／限额／中断／副本保护，设备实际历史恢复及快照预览取消 |
| PDF／PNG 只显示 Markdown 原文 | 1.9 原生排版支持标题、行内样式、列表、引用、代码、基础表格；保留原文模式，表格与长代码按文字行分页 | 设备行覆盖／分页／表头重复，实际 PDF 与长图，Poppler 渲染和文本核对 |
| 长图超限前已打开目标，可能清空旧文件 | 1.9 先在私有缓存完成排版，再打开所选目标；失败清理缓存 | 真实系统文档提供方中预置内容，超限导出后逐字核对目标不变 |
| 设置页残留手写的旧版号 | 1.9 从构建版本生成资源，设置页随安装包版号更新 | 最终 APK 手机／平板设置界面及 Manifest 版号一致 |
| 外观只有原始配色和深浅色，没有稿纸 | 1.10 五套内置配色各有深浅版本，横线跟随正文布局，设置内实时预览，取消不保存 | 全部配色／回退／对比度单测；实际预览取消、保存重开、选区、单稿／双稿／只读、位图横线及硬件绘制下长文滚动检查 |
| 强调色按钮和高完成度统计格子固定白字，浅色底色对比度不足 | 1.10 根据实际底色亮度选择黑／白文字，包含统计热力图各档混合色 | 十套配色的按钮及五档统计格子对比度至少 4.5；十套原生界面的按钮文字检查 |
| 界面文案硬编码，菜单动作依赖中文显示文字 | 1.11 统一中英文资源、跟随系统与手动切换；固定标识驱动菜单，保留文稿及用户命名 | 资源覆盖／格式参数单测，设备语言切换、取消、冷启动、菜单、统计、双稿与数据保留检查 |

| AI 续写仅有待办、缺少用户服务配置 | 1.12 接入 DeepSeek：本机加密 Key、连接测试、上下文续写、可编辑预览及主动插入 | JVM 请求与边界检查，设备配置／取消／覆盖保护／双稿／撤销，中文英文真实接口调用 |

目录扫描使用有界后台线程池，避免文件提供方读取阻塞现有文稿保存。目录扫描会跳过隐藏文件／目录和非文本附件；最多检查 5,000 个条目、导入 1,000 篇、单篇 2 MB、总正文 64 MB。文件名中的 ASCII 数字按数值比较，等值补零名称按位数确定次序，不受整数溢出影响。读取失败、无效 UTF-8、重复／循环文档引用或超限均中止整批，确认前不改写文稿库；不会更改来源文件。

找回搜索仅作字面匹配，不把关键词当正则表达式；单快照 64 MB、总读取预算 256 MB、最多 200 个匹配版本，损坏或超限快照报告跳过。以文稿 ID、标题和正文的 SHA-256 指纹去重，当前来源优先；不同文稿的相同正文仍分别显示。恢复副本只写入标题、正文、笔记本，不改变原文、只读／删除状态、历史、统计及来源快照。

## 仍未完成的范围

以下是产品能力的明确边界，不是本轮已完成项：

| 范围 | 当前状态与后续依赖 |
| --- | --- |
| 云同步、WebDAV／云盘、桌面实时连接 | 需要认证、传输、冲突规则与真实服务验证；当前各平台本地独立存储 |
| AI 校对／对话／实时副驾驶及其他服务商 | 1.12 已接入 DeepSeek 主动续写、个人 Key 配置、费用说明、可编辑预览与确认插入；其他 AI 工作流及 Apple AI 尚未提供 |
| 图片资源、数学公式、复杂 Markdown | PDF／PNG 的基础 Markdown 富排版已完成；图片资产、公式、复杂嵌套、合并单元格和超宽表格富排版仍未支持 |
| 自定义主题／背景、光标摇杆和滑动键盘 | 1.10 已有五套内置配色、深浅色与横线稿纸；尚无自定义颜色、背景图片、稿纸间距、主题文件导入导出及光标摇杆／滑动键盘 |
| 逐句实时 TTS 回读、实时关键词高亮 | 当前为主动朗读／停止和关键词次数检查；设备语音播放仍需真机验证 |
| 覆盖整库恢复、智能合并及损坏片段修复 | 跨当前／废纸篓／历史／自动快照的关键词找回已完成；整库合并仍创建新 ID，没有覆盖／智能合并、缓存片段修复或第三方数据库扫描 |
| 专用迁移、其他语言及导出语言元数据 | 当前支持 UTF-8 Markdown／TXT／ZIP 和简作 .jzb；安卓界面已支持简体中文／English，其他语言及 Apple 界面仍未翻译；EPUB 固定目录文字与语言元数据沿用中文 |
| 独立密码／文件加密与延时锁 | 当前设备密码锁控制访问，文稿存储不加密；1.12 的 API Key 单独加密保存；实际设备认证仍待验证 |

确认前的导入预览不是永久草稿：旋转可保留；详情尚未保存的修改、进程被终止后的候选内容需要重新选择。系统清理缓存后，未完成的导出需重新发起。取消扫描已可立即放弃候选，但提供方若忽略系统取消请求，底层读取可能继续等待；最多两路后台扫描，耗尽时提示稍后重试。实际云盘提供方长期阻塞仍待验证。找回窗口在旋转、退出或应用锁定时关闭，需要重新搜索。

## 代码与验证

主要实现位于 `FolderImport.java`、`FolderScan.java`、`TextImport.java`、`RecoverySearch.java`、`RecoveryDialog.java`、`IncomingText.java`、`ImportPreview.java`、`ExportSnapshot.java`、`PrintLayout.java`、`PreparedExport.java`、`DocumentExport.java`、`WritingAppearance.java`、`AppearanceSettings.java`、`PaperLines.java` 和 `MainActivity.java`。测试包括 `TextImportTest`、`RecoverySearchTest`、`TransferInstrumentedTest`、`RecoveryInstrumentedTest`、`PrintInstrumentedTest`、`WritingAppearanceTest` 和 `AppearanceInstrumentedTest`；详细结果见 [Android 验证记录](Android-validation.md)。

实现依据：[Android 文件访问](https://developer.android.com/training/data-storage/shared/documents-files)、[DocumentsContract](https://developer.android.com/reference/android/provider/DocumentsContract)、[接收其他应用的数据](https://developer.android.com/training/sharing/receive)、[保存 UI 状态](https://developer.android.com/topic/libraries/architecture/saving-states)。

1.8 的查询／文件读取取消使用 [ContentResolver](https://developer.android.com/reference/android/content/ContentResolver) 的 CancellationSignal 重载及 [CancellationSignal](https://developer.android.com/reference/android/os/CancellationSignal)，同时在条目遍历／正文读取中检查取消，关闭流和发送取消请求均不占用 UI 线程。

1.9 使用 [StaticLayout](https://developer.android.com/reference/android/text/StaticLayout) 与系统 PdfDocument／Canvas 排版；PDF 固定 A4，PNG 宽 1080 px，保留既有 1,000 页／16,000 px 限制。基础表格上限八列，超过则保留原文；重复表头仅限不超过正文高度三分之一的表头。导出按支持的语法排版，不修改文稿或库格式；罕见字的视觉显示依赖字体。

1.10 使用 `WritingAppearance` 提供稳定主题 ID 与成对配色，`AppearanceSettings` 在原设置窗口中保留本次草稿，`PaperLines` 按 [TextView](https://developer.android.com/reference/android/widget/TextView)／[Layout](https://developer.android.com/reference/android/text/Layout) 的实际文字基线绘制。硬件记录画布可能覆盖整个长文视图，因此另外与视图可见区域相交，祖先滚动时重绘，卸载视图时移除监听；不会为整篇文稿逐行绘制横线。原生控件强调色和窗口背景与保存后的配色一致。横线不进入文字、Markdown 或备份；预览窗口不是永久草稿，未保存时重建会关闭。


1.11 使用 [Android 应用语言](https://developer.android.com/guide/topics/resources/app-languages) 和 [LocaleManager](https://developer.android.com/reference/android/app/LocaleManager)，在 Android 13+ 读取系统拥有的应用语言选择；旧版 Android 使用独立配置上下文。系统筛选、内置快捷项和错误类型分别保留稳定标识，只在展示层选择语言；用户正文及自定义名称不按文字匹配翻译。新增 `AppLanguage`、`UiText`、`UserError`、`LocalizationTest` 和 `LocalizationInstrumentedTest`；库格式保持 4。


1.12 的 AI 请求通过固定 DeepSeek HTTPS 地址发送，只读取当前文稿的有界片段。生成与文稿保存使用独立线程；取消、页面销毁或应用锁关闭 AI 窗口后丢弃迟到结果。原文快照及文稿 ID 校验后才允许插入，保留历史并对当前 Editable 作单次修改，以保留系统撤销。API Key 使用 Android Keystore AES-GCM 加密，与文稿数据分开；服务费用及请求范围在操作界面说明。默认 `deepseek-flash` 与可用模型由真实官方接口验证，详见 [DeepSeek API](https://api-docs.deepseek.com/api/create-chat-completion/)。库格式保持 4。
