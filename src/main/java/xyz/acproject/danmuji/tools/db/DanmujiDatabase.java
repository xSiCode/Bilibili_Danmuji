package xyz.acproject.danmuji.tools.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.LogPathConf;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * SQLite 数据库连接管理器。
 * 所有弹幕姬实例共享同一个 .db 文件，WAL 模式保证多进程并发写入。
 */
public class DanmujiDatabase {
    private static final Logger LOGGER = LogManager.getLogger(DanmujiDatabase.class);
    private static volatile DanmujiDatabase instance;
    private static String dbPath;

    private DanmujiDatabase() {
        init();
    }

    public static DanmujiDatabase getInstance() {
        if (instance == null) {
            synchronized (DanmujiDatabase.class) {
                if (instance == null) {
                    instance = new DanmujiDatabase();
                }
            }
        }
        return instance;
    }

    private void init() {
        try {
            // 数据库文件放在统一日志目录下
            String dir = LogPathConf.getLogDir();
            new File(dir).mkdirs();
            dbPath = dir + File.separator + "danmuji_viewer.db";

            // 加载 SQLite 驱动
            Class.forName("org.sqlite.JDBC");

            // 创建表结构
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                // WAL 模式 — 读不阻塞写，适合多进程并发
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA busy_timeout=5000");
                stmt.execute("PRAGMA cache_size=-64000");
                stmt.execute("PRAGMA foreign_keys=ON");

                // ========================
                // 表1：弹幕
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS danmaku (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT," +
                    "  uid           BIGINT  NOT NULL," +
                    "  uname         TEXT," +
                    "  content       TEXT," +
                    "  msg_type      INTEGER DEFAULT 0," +
                    "  is_emoticon   INTEGER DEFAULT 0," +
                    "  emoticon_name TEXT," +
                    "  emoticon_url  TEXT," +
                    "  vip           INTEGER DEFAULT 0," +
                    "  svip          INTEGER DEFAULT 0," +
                    "  manager       INTEGER DEFAULT 0," +
                    "  uidentity     INTEGER," +
                    "  iphone        INTEGER DEFAULT 0," +
                    "  guard_level   INTEGER DEFAULT 0," +
                    "  medal_level   INTEGER," +
                    "  medal_name    TEXT," +
                    "  medal_anchor  TEXT," +
                    "  medal_room    BIGINT," +
                    "  ulevel        INTEGER," +
                    "  ulevel_rank   TEXT," +
                    "  old_title     TEXT," +
                    "  title         TEXT," +
                    "  timestamp     BIGINT  NOT NULL," +
                    "  created_at    TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // ========================
                // 表2：进入事件
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS enter_events (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT," +
                    "  uid           BIGINT  NOT NULL," +
                    "  uname         TEXT," +
                    "  uname_color   TEXT," +
                    "  timestamp     BIGINT  NOT NULL," +
                    "  score         BIGINT," +
                    "  medal_level   INTEGER," +
                    "  medal_name    TEXT," +
                    "  medal_anchor  TEXT," +
                    "  medal_room    BIGINT," +
                    "  medal_color   TEXT," +
                    "  guard_level   INTEGER DEFAULT 0," +
                    "  is_lighted    INTEGER DEFAULT 0," +
                    "  identities    TEXT," +
                    "  created_at    TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // ========================
                // 表3：关注事件
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS follow_events (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT," +
                    "  uid           BIGINT  NOT NULL," +
                    "  uname         TEXT," +
                    "  uname_color   TEXT," +
                    "  timestamp     BIGINT  NOT NULL," +
                    "  score         BIGINT," +
                    "  medal_level   INTEGER," +
                    "  medal_name    TEXT," +
                    "  medal_anchor  TEXT," +
                    "  medal_room    BIGINT," +
                    "  medal_color   TEXT," +
                    "  guard_level   INTEGER DEFAULT 0," +
                    "  is_lighted    INTEGER DEFAULT 0," +
                    "  identities    TEXT," +
                    "  created_at    TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // ========================
                // 表4：礼物明细
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS gift_detail (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT," +
                    "  uid           BIGINT  NOT NULL," +
                    "  uname         TEXT," +
                    "  face          TEXT," +
                    "  gift_id       INTEGER," +
                    "  gift_name     TEXT    NOT NULL," +
                    "  gift_type     INTEGER," +
                    "  num           INTEGER DEFAULT 1," +
                    "  price         INTEGER," +
                    "  total_coin    BIGINT," +
                    "  coin_type     INTEGER," +
                    "  action        TEXT," +
                    "  guard_level   INTEGER DEFAULT 0," +
                    "  medal_level   INTEGER," +
                    "  medal_name    TEXT," +
                    "  medal_anchor  TEXT," +
                    "  medal_color   TEXT," +
                    "  timestamp     BIGINT  NOT NULL," +
                    "  source        TEXT DEFAULT 'gift'," +
                    "  created_at    TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // ========================
                // 表5：上舰记录
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS guard_buy (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT," +
                    "  uid           BIGINT  NOT NULL," +
                    "  uname         TEXT," +
                    "  guard_level   INTEGER," +
                    "  num           INTEGER," +
                    "  price         INTEGER," +
                    "  gift_name     TEXT," +
                    "  start_time    BIGINT," +
                    "  end_time      BIGINT," +
                    "  created_at    TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // ========================
                // 表6：醒目留言
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS super_chat (" +
                    "  id              INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id         BIGINT  NOT NULL," +
                    "  anchor_name     TEXT," +
                    "  uid             BIGINT  NOT NULL," +
                    "  uname           TEXT," +
                    "  message         TEXT," +
                    "  price           INTEGER," +
                    "  keep_time       INTEGER," +
                    "  start_time      BIGINT," +
                    "  end_time        BIGINT," +
                    "  gift_name       TEXT," +
                    "  medal_level     INTEGER," +
                    "  medal_name      TEXT," +
                    "  medal_color     TEXT," +
                    "  background_color TEXT," +
                    "  created_at      TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // ========================
                // 表7：欢迎老爷
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS welcome_vip (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT," +
                    "  uid           BIGINT  NOT NULL," +
                    "  uname         TEXT," +
                    "  vip           INTEGER DEFAULT 0," +
                    "  svip          INTEGER DEFAULT 0," +
                    "  is_admin      INTEGER DEFAULT 0," +
                    "  timestamp     BIGINT  NOT NULL," +
                    "  created_at    TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // ========================
                // 表8：欢迎舰长
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS welcome_guard (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT," +
                    "  uid           BIGINT  NOT NULL," +
                    "  uname         TEXT," +
                    "  guard_level   INTEGER," +
                    "  timestamp     BIGINT  NOT NULL," +
                    "  created_at    TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // ========================
                // 表9：禁言
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS block_msg (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT," +
                    "  uid           BIGINT  NOT NULL," +
                    "  uname         TEXT," +
                    "  operator      INTEGER," +
                    "  timestamp     BIGINT  NOT NULL," +
                    "  created_at    TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // ========================
                // 表10：AICU 用户标记
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS aicu_usermark (" +
                    "  uid        BIGINT PRIMARY KEY," +
                    "  data_json  TEXT," +
                    "  fetch_time BIGINT" +
                    ")"
                );

                // ========================
                // 表11：AICU 评论
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS aicu_reply (" +
                    "  uid         BIGINT NOT NULL," +
                    "  pn          INTEGER NOT NULL DEFAULT 1," +
                    "  data_json   TEXT," +
                    "  total_count INTEGER DEFAULT 0," +
                    "  fetch_time  BIGINT," +
                    "  PRIMARY KEY (uid, pn)" +
                    ")"
                );

                // ========================
                // 表12：AICU 视频弹幕
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS aicu_videodm (" +
                    "  uid         BIGINT NOT NULL," +
                    "  pn          INTEGER NOT NULL DEFAULT 1," +
                    "  data_json   TEXT," +
                    "  total_count INTEGER DEFAULT 0," +
                    "  fetch_time  BIGINT," +
                    "  PRIMARY KEY (uid, pn)" +
                    ")"
                );

                // ========================
                // 表13：AICU 直播弹幕
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS aicu_livedm (" +
                    "  uid         BIGINT NOT NULL," +
                    "  pn          INTEGER NOT NULL DEFAULT 1," +
                    "  data_json   TEXT," +
                    "  total_count INTEGER DEFAULT 0," +
                    "  fetch_time  BIGINT," +
                    "  PRIMARY KEY (uid, pn)" +
                    ")"
                );

                // ========================
                // 表14：直播间信息时间序列（对应 _1_直播间信息.csv）
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS room_info_series (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT    NOT NULL DEFAULT ''," +
                    "  time_key      TEXT    NOT NULL," +
                    "  watch_count   BIGINT  NOT NULL DEFAULT 0," +
                    "  online_count  BIGINT  NOT NULL DEFAULT 0," +
                    "  like_count    BIGINT  NOT NULL DEFAULT 0" +
                    ")"
                );
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_room_info_series ON room_info_series(room_id, anchor_name, time_key)");

                // ========================
                // 表15：礼物聚合记录（对应 _3_礼物信息.csv）
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS gift_summary (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT    NOT NULL DEFAULT ''," +
                    "  uid           BIGINT  NOT NULL," +
                    "  uname         TEXT    NOT NULL DEFAULT ''," +
                    "  gift_name     TEXT    NOT NULL," +
                    "  total_price   BIGINT  NOT NULL DEFAULT 0," +
                    "  count         INTEGER NOT NULL DEFAULT 0," +
                    "  latest_time   BIGINT  NOT NULL DEFAULT 0" +
                    ")"
                );
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_gift_summary ON gift_summary(room_id, anchor_name, uid, gift_name)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_gift_summary_uid ON gift_summary(uid)");

                // ========================
                // 表16：观众聚合记录（对应 _4_观众信息.csv）
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS visitor_summary (" +
                    "  id                INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id           BIGINT  NOT NULL," +
                    "  anchor_name       TEXT    NOT NULL DEFAULT ''," +
                    "  uid               BIGINT  NOT NULL," +
                    "  uname             TEXT    NOT NULL DEFAULT ''," +
                    "  score             INTEGER NOT NULL DEFAULT 0," +
                    "  score_type        TEXT    NOT NULL DEFAULT ''," +
                    "  count             INTEGER NOT NULL DEFAULT 0," +
                    "  in_pn_table       INTEGER NOT NULL DEFAULT 0," +
                    "  session           INTEGER NOT NULL DEFAULT 1," +
                    "  latest_entry_time BIGINT  NOT NULL DEFAULT 0" +
                    ")"
                );
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_visitor_summary ON visitor_summary(room_id, anchor_name, uid)");

                // ========================
                // 表17：匹配聚合记录（对应 _5_匹配信息.csv）
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS match_summary (" +
                    "  id                INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id           BIGINT  NOT NULL," +
                    "  anchor_name       TEXT    NOT NULL DEFAULT ''," +
                    "  matched_uid       BIGINT  NOT NULL," +
                    "  matched_name      TEXT    NOT NULL DEFAULT ''," +
                    "  score             INTEGER NOT NULL DEFAULT 0," +
                    "  count             INTEGER NOT NULL DEFAULT 0," +
                    "  latest_match_time BIGINT  NOT NULL DEFAULT 0" +
                    ")"
                );
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_match_summary ON match_summary(room_id, anchor_name, matched_uid)");

                // ========================
                // 表18：关注聚合记录（对应 _6_关注人信息.csv）
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS follow_summary (" +
                    "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id     BIGINT  NOT NULL," +
                    "  anchor_name TEXT    NOT NULL DEFAULT ''," +
                    "  uid         BIGINT  NOT NULL," +
                    "  uname       TEXT    NOT NULL DEFAULT ''," +
                    "  count       INTEGER NOT NULL DEFAULT 0," +
                    "  latest_time BIGINT  NOT NULL DEFAULT 0" +
                    ")"
                );
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_follow_summary ON follow_summary(room_id, anchor_name, uid)");

                // ========================
                // 表19：陌生观众记录（对应 _7_陌生观众.csv）
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS stranger_viewer (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  anchor_name   TEXT    NOT NULL DEFAULT ''," +
                    "  uid           BIGINT  NOT NULL," +
                    "  name          TEXT    NOT NULL DEFAULT ''," +
                    "  face          TEXT    DEFAULT ''," +
                    "  score         INTEGER NOT NULL DEFAULT 0," +
                    "  score_types   TEXT    NOT NULL DEFAULT ''," +
                    "  count         INTEGER NOT NULL DEFAULT 0," +
                    "  session       INTEGER NOT NULL DEFAULT 1," +
                    "  blocked       INTEGER NOT NULL DEFAULT 0," +
                    "  time          BIGINT  NOT NULL DEFAULT 0" +
                    ")"
                );
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_stranger_viewer ON stranger_viewer(room_id, anchor_name, uid)");

                // ========================
                // 表20：足迹留印（对应 _11_足迹留印.csv）
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS footprint (" +
                    "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id     BIGINT  NOT NULL DEFAULT 0," +
                    "  anchor_name TEXT    NOT NULL DEFAULT ''," +
                    "  auid        BIGINT  NOT NULL DEFAULT 0," +
                    "  uid         BIGINT  NOT NULL," +
                    "  uname       TEXT    NOT NULL DEFAULT ''," +
                    "  utime       BIGINT  NOT NULL DEFAULT 0" +
                    ")"
                );
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_footprint_room ON footprint(room_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_footprint_uid ON footprint(uid)");

                // ========================
                // 表21：本地黑/白名单（对应 set/本地黑名单.csv / 本地白名单.csv）
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS black_white_list (" +
                    "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  list_type   TEXT    NOT NULL CHECK(list_type IN ('black','white'))," +
                    "  uid         BIGINT  NOT NULL," +
                    "  name        TEXT    NOT NULL DEFAULT ''," +
                    "  create_time BIGINT  NOT NULL DEFAULT 0," +
                    "  update_time BIGINT  NOT NULL DEFAULT 0," +
                    "  score       INTEGER NOT NULL DEFAULT 0," +
                    "  score_type  TEXT    NOT NULL DEFAULT ''," +
                    "  room_id     BIGINT  NOT NULL DEFAULT 0," +
                    "  count       INTEGER NOT NULL DEFAULT 0" +
                    ")"
                );
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_black_white_list ON black_white_list(list_type, uid)");

                // ========================
                // 表22：点赞记录
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS like_record (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  room_id       BIGINT  NOT NULL," +
                    "  room_name     TEXT," +
                    "  anchor_name   TEXT," +
                    "  uid           BIGINT  NOT NULL," +
                    "  uname         TEXT," +
                    "  ruid          BIGINT," +
                    "  timestamp     BIGINT  NOT NULL," +
                    "  created_at    TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // ========================
                // 表23：迁移日志（记录 CSV→SQLite 迁移状态）
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS _migration_log (" +
                    "  table_name  TEXT PRIMARY KEY," +
                    "  migrated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))" +
                    ")"
                );

                // 创建索引
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_danmaku_uid ON danmaku(uid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_danmaku_ts ON danmaku(timestamp)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_danmaku_room ON danmaku(room_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_danmaku_uname ON danmaku(uname)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_enter_uid ON enter_events(uid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_enter_ts ON enter_events(timestamp)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_enter_uname ON enter_events(uname)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_follow_uid ON follow_events(uid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_follow_ts ON follow_events(timestamp)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_follow_uname ON follow_events(uname)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_gift_detail_uid ON gift_detail(uid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_gift_detail_ts ON gift_detail(timestamp)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_gift_detail_uname ON gift_detail(uname)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_guard_buy_uid ON guard_buy(uid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_guard_buy_uname ON guard_buy(uname)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_super_chat_uid ON super_chat(uid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_super_chat_uname ON super_chat(uname)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_welcome_vip_uid ON welcome_vip(uid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_welcome_vip_uname ON welcome_vip(uname)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_welcome_guard_uid ON welcome_guard(uid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_welcome_guard_uname ON welcome_guard(uname)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_block_msg_uid ON block_msg(uid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_block_msg_uname ON block_msg(uname)");

                // 新聚合表的辅助索引
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_room_info_room ON room_info_series(room_id, anchor_name)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_gift_summary_room ON gift_summary(room_id, anchor_name)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_gift_summary_uname ON gift_summary(uname)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_visitor_summary_room ON visitor_summary(room_id, anchor_name)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_visitor_summary_uname ON visitor_summary(uname)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_match_summary_room ON match_summary(room_id, anchor_name)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_follow_summary_room ON follow_summary(room_id, anchor_name)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_stranger_viewer_room ON stranger_viewer(room_id, anchor_name)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_like_record_uid ON like_record(uid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_like_record_ruid ON like_record(ruid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_like_record_room ON like_record(room_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_like_record_ts ON like_record(timestamp)");

                // ========================
                // 表24：API 响应缓存（processFollowings 的5个HTTP请求结果持久化）
                // ========================
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS api_cache (" +
                    "  cache_key     TEXT PRIMARY KEY," +
                    "  api_type      TEXT NOT NULL," +
                    "  vmid          INTEGER NOT NULL," +
                    "  response_body TEXT NOT NULL," +
                    "  created_at    INTEGER NOT NULL," +
                    "  ttl_seconds   INTEGER NOT NULL DEFAULT 2592000" +
                    ")"
                );
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_api_cache_type ON api_cache(api_type)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_api_cache_vmid ON api_cache(vmid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_api_cache_expire ON api_cache(created_at, ttl_seconds)");

                LOGGER.info("DanmujiDatabase all tables and indexes created at: {}", dbPath);

                // CSV → SQLite 一次性迁移已完成（2026-06-11），后续不再自动执行。
                // 如需手动重新迁移，调用 DanmujiMigration.migrateIfNeeded() 即可。
                // DanmujiMigration.migrateIfNeeded();

                // FTS5 全文搜索（可选功能，失败不影响核心功能）
                try {
                    stmt.execute(
                        "CREATE VIRTUAL TABLE IF NOT EXISTS danmaku_fts USING fts5(" +
                        "  content, uname, content='danmaku', content_rowid='id'" +
                        ")"
                    );
                    stmt.execute(
                        "CREATE TRIGGER IF NOT EXISTS danmaku_ai AFTER INSERT ON danmaku BEGIN " +
                        "  INSERT INTO danmaku_fts(rowid, content, uname) VALUES (new.id, new.content, new.uname); " +
                        "END"
                    );
                    LOGGER.info("DanmujiDatabase FTS5 full-text search enabled");
                } catch (Exception e) {
                    LOGGER.warn("FTS5 not available on this SQLite build, full-text search disabled: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.error("DanmujiDatabase init failed: {}", e.getMessage(), e);
            // 确保 dbPath 被设置，即使初始化失败也能被外部诊断
            if (dbPath == null) {
                dbPath = LogPathConf.getLogDir() + File.separator + "danmuji_viewer.db";
            }
        }
    }

    /**
     * 获取数据库连接（每次调用返回新连接，调用方负责关闭）
     */
    public static Connection getConnection() throws Exception {
        if (dbPath == null) {
            getInstance(); // 触发初始化
        }
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public static String getDbPath() {
        if (dbPath == null) {
            getInstance();
        }
        return dbPath;
    }
}
