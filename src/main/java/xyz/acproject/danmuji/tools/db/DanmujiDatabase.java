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

                LOGGER.info("DanmujiDatabase all tables and indexes created at: {}", dbPath);

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
