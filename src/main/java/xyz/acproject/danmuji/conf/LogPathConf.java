package xyz.acproject.danmuji.conf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 统一日志目录配置。所有 CSV / log / footprint 工具类通过此类获取基准目录。
 * 优先级：danmuji.log.dir 配置值 > 系统 Documents/Danmuji_log 目录（默认）。
 */
@Configuration
public class LogPathConf {

    /** 用户显式配置的日志根目录（末尾不含分隔符），空字符串表示未配置 */
    private static volatile String configuredDir = "";

    /** 回退路径缓存（系统 Documents 目录下的 Danmuji_log），不含末尾分隔符 */
    private static volatile String fallbackDir;

    @Value("${danmuji.log.dir:}")
    public void setLogDirFromConfig(String path) {
        if (path != null && !path.trim().isEmpty()) {
            configuredDir = path.trim();
            // 去掉末尾的 / 或 \，统一为无后缀格式
            while (configuredDir.endsWith("/") || configuredDir.endsWith("\\")) {
                configuredDir = configuredDir.substring(0, configuredDir.length() - 1);
            }
        }
    }

    /**
     * 返回 Danmuji_log 目录的绝对路径（不含末尾分隔符）。
     * 若配置了 danmuji.log.dir 则直接使用；
     * 否则默认使用系统 Documents/Danmuji_log 目录。
     */
    public static String getLogDir() {
        // 优先使用用户配置
        String dir = configuredDir;
        if (dir != null && !dir.isEmpty()) {
            ensureDir(dir);
            return dir;
        }
        // 回退路径：系统 Documents/Danmuji_log
        String fb = fallbackDir;
        if (fb == null) {
            synchronized (LogPathConf.class) {
                fb = fallbackDir;
                if (fb == null) {
                    String docsDir = resolveDocumentsDir();
                    fb = docsDir + File.separator + "Danmuji_log";
                    fallbackDir = fb;
                }
            }
        }
        ensureDir(fb);
        return fb;
    }

    /**
     * 解析系统默认文档目录。
     * Windows: {user.home}/Documents
     * macOS:   {user.home}/Documents
     * Linux:   {user.home}/Documents（如不存在则用 user.home）
     */
    private static String resolveDocumentsDir() {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            return System.getProperty("user.dir");
        }
        File docs = new File(home, "Documents");
        if (docs.exists() && docs.isDirectory()) {
            return docs.getAbsolutePath();
        }
        // Documents 目录不存在时回退到 user.home（某些 Linux 发行版）
        return home;
    }

    /**
     * 同 getLogDir()，但返回 File 对象。
     */
    public static File getLogDirAsFile() {
        File dir = new File(getLogDir());
        ensureDir(dir.getAbsolutePath());
        return dir;
    }

    private static void ensureDir(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
