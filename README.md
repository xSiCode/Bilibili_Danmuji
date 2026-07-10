# BiliBili_Danmuji — B站直播弹幕姬

[![GitHub release](https://img.shields.io/github/v/release/BanqiJane/Bilibili_Danmuji.svg)](https://github.com/BanqiJane/Bilibili_Danmuji/releases)
[![License](https://img.shields.io/badge/License-GPL--3.0-green.svg)](https://opensource.org/licenses/GPL-3.0)

基于 **Spring Boot 2.6.8** 的 Bilibili 直播弹幕姬，使用 WebSocket 协议连接 B站直播间，提供 **WebUI 可视化操作面板**。支持弹幕显示、自动感谢、智能回复、数据统计等全套直播辅助功能。

---

## 目录

- [快速开始](#快速开始)
- [运行环境](#运行环境)
- [获取方式](#获取方式)
- [功能概览](#功能概览)
- [页面与路由](#页面与路由)
- [配置说明](#配置说明)
  - [弹幕显示配置](#弹幕显示配置)
  - [礼物感谢姬](#礼物感谢姬)
  - [关注感谢姬](#关注感谢姬)
  - [欢迎姬](#欢迎姬)
  - [定时广告姬](#定时广告姬)
  - [上舰私信姬](#上舰私信姬)
  - [自动回复姬](#自动回复姬)
  - [黑名单姬](#黑名单姬)
  - [其他功能姬](#其他功能姬)
- [命令行参数](#命令行参数)
- [数据存储](#数据存储)
- [Docker 部署](#docker-部署)
- [开发指南](#开发指南)
  - [项目结构](#项目结构)
  - [本地构建](#本地构建)
  - [技术栈](#技术栈)
- [注意事项](#注意事项)
- [常见问题](#常见问题)
- [License](#license)

---

## 快速开始

### 1. 确保已安装 Java 8+

```bash
java -version
# 应输出 java version "1.8.0_xxx" 或更高
```

### 2. 下载并运行

**方式一：便携版（推荐，无需安装 Java）**
- 从 [Actions 构建页面](https://github.com/xSiCode/Bilibili_Danmuji/actions/workflows/build.yaml) 下载 `BiliBili_Danmuji_x.x.x_portable`
- 解压后双击 `run.bat`（Windows）

**方式二：JAR 包（需系统安装 Java）**
```bash
java -jar -Xms64m -Xmx128m BiliBili_Danmuji-2.7.0.6beta.jar --server.port=23333
```

### 3. 打开浏览器访问

```
http://127.0.0.1:23333
```

首次使用需扫码登录或填写 Cookie，然后输入直播间房间号连接即可。

---

## 运行环境

| 操作系统 | 支持情况 |
|---------|---------|
| Windows | ✅ 完全支持（含便携版） |
| Linux   | ✅ 完全支持（含 Docker） |
| macOS   | ✅ 完全支持 |

**浏览器要求：** Chrome / Firefox / Edge / Opera / Safari 最新版本（不支持 IE）

---

## 获取方式

| 制品 | 说明 |
|------|------|
| `BiliBili_Danmuji_x.x.x.jar` | 纯 JAR 包，需 Java 8+ |
| `BiliBili_Danmuji_x.x.x` | JAR + 启动脚本 (`run.bat` / `run.sh`)，需 Java |
| `BiliBili_Danmuji_x.x.x_portable` | 绿色便携版，自带 JRE，解压即用 |
| Docker 镜像 | `docker pull zzcabc/danmuji`（社区维护） |

所有制品通过 [GitHub Actions](https://github.com/xSiCode/Bilibili_Danmuji/actions/workflows/build.yaml) 自动构建，每次推送代码后自动产出。

---

## 功能概览

### 核心功能

| 功能模块 | 说明 |
|---------|------|
| 🎯 **弹幕显示** | 实时显示弹幕、礼物、舰长、关注、禁言等信息，支持勋章/UL/房管/舰长图标 |
| 🔐 **扫码登录** | 支持 B站 APP 扫码登录，自动获取 Cookie |
| 🍪 **Cookie 登录** | 手动填写 `bili_jct` + `SESSDATA` 登录 |
| 💬 **网页弹幕** | 浏览器端实时弹幕面板 + OBS 叠加层组件 |
| 📝 **弹幕保存** | 自动保存弹幕到本地 CSV + SQLite 数据库 |
| 🔄 **断线重连** | 网络异常自动重连直播间 |

### 自动互动姬

| 功能模块 | 说明 |
|---------|------|
| 🎁 **礼物感谢姬** | 自动发送感谢弹幕，支持延迟合并、模板、过滤、仅直播模式 |
| 👥 **关注感谢姬** | 实时感谢新关注，支持延迟合并、多人感谢 |
| 👋 **欢迎姬** | 欢迎进入直播间的观众，支持舰长/VIP 区分 |
| 📢 **定时广告姬** | 定时发送公告/广告弹幕，支持随机/顺序两种模式 |
| 💌 **上舰私信姬** | 新舰长自动发送私信，支持礼品码分发 |
| 🤖 **自动回复姬** | 关键字匹配自动回复，支持禁言、精确匹配、条件组合 |
| 🚫 **黑名单姬** | UID/关键词屏蔽，供其他姬共用 |
| 🔍 **关键词检测姬** | 弹幕关键词检测与记录 |
| ⛔ **自动拉黑姬** | 负黑用户自动拉黑处理 |
| 👀 **欢迎凝视姬** | 特定目标观众进入时提醒 |

### 数据与管理

| 功能模块 | 说明 |
|---------|------|
| 📊 **数据仪表盘** | 直播间信息、观看数、点赞数、在线数趋势图表 |
| 📈 **弹幕分析** | 弹幕数量趋势、发送排行、词频分析、弹幕质量分析 |
| 👥 **观众分析** | 观众进出统计、打分分布、进出榜/场次榜 |
| 🎯 **匹配管理** | 观众匹配记录、匹配分数分布分析 |
| 🔧 **房间管理** | 查看禁言列表、撤销禁言（需房管权限） |
| 🔎 **AICU 查询** | 第三方平台用户数据查询（缓存至 SQLite） |
| 📋 **账号池** | 多账号管理，支持账号切换、冷却控制 |

### 自动化任务

| 功能 | 说明 |
|------|------|
| ✅ **每日签到** | 自动完成直播签到任务 |
| 🏅 **每日打卡** | 按勋章列表自动打卡，完成首日 +100 亲密度 |
| 💓 **模拟在线** | 保持直播间在线状态（老爷可增加在线经验） |

### 其他特性

- **配置导入/导出** — JSON 格式配置备份与恢复
- **实时生效** — 大部分配置修改后无需重启
- **OBS 弹幕组件** — 专用弹幕叠加层页面，适配直播推流
- **足迹留印** — 轻量级观众进出记录，零 API 调用
- **陌生人检测** — 识别非粉丝/非勋章观众

---

## 页面与路由

所有页面均通过浏览器访问 `http://127.0.0.1:23333`：

| 路由 | 页面 | 说明 |
|------|------|------|
| `/` `/index` | 主页 | 弹幕显示 + 状态概览 |
| `/connect` | 连接房间 | 输入房间号连接直播间 |
| `/login` | 扫码登录 | B站 APP 扫码登录 |
| `/cookie_set` | Cookie 登录 | 手动填写 Cookie 信息 |
| `/settings` | 功能设置 | 所有功能姬的配置面板 |
| `/live-room` | 直播间管理 | 观看数/在线数/点赞数趋势分析 |
| `/danmaku` | 弹幕管理 | 弹幕搜索、排行、词频分析 |
| `/audience` | 观众管理 | 观众进出统计、打分分析 |
| `/blacklist` | 黑名单管理 | 黑白名单增删改查 |
| `/dashboard` | 数据仪表盘 | 综合数据概览 |
| `/management` | 房间管理 | 禁言列表、撤销禁言 |
| `/aicu` | AICU 查询 | 第三方用户数据查询 |
| `/account_pool` | 账号池 | 多账号管理 |
| `/danmu_widget` | 弹幕组件 | 嵌入式弹幕显示组件 |
| `/obs_danmaku` | OBS 弹幕 | 推流用弹幕叠加层 |

---

## 配置说明

所有配置通过 WebUI（`/settings` 页面）操作，修改后实时生效。配置数据加密存储在 `DanmujiProfile` 文件中，**请勿泄露给他人**。

### 弹幕显示配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| 弹幕开关 | 是否显示弹幕 | 开启 |
| 舰长/老爷图标 | 显示舰队和老爷图标 | 开启 |
| VIP 图标 | 显示 VIP 图标 | 开启 |
| 房管图标 | 显示房管图标 | 开启 |
| 勋章图标 | 显示粉丝勋章图标 | 关闭 |
| UL 等级图标 | 显示用户等级图标 | 关闭 |
| 本房间勋章屏蔽 | 屏蔽非本房间勋章的弹幕 | 关闭 |
| 禁言消息 | 显示房管禁言消息 | 开启 |
| 礼物消息 | 显示礼物消息 | 开启 |
| 免费礼物消息 | 显示免费礼物消息 | 开启 |
| 控制台打印 | 控制台输出弹幕 | 开启 |

### 礼物感谢姬

- **延迟感谢**：在设定的延时内统计礼物并合并感谢，新礼物或数量变动会刷新延时
- **屏蔽模式**（4 种）：
  1. 自定义礼物名称（支持黑名单/白名单切换）
  2. 屏蔽所有免费礼物
  3. 低价值礼物屏蔽
  4. 自定义规则
- **感谢模式**：单人单种 / 单人多种
- **人员过滤**：可按本房间勋章、不限本房间舰长过滤
- **模板参数**：支持多条感谢模板随机发送
- **舰队/红包/醒目留言**：支持舰队、红包、SC 感谢
- **仅直播中开启**：可选

> ⚠️ B站重复弹幕限制约 3 秒，建议延迟设置 ≥ 3 秒。延迟过高且礼物持续赠送可能造成刷屏。

### 关注感谢姬

- 真正的实时感谢
- 支持延迟合并、多人感谢
- 可屏蔽天选时刻下的关注
- 支持多条随机模板
- 仅直播中开启：可选

### 欢迎姬

- 欢迎进入直播间的观众
- 支持舰长/VIP 区分欢迎
- 人员过滤（本房间勋章、舰长）
- 延迟合并、多条模板
- 仅直播中开启：可选

### 定时广告姬

- 定时发送弹幕（用于广告/公告）
- 两种模式：随机发送 / 按顺序发送
- 支持随机时间间隔
- 仅直播中开启：可选

### 上舰私信姬

- 触发新舰长后自动发送私信
- 可在直播间发送提醒弹幕
- 支持礼品码分发（按舰长等级区分）
- 重复发送控制：本地记录已发送舰长，避免重复

**私信模板参数：**

| 参数 | 含义 |
|------|------|
| `%uName%` | 用户名称 |
| `%guardLevel%` | 舰长等级名称 |
| `%giftCode%` | 礼品码 |

**礼品码格式：** 按行分割。如按等级区分：`提督-GIFTCODE123`、`舰长-CODE456`。不带前缀则所有等级通用。

### 自动回复姬

- 多关键字 + 屏蔽词组合匹配
- 多条随机回复
- 精确匹配模式
- 条件组合：满足条件 A 或 B → 发送内容 C
- 人员过滤（本房间勋章、舰长）
- 关键字禁言功能（参数 `%BLOCK%{{小时}}`）
- 仅直播中开启：可选

**回复模板参数：**

| 参数 | 含义 |
|------|------|
| `%NAME%` | 触发者用户名 |
| `%FANS%` | 实时关注数 |
| `%WATHER%` | 实时观看人数 |
| `%LIKE%` | 实时点赞数 |
| `%LIVETIME%` | 当前直播时长 |
| `%HOT%` | 当前人气值 |
| `%TIME%` | 北京时间 |
| `%WEATHER%` | 推荐天气 |
| `%BLOCK%{{小时}}` | 禁言（1~720 小时，默认 1） |

### 黑名单姬

- 按 UID 或模糊名称屏蔽
- 黑名单/白名单切换
- 供礼物感谢姬、欢迎姬、关注感谢姬、自动回复姬共用
- 支持 CSV 导入/导出

### 其他功能姬

| 功能姬 | 说明 |
|--------|------|
| 直播状态姬 | 监控直播状态变化（0=未开播, 1=直播中, 2=轮播） |
| 定时姬 | 定时执行自定义任务 |
| 弹幕话术姬 | 弹幕发送模板管理 |
| 欢迎凝视姬 | 指定目标观众进入时触发提醒 |
| 关键词检测姬 | 弹幕关键词检测与记录 |
| 自动拉黑姬 | 根据规则自动拉黑负黑用户 |

---

## 命令行参数

### 端口配置

```bash
java -jar BiliBili_Danmuji-2.7.0.6beta.jar --server.port=23333
```

### 内存限制

```bash
java -jar -Xms64m -Xmx128m BiliBili_Danmuji-2.7.0.6beta.jar
```

### 日志目录

```bash
# 绝对路径
java -jar danmuji.jar --danmuji.log.dir="D:/Danmuji_log"

# 相对路径（相对于运行目录）
java -jar danmuji.jar --danmuji.log.dir="./Danmuji_log"
```

### 配置覆盖

通过 `--danmuji.conf.<字段名>=<值>` 覆盖任意 CenterSetConf 配置项：

```bash
# 指定房间号并关闭浏览器自动打开
java -jar danmuji.jar \
  --server.port=21201 \
  --room.id=12345 \
  --danmuji.conf.is_footprint_record=true \
  --danmuji.conf.win_auto_openSet=false
```

### 多开示例

```bash
# 同时监听多个直播间
java -jar danmuji.jar --server.port=21201 --room.id=11111
java -jar danmuji.jar --server.port=21202 --room.id=22222
```

---

## 数据存储

### 文件结构

首次运行后自动生成以下目录和文件：

```
项目目录/
├── DanmujiProfile          # 加密配置文件（含 Cookie，切勿泄露）
├── Danmuji_log/            # 弹幕日志 + CSV 数据文件
│   ├── {日期}_{房间号}_1_直播间信息.csv
│   ├── {日期}_{房间号}_2_弹幕信息.csv
│   ├── {日期}_{房间号}_4_观众信息.csv
│   ├── {日期}_{房间号}_5_匹配信息.csv
│   └── danmuji_viewer.db   # SQLite 数据库
├── guardFile/              # 舰长记录文件（上舰私信姬使用）
│   └── guards{房间号}
├── Danmuji_log/            # 日志文件夹
└── build-info.properties   # 构建时间戳（自动生成）
```

### SQLite 数据库

数据库文件：`Danmuji_log/danmuji_viewer.db`

包含以下数据表：
- **直播原始事件** — `danmaku`, `enter`, `follow`, `gift`, `guard`, `sc`, `vip`, `captain`, `block`
- **AICU 查询缓存** — `aicu_usermark`, `aicu_reply`, `aicu_videodm`, `aicu_livedm`
- **聚合统计** — 内存定期刷入（10s/60s 间隔）
- **黑白名单** — `local_black_white_list`
- **全文搜索** — FTS5 虚拟表

### 日志配置

默认日志目录为 `Documents/Danmuji_log/`（系统文档目录）。可通过 `--danmuji.log.dir` 参数自定义。

---

## Docker 部署

### 使用 Docker Compose

```bash
cd deploy/docker
# 编辑 .env 文件配置环境变量
docker-compose up -d
```

### 手动构建

```bash
docker build -t danmuji -f deploy/docker/Dockerfile .
docker run -d -p 23333:23333 -v /opt/store:/opt/store danmuji
```

**JVM 参数可通过环境变量调整：**
```bash
JAVA_OPS="-Xms64m -Xmx256m"
```

### 持久化

配置文件通过卷挂载持久化：`/opt/store` → 宿主机对应目录。

---

## 开发指南

### 项目结构

```
Bilibili_Danmuji/
├── src/main/java/xyz/acproject/danmuji/
│   ├── BiliBiliDanmujiApplication.java  # 启动类
│   ├── client/          # WebSocket 客户端代理
│   ├── component/       # Spring 组件（线程管理、任务注册）
│   ├── conf/            # 配置类（公共数据、中心配置）
│   │   ├── set/         # 各功能姬配置子类
│   │   └── CenterSetConf.java  # 中心配置（JSON 序列化）
│   ├── config/          # Spring 配置（WebSocket、定时任务、JSON）
│   ├── controller/      # Web 路由 + REST API
│   │   ├── WebController.java   # 主控制器（页面 + 全部 API）
│   │   └── DanmuWebsocket.java  # WebSocket 推送端点
│   ├── entity/          # 数据实体（弹幕、礼物、用户、房间等）
│   ├── enums/           # 枚举常量
│   ├── http/            # HTTP 请求工具（B站 API 调用）
│   ├── service/         # 服务层
│   ├── task/            # 定时任务
│   ├── thread/          # 多线程处理
│   │   ├── core/        # 核心线程（心跳、解析、重连）
│   │   └── ...          # 各功能姬线程
│   ├── tools/           # 工具类（文件、加密、数据解析）
│   └── utils/           # 通用工具（OkHttp、二维码、签名）
├── src/main/resources/
│   ├── templates/       # Thymeleaf 页面模板（17个页面）
│   ├── static/          # 静态资源（JS/CSS）
│   ├── application.yml  # Spring Boot 配置
│   └── log4j2.xml       # 日志配置
├── deploy/docker/       # Docker 部署文件
├── lib/                 # 本地依赖（javastruct）
├── build.gradle         # Gradle 构建脚本
└── settings.gradle      # Gradle 设置
```

### 本地构建

```bash
# Windows
gradlew.bat build -x test

# Linux/Mac
./gradlew build -x test
```

构建产物位于 `build/libs/BiliBili_Danmuji-{version}.jar`。

### 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 2.6.8 | 应用框架 |
| Java-WebSocket | 1.6.0 | B站弹幕协议通信 |
| Thymeleaf | — | 服务端模板渲染 |
| OkHttp 3 | 3.14.9 | HTTP 客户端 |
| FastJSON | 1.2.83 | JSON 解析 |
| SQLite JDBC | 3.45.3.0 | 本地数据库 |
| Protobuf | 4.31.1 | B站协议解包 |
| Brotli | 0.1.2 | 压缩数据解压 |
| ZXing | 3.4.0 | 二维码生成 |
| Bootstrap 5 | — | UI 框架 |
| Chart.js | 4.4.7 | 图表渲染 |
| SortableJS | 1.15.6 | 表格列拖曳 |

---

## 注意事项

### 🔒 安全

1. **配置文件 `DanmujiProfile` 包含加密后的 Cookie 信息**，请勿分享、上传或泄露给任何人。泄露即等同于交出账号控制权。
2. 不要将 `DanmujiProfile`、`guardFile/`、`Danmuji_log/` 提交到 Git 仓库（已在 `.gitignore` 中排除）。
3. 远程访问时建议使用防火墙限制来源 IP，或通过反向代理添加认证。

### ⚙️ 运行

1. **Java 版本**：需要 JDK/JRE 8 或更高版本。绿色便携版内置 JRE，无需额外安装。
2. **端口占用**：默认自动选择空闲端口（`port: 0`），也可通过 `--server.port=指定端口` 固定。
3. **浏览器兼容**：不支持 IE，请使用 Chrome/Firefox/Edge 等现代浏览器。
4. **首次启动**：会生成 `DanmujiProfile` 配置文件、`Danmuji_log/` 日志目录和 SQLite 数据库文件。

### 📡 B站相关

1. **Cookie 有效期**：B站 Cookie 有时效性，过期后需重新扫码登录或手动更新 Cookie。
2. **弹幕频率限制**：B站限制重复弹幕间隔约 3 秒，请合理设置感谢姬的延迟时间。
3. **未登录限制**：未登录状态下用户名可能显示为 `*` 号（B站灰度策略），部分功能不可用。
4. **API 变更风险**：B站接口可能随时调整，如遇功能异常请更新到最新版本。

### 💾 数据

1. **弹幕日志**：CSV 文件按日期和房间号命名，建议定期清理旧文件避免占用过大。
2. **数据库**：SQLite 使用 WAL 模式，正常退出不会损坏。异常断电极小概率需手动修复。
3. **设置备份**：定期使用 WebUI 的设置导出功能备份配置为 JSON 文件。

### 🔧 多开

1. 多开实例需要使用不同端口（`--server.port`）。
2. 多开时日志目录默认共享，如需隔离请使用 `--danmuji.log.dir` 指定不同目录。
3. 每个实例的配置文件 `DanmujiProfile` 独立。

---

## 常见问题

### Q: 启动后浏览器没有自动打开设置页面？

A: 在设置中确认「自动打开设置页面」选项已开启，或手动访问 `http://127.0.0.1:23333`。也可通过 `--danmuji.conf.win_auto_openSet=false` 禁用此行为。

### Q: 连接房间后没有弹幕？

A: 检查：① 是否已登录（扫码或 Cookie）；② 房间号是否正确；③ 直播间是否正在直播；④ 防火墙是否允许 Java 的网络连接。

### Q: Cookie 如何获取？

A: 浏览器登录 B站后，F12 → Application → Cookies → 复制 `bili_jct` 和 `SESSDATA` 的值，在 `/cookie_set` 页面填入即可。

### Q: 如何更新到新版本？

A: 下载新版本 JAR 包替换旧文件即可，`DanmujiProfile` 配置文件兼容。版本更新不会丢失配置数据。

### Q: 弹幕姬占用内存过高？

A: 建议添加 JVM 内存限制参数：`-Xms64m -Xmx128m`。绿色便携版和启动脚本已内置此参数。

### Q: 如何在一台机器上运行多个弹幕姬？

A: 使用不同端口启动多个实例，参见[命令行参数](#命令行参数)中的多开示例。

---

## License

本项目基于 **GPL-3.0 License** 开源协议。

原始项目地址：[https://github.com/BanqiJane/Bilibili_Danmuji](https://github.com/BanqiJane/Bilibili_Danmuji)
