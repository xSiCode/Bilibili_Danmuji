# SQLite 数据库表结构文档

> **数据库文件**: `{LogPathConf.getLogDir()}/danmuji_viewer.db`  
> **驱动**: `org.sqlite.JDBC`（JDBC 直连，无 ORM）  
> **模式**: WAL（Write-Ahead Logging），读不阻塞写  
> **PRAGMA**: `synchronous=NORMAL`, `busy_timeout=5000`, `cache_size=-64000`, `foreign_keys=ON`  
> **连接方式**: `DanmujiDatabase.getConnection()` — 每次返回新连接，调用方负责关闭

---

## 表分类总览

| 分类 | 表数量 | 说明 |
|------|--------|------|
| 直播原始事件 | 9 | 弹幕、进入、关注、礼物、上舰、SC、老爷、舰长、禁言 |
| AICU 查询缓存 | 4 | 第三方用户数据缓存（分页 JSON 存储） |
| 聚合统计表 | 7 | CSV 替代表，内存定期刷入（10s / 60s） |
| 系统管理 | 2 | 迁移日志、黑/白名单 |
| 全文搜索 | 1 | FTS5 虚拟表 |

---

## 一、直播原始事件表（9 张）

这些表由 `ParseMessageThread` → `*Recorder` 类实时写入，记录直播间每条原始事件。

### 1. `danmaku` — 弹幕记录

| 写入方式 | `DanmakuRecorder`（批量，1s 间隔） |
|---------|----------------------------------|

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT | 主播名 |
| `uid` | BIGINT NOT NULL | 用户 UID |
| `uname` | TEXT | 用户名 |
| `content` | TEXT | 弹幕内容 |
| `msg_type` | INTEGER DEFAULT 0 | 消息类型（0=文本 1=表情） |
| `is_emoticon` | INTEGER DEFAULT 0 | 是否表情 |
| `emoticon_name` | TEXT | 表情名称 |
| `emoticon_url` | TEXT | 表情 URL |
| `vip` | INTEGER DEFAULT 0 | 是否 VIP |
| `svip` | INTEGER DEFAULT 0 | 是否 SVIP |
| `manager` | INTEGER DEFAULT 0 | 是否房管 |
| `uidentity` | INTEGER | 用户身份标识 |
| `iphone` | INTEGER DEFAULT 0 | 是否 iPhone 客户端 |
| `guard_level` | INTEGER DEFAULT 0 | 舰队等级 |
| `medal_level` | INTEGER | 粉丝勋章等级 |
| `medal_name` | TEXT | 勋章名 |
| `medal_anchor` | TEXT | 勋章主播 |
| `medal_room` | BIGINT | 勋章房间 |
| `ulevel` | INTEGER | 用户等级 |
| `ulevel_rank` | TEXT | 用户等级排名 |
| `old_title` | TEXT | 旧头衔 |
| `title` | TEXT | 当前头衔 |
| `timestamp` | BIGINT NOT NULL | 事件时间戳（毫秒） |
| `created_at` | TEXT | 入库时间（本地时间） |

**索引**: `uid`, `timestamp`, `room_id`, `uname`  
**FTS5**: `danmaku_fts(content, uname)` — 全文搜索虚拟表，INSERT 触发器自动同步

---

### 2. `enter_events` — 进入直播间事件

| 写入方式 | `EnterRecorder` |
|---------|----------------|

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT | 主播名 |
| `uid` | BIGINT NOT NULL | 用户 UID |
| `uname` | TEXT | 用户名 |
| `uname_color` | TEXT | 用户名颜色 |
| `timestamp` | BIGINT NOT NULL | 事件时间戳（毫秒） |
| `score` | BIGINT | 用户积分 |
| `medal_level` | INTEGER | 勋章等级 |
| `medal_name` | TEXT | 勋章名 |
| `medal_anchor` | TEXT | 勋章主播 |
| `medal_room` | BIGINT | 勋章房间 |
| `medal_color` | TEXT | 勋章颜色 |
| `guard_level` | INTEGER DEFAULT 0 | 舰队等级 |
| `is_lighted` | INTEGER DEFAULT 0 | 勋章是否点亮 |
| `identities` | TEXT | 身份标识列表 |
| `created_at` | TEXT | 入库时间 |

**索引**: `uid`, `timestamp`, `uname`

---

### 3. `follow_events` — 关注事件

| 写入方式 | `FollowRecorder` |
|---------|-----------------|

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT | 主播名 |
| `uid` | BIGINT NOT NULL | 关注者 UID |
| `uname` | TEXT | 关注者用户名 |
| `uname_color` | TEXT | 用户名颜色 |
| `timestamp` | BIGINT NOT NULL | 事件时间戳（毫秒） |
| `score` | BIGINT | 积分 |
| `medal_level` | INTEGER | 勋章等级 |
| `medal_name` | TEXT | 勋章名 |
| `medal_anchor` | TEXT | 勋章主播 |
| `medal_room` | BIGINT | 勋章房间 |
| `medal_color` | TEXT | 勋章颜色 |
| `guard_level` | INTEGER DEFAULT 0 | 舰队等级 |
| `is_lighted` | INTEGER DEFAULT 0 | 勋章是否点亮 |
| `identities` | TEXT | 身份标识列表 |
| `created_at` | TEXT | 入库时间 |

**索引**: `uid`, `timestamp`, `uname`

---

### 4. `gift_detail` — 礼物明细

| 写入方式 | `GiftDetailRecorder` |
|---------|---------------------|

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT | 主播名 |
| `uid` | BIGINT NOT NULL | 送礼者 UID |
| `uname` | TEXT | 送礼者用户名 |
| `face` | TEXT | 头像 URL |
| `gift_id` | INTEGER | 礼物 ID |
| `gift_name` | TEXT NOT NULL | 礼物名称 |
| `gift_type` | INTEGER | 礼物类型 |
| `num` | INTEGER DEFAULT 1 | 数量 |
| `price` | INTEGER | 单价（瓜子） |
| `total_coin` | BIGINT | 总价 |
| `coin_type` | INTEGER | 货币类型（金瓜子/银瓜子） |
| `action` | TEXT | 动作 |
| `guard_level` | INTEGER DEFAULT 0 | 舰队等级 |
| `medal_level` | INTEGER | 勋章等级 |
| `medal_name` | TEXT | 勋章名 |
| `medal_anchor` | TEXT | 勋章主播 |
| `medal_color` | TEXT | 勋章颜色 |
| `timestamp` | BIGINT NOT NULL | 事件时间戳（毫秒） |
| `source` | TEXT DEFAULT 'gift' | 来源 |
| `created_at` | TEXT | 入库时间 |

**索引**: `uid`, `timestamp`, `uname`

---

### 5. `guard_buy` — 上舰记录

| 写入方式 | `GuardBuyRecorder` |
|---------|-------------------|

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT | 主播名 |
| `uid` | BIGINT NOT NULL | 上舰者 UID |
| `uname` | TEXT | 用户名 |
| `guard_level` | INTEGER | 舰队等级（1=舰长 2=提督 3=总督） |
| `num` | INTEGER | 数量 |
| `price` | INTEGER | 价格 |
| `gift_name` | TEXT | 礼物名称 |
| `start_time` | BIGINT | 开始时间（毫秒） |
| `end_time` | BIGINT | 结束时间（毫秒） |
| `created_at` | TEXT | 入库时间 |

**索引**: `uid`, `uname`

---

### 6. `super_chat` — 醒目留言

| 写入方式 | `SuperChatRecorder` |
|---------|--------------------|

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT | 主播名 |
| `uid` | BIGINT NOT NULL | 用户 UID |
| `uname` | TEXT | 用户名 |
| `message` | TEXT | SC 内容 |
| `price` | INTEGER | 金额 |
| `keep_time` | INTEGER | 停留时长（秒） |
| `start_time` | BIGINT | 开始时间（毫秒） |
| `end_time` | BIGINT | 结束时间（毫秒） |
| `gift_name` | TEXT | 礼物名称 |
| `medal_level` | INTEGER | 勋章等级 |
| `medal_name` | TEXT | 勋章名 |
| `medal_color` | TEXT | 勋章颜色 |
| `background_color` | TEXT | SC 背景色 |
| `created_at` | TEXT | 入库时间 |

**索引**: `uid`, `uname`

---

### 7. `welcome_vip` — 欢迎老爷

| 写入方式 | `WelcomeVipRecorder` |
|---------|---------------------|

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT | 主播名 |
| `uid` | BIGINT NOT NULL | 用户 UID |
| `uname` | TEXT | 用户名 |
| `vip` | INTEGER DEFAULT 0 | 是否 VIP |
| `svip` | INTEGER DEFAULT 0 | 是否 SVIP |
| `is_admin` | INTEGER DEFAULT 0 | 是否管理员 |
| `timestamp` | BIGINT NOT NULL | 事件时间戳（毫秒） |
| `created_at` | TEXT | 入库时间 |

**索引**: `uid`, `uname`

---

### 8. `welcome_guard` — 欢迎舰长

| 写入方式 | `WelcomeGuardRecorder` |
|---------|-----------------------|

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT | 主播名 |
| `uid` | BIGINT NOT NULL | 用户 UID |
| `uname` | TEXT | 用户名 |
| `guard_level` | INTEGER | 舰队等级 |
| `timestamp` | BIGINT NOT NULL | 事件时间戳（毫秒） |
| `created_at` | TEXT | 入库时间 |

**索引**: `uid`, `uname`

---

### 9. `block_msg` — 禁言记录

| 写入方式 | `BlockMsgRecorder` |
|---------|-------------------|

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT | 主播名 |
| `uid` | BIGINT NOT NULL | 被禁言者 UID |
| `uname` | TEXT | 用户名 |
| `operator` | INTEGER | 操作者 UID |
| `timestamp` | BIGINT NOT NULL | 事件时间戳（毫秒） |
| `created_at` | TEXT | 入库时间 |

**索引**: `uid`, `uname`

---

## 二、AICU 查询缓存表（4 张）

由 `AicuService` 管理，将从 aicu.cc API 获取的用户数据分页存入 SQLite，避免重复请求。

### 10. `aicu_usermark`

| 列名 | 类型 | 说明 |
|------|------|------|
| `uid` | BIGINT PK | 用户 UID |
| `data_json` | TEXT | 用户标记 JSON（含历史用户名 hname 等） |
| `fetch_time` | BIGINT | 拉取时间（Unix 秒） |

### 11. `aicu_reply`

| 列名 | 类型 | 说明 |
|------|------|------|
| `uid` | BIGINT NOT NULL | 用户 UID |
| `pn` | INTEGER NOT NULL DEFAULT 1 | 页码 |
| `data_json` | TEXT | 当前页评论数据 JSON |
| `total_count` | INTEGER DEFAULT 0 | 总评论数 |
| `fetch_time` | BIGINT | 拉取时间（Unix 秒） |

**主键**: `(uid, pn)`

### 12. `aicu_videodm`

| 列名 | 类型 | 说明 |
|------|------|------|
| `uid` | BIGINT NOT NULL | 用户 UID |
| `pn` | INTEGER NOT NULL DEFAULT 1 | 页码 |
| `data_json` | TEXT | 当前页视频弹幕数据 JSON |
| `total_count` | INTEGER DEFAULT 0 | 总弹幕数 |
| `fetch_time` | BIGINT | 拉取时间（Unix 秒） |

**主键**: `(uid, pn)`

### 13. `aicu_livedm`

| 列名 | 类型 | 说明 |
|------|------|------|
| `uid` | BIGINT NOT NULL | 用户 UID |
| `pn` | INTEGER NOT NULL DEFAULT 1 | 页码 |
| `data_json` | TEXT | 当前页直播弹幕数据 JSON |
| `total_count` | INTEGER DEFAULT 0 | 总弹幕数 |
| `fetch_time` | BIGINT | 拉取时间（Unix 秒） |

**主键**: `(uid, pn)`

---

## 三、聚合统计表（7 张）

替代 CSV 文件的读取和修改操作。由各 `*Tools` / `*Service` 类在定时 CSV 刷盘时同步写入 SQLite（双写），CSV 文件仅保留供人工查看。

> **写入机制**: 所有 `flushToSqlite()` 均使用显式事务（`setAutoCommit(false)` + `commit()`/`rollback()`），确保批量写入原子性。

### 14. `room_info_series` — 直播间信息时间序列

| 对应 CSV | `_1_直播间信息.csv` |
|---------|-------------------|
| 写入方式 | `RoomInfoLogTools.flushToSqlite()`（全量覆写，10s 间隔） |
| Web 列表 | `/listCsvFiles` |
| 数据查询 | `/readCsvData` |

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT NOT NULL DEFAULT '' | 主播名 |
| `time_key` | TEXT NOT NULL | 时间键（分钟精度 `yyyy-MM-dd HH:mm`） |
| `watch_count` | BIGINT NOT NULL DEFAULT 0 | 累计观看数 |
| `online_count` | BIGINT NOT NULL DEFAULT 0 | 在线人数 |
| `like_count` | BIGINT NOT NULL DEFAULT 0 | 点赞数 |

**唯一约束**: `(room_id, anchor_name, time_key)`  
**索引**: `room_id, anchor_name`

---

### 15. `gift_summary` — 礼物聚合记录

| 对应 CSV | `_3_礼物信息.csv` |
|---------|-------------------|
| 写入方式 | `GiftLogTools.flushToSqlite()`（全量覆写，60s 间隔） |
| Web 列表 | `/listGiftCsvFiles` |
| 数据查询 | `/readGiftCsvData` |

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT NOT NULL DEFAULT '' | 主播名 |
| `uid` | BIGINT NOT NULL | 用户 UID |
| `uname` | TEXT NOT NULL DEFAULT '' | 用户名 |
| `gift_name` | TEXT NOT NULL | 礼物名称 |
| `total_price` | BIGINT NOT NULL DEFAULT 0 | 累计金额 |
| `count` | INTEGER NOT NULL DEFAULT 0 | 赠送次数 |
| `latest_time` | BIGINT NOT NULL DEFAULT 0 | 最近赠送时间（毫秒） |

**唯一约束**: `(room_id, anchor_name, uid, gift_name)`  
**索引**: `uid`, `room_id, anchor_name`, `uname`

---

### 16. `visitor_summary` — 观众聚合记录

| 对应 CSV | `_4_观众信息.csv` |
|---------|-------------------|
| 写入方式 | `VisitorCountTools.flushToSqlite()`（增量更新，60s 间隔） |
| Web 列表 | `/listVisitorCsvFiles` |
| 数据查询 | `/readVisitorCsvData` |

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT NOT NULL DEFAULT '' | 主播名 |
| `uid` | BIGINT NOT NULL | 用户 UID |
| `uname` | TEXT NOT NULL DEFAULT '' | 用户名 |
| `score` | INTEGER NOT NULL DEFAULT 0 | 积分 |
| `score_type` | TEXT NOT NULL DEFAULT '' | 积分类型 |
| `count` | INTEGER NOT NULL DEFAULT 0 | 进入次数 |
| `in_pn_table` | INTEGER NOT NULL DEFAULT 0 | 是否在判定表中（0/1） |
| `session` | INTEGER NOT NULL DEFAULT 1 | 场次 |
| `latest_entry_time` | BIGINT NOT NULL DEFAULT 0 | 最近进入时间（毫秒） |

**唯一约束**: `(room_id, anchor_name, uid)`  
**索引**: `room_id, anchor_name`, `uname`

---

### 17. `match_summary` — 匹配聚合记录

| 对应 CSV | `_5_匹配信息.csv` |
|---------|-------------------|
| 写入方式 | `MatchCountTools.flushToSqlite()`（全量覆写，60s 间隔） |
| Web 列表 | `/listMatchCsvFiles` |
| 数据查询 | `/readMatchCsvData` |

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT NOT NULL DEFAULT '' | 主播名 |
| `matched_uid` | BIGINT NOT NULL | 匹配用户 UID |
| `matched_name` | TEXT NOT NULL DEFAULT '' | 匹配用户名 |
| `score` | INTEGER NOT NULL DEFAULT 0 | 匹配分 |
| `count` | INTEGER NOT NULL DEFAULT 0 | 匹配次数 |
| `latest_match_time` | BIGINT NOT NULL DEFAULT 0 | 最近匹配时间（毫秒） |

**唯一约束**: `(room_id, anchor_name, matched_uid)`  
**索引**: `room_id, anchor_name`

---

### 18. `follow_summary` — 关注聚合记录

| 对应 CSV | `_6_关注人信息.csv` |
|---------|-------------------|
| 写入方式 | `FollowingCountTools.flushToSqlite()`（全量覆写，60s 间隔） |
| Web 列表 | `/listFollowCsvFiles` |
| 数据查询 | `/readFollowCsvData` |

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT NOT NULL DEFAULT '' | 主播名 |
| `uid` | BIGINT NOT NULL | 用户 UID |
| `uname` | TEXT NOT NULL DEFAULT '' | 用户名 |
| `count` | INTEGER NOT NULL DEFAULT 0 | 关注次数 |
| `latest_time` | BIGINT NOT NULL DEFAULT 0 | 最近关注时间（毫秒） |

**唯一约束**: `(room_id, anchor_name, uid)`  
**索引**: `room_id, anchor_name`

---

### 19. `stranger_viewer` — 陌生观众记录

| 对应 CSV | `_7_陌生观众.csv` |
|---------|-------------------|
| 写入方式 | `StrangerViewerService.flushToSqlite()`（增量更新，60s 间隔） |
| Web 列表 | `/listStrangerCsvFiles` |
| 数据查询 | `/strangerViewerData` |

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL | 直播间 ID |
| `anchor_name` | TEXT NOT NULL DEFAULT '' | 主播名 |
| `uid` | BIGINT NOT NULL | 用户 UID |
| `name` | TEXT NOT NULL DEFAULT '' | 用户名 |
| `face` | TEXT DEFAULT '' | 头像 URL |
| `score` | INTEGER NOT NULL DEFAULT 0 | 积分 |
| `score_types` | TEXT NOT NULL DEFAULT '' | 积分类型 |
| `count` | INTEGER NOT NULL DEFAULT 0 | 出现次数 |
| `session` | INTEGER NOT NULL DEFAULT 1 | 场次 |
| `blocked` | INTEGER NOT NULL DEFAULT 0 | 是否拉黑（0/1） |
| `time` | BIGINT NOT NULL DEFAULT 0 | 最近出现时间（毫秒） |

**唯一约束**: `(room_id, anchor_name, uid)`  
**索引**: `room_id, anchor_name`

---

### 20. `footprint` — 足迹留印

| 对应 CSV | `_11_足迹留印.csv` |
|---------|-------------------|
| 写入方式 | `FootprintFileTools.flushToSqlite()`（追加写入，1s 批量） |

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `room_id` | BIGINT NOT NULL DEFAULT 0 | 直播间 ID |
| `anchor_name` | TEXT NOT NULL DEFAULT '' | 主播名 |
| `auid` | BIGINT NOT NULL DEFAULT 0 | 主播 UID |
| `uid` | BIGINT NOT NULL | 足迹用户 UID |
| `uname` | TEXT NOT NULL DEFAULT '' | 用户名 |
| `utime` | BIGINT NOT NULL DEFAULT 0 | 足迹时间戳（毫秒） |

**索引**: `room_id`, `uid`  
> 注意：此表无唯一约束（可重复），追加写入不覆盖历史。

---

## 四、系统管理表（2 张）

### 21. `black_white_list` — 本地黑/白名单

| 对应 CSV | `set/本地黑名单.csv`、`set/本地白名单.csv` |
|---------|------------------------------------------|
| 写入方式 | `LocalBlackWhiteListService.flushToSqlite()`（全量覆写，60s 间隔） |
| 存储目录 | `ProFileTools.getStoreDir()/set/` |

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK | 自增主键 |
| `list_type` | TEXT NOT NULL | 列表类型，CHECK 约束：`'black'` 或 `'white'` |
| `uid` | BIGINT NOT NULL | 用户 UID |
| `name` | TEXT NOT NULL DEFAULT '' | 用户名 |
| `create_time` | BIGINT NOT NULL DEFAULT 0 | 创建时间（毫秒） |
| `update_time` | BIGINT NOT NULL DEFAULT 0 | 更新时间（毫秒） |
| `score` | INTEGER NOT NULL DEFAULT 0 | 分数 |
| `score_type` | TEXT NOT NULL DEFAULT '' | 分数类型 |
| `room_id` | BIGINT NOT NULL DEFAULT 0 | 来源房间 ID |
| `count` | INTEGER NOT NULL DEFAULT 0 | 计数 |

**唯一约束**: `(list_type, uid)`

---

### 22. `_migration_log` — CSV 迁移状态

| 说明 | 记录 CSV→SQLite 一次性迁移是否已完成 |
|------|--------------------------------------|

| 列名 | 类型 | 说明 |
|------|------|------|
| `table_name` | TEXT PK | 目标表名 |
| `migrated_at` | TEXT | 迁移完成时间（本地时间） |

迁移完成后，`DanmujiDatabase.init()` 不再自动调用 `DanmujiMigration.migrateIfNeeded()`。如需手动重新迁移，调用 `DanmujiMigration.migrateIfNeeded()`。

---

## 五、全文搜索（1 个 FTS5 虚拟表）

### `danmaku_fts` — 弹幕全文索引

| 类型 | FTS5 虚拟表 |
|------|------------|
| 内容表 | `danmaku` |
| 索引列 | `content`, `uname` |

INSERT 触发器 `danmaku_ai` 在弹幕写入 `danmaku` 时自动同步到 FTS5 表。失败不影响核心功能（SQLite 编译时 FTS5 可选）。

---

## 索引清单

### 普通索引

| 表 | 索引名 | 列 |
|----|--------|-----|
| `danmaku` | `idx_danmaku_uid` | `uid` |
| `danmaku` | `idx_danmaku_ts` | `timestamp` |
| `danmaku` | `idx_danmaku_room` | `room_id` |
| `danmaku` | `idx_danmaku_uname` | `uname` |
| `enter_events` | `idx_enter_uid` | `uid` |
| `enter_events` | `idx_enter_ts` | `timestamp` |
| `enter_events` | `idx_enter_uname` | `uname` |
| `follow_events` | `idx_follow_uid` | `uid` |
| `follow_events` | `idx_follow_ts` | `timestamp` |
| `follow_events` | `idx_follow_uname` | `uname` |
| `gift_detail` | `idx_gift_detail_uid` | `uid` |
| `gift_detail` | `idx_gift_detail_ts` | `timestamp` |
| `gift_detail` | `idx_gift_detail_uname` | `uname` |
| `guard_buy` | `idx_guard_buy_uid` | `uid` |
| `guard_buy` | `idx_guard_buy_uname` | `uname` |
| `super_chat` | `idx_super_chat_uid` | `uid` |
| `super_chat` | `idx_super_chat_uname` | `uname` |
| `welcome_vip` | `idx_welcome_vip_uid` | `uid` |
| `welcome_vip` | `idx_welcome_vip_uname` | `uname` |
| `welcome_guard` | `idx_welcome_guard_uid` | `uid` |
| `welcome_guard` | `idx_welcome_guard_uname` | `uname` |
| `block_msg` | `idx_block_msg_uid` | `uid` |
| `block_msg` | `idx_block_msg_uname` | `uname` |
| `room_info_series` | `idx_room_info_room` | `room_id, anchor_name` |
| `gift_summary` | `idx_gift_summary_room` | `room_id, anchor_name` |
| `gift_summary` | `idx_gift_summary_uname` | `uname` |
| `visitor_summary` | `idx_visitor_summary_room` | `room_id, anchor_name` |
| `visitor_summary` | `idx_visitor_summary_uname` | `uname` |
| `match_summary` | `idx_match_summary_room` | `room_id, anchor_name` |
| `follow_summary` | `idx_follow_summary_room` | `room_id, anchor_name` |
| `stranger_viewer` | `idx_stranger_viewer_room` | `room_id, anchor_name` |
| `footprint` | `idx_footprint_room` | `room_id` |
| `footprint` | `idx_footprint_uid` | `uid` |

### 唯一索引

| 表 | 索引名 | 列 |
|----|--------|-----|
| `room_info_series` | `uq_room_info_series` | `room_id, anchor_name, time_key` |
| `gift_summary` | `uq_gift_summary` | `room_id, anchor_name, uid, gift_name` |
| `visitor_summary` | `uq_visitor_summary` | `room_id, anchor_name, uid` |
| `match_summary` | `uq_match_summary` | `room_id, anchor_name, matched_uid` |
| `follow_summary` | `uq_follow_summary` | `room_id, anchor_name, uid` |
| `stranger_viewer` | `uq_stranger_viewer` | `room_id, anchor_name, uid` |
| `black_white_list` | `uq_black_white_list` | `list_type, uid` |

---

## 数据流向图

```
B站 WebSocket 直播消息
        │
        ▼
  ParseMessageThread（消息解析）
        │
        ├──→ DanmakuRecorder ──→ danmaku（批量 1s）
        ├──→ EnterRecorder ──→ enter_events
        ├──→ FollowRecorder ──→ follow_events
        ├──→ GiftDetailRecorder ──→ gift_detail
        ├──→ GuardBuyRecorder ──→ guard_buy
        ├──→ SuperChatRecorder ──→ super_chat
        ├──→ WelcomeVipRecorder ──→ welcome_vip
        ├──→ WelcomeGuardRecorder ──→ welcome_guard
        └──→ BlockMsgRecorder ──→ block_msg

        │
        ▼
  内存聚合（Tools/Service 类）
        │
        ├──→ CSV 文件（保留供人工查看）
        └──→ flushToSqlite()（定时 10s / 60s）
                ├──→ room_info_series
                ├──→ gift_summary
                ├──→ visitor_summary
                ├──→ match_summary
                ├──→ follow_summary
                ├──→ stranger_viewer
                ├──→ footprint
                └──→ black_white_list

  AicuService（第三方 API）
        └──→ aicu_usermark / aicu_reply / aicu_videodm / aicu_livedm

  DanmujiMigration（已完成，已禁用自动执行）
        └──→ 所有聚合表的 CSR → SQLite 一次性导入
```

---

## 维护说明

| 操作 | 方法 |
|------|------|
| 获取连接 | `DanmujiDatabase.getConnection()`（try-with-resources） |
| 数据库路径 | `DanmujiDatabase.getDbPath()` |
| CSV 文件名解析 | `DanmujiMigration.parseRoomAnchorStr(filename)` |
| Web 端文件列表 | `WebController.listFromSqlite(req, table, suffix)` |
| 手动重新迁移 | `DanmujiMigration.migrateIfNeeded()`（已禁用自动调用） |
