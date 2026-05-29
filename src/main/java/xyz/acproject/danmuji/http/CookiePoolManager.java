package xyz.acproject.danmuji.http;

import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.conf.set.AccountPoolConf;
import xyz.acproject.danmuji.entity.user_data.SubAccount;
import xyz.acproject.danmuji.tools.BASE64Encoder;
import xyz.acproject.danmuji.tools.CookieEncryptUtils;
import xyz.acproject.danmuji.tools.file.ProFileTools;
import xyz.acproject.danmuji.utils.OkHttp3Utils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cookie 池管理器 — 管理主账号 + 多个子账号的 Cookie 轮换。
 * 仅用于 HttpRoomData 中的动态 API 和卡片信息 API。
 * 持久化到 DanmujiAccountPool 文件。
 *
 * @author BanqiJane
 */
public class CookiePoolManager {

    private static final Logger LOGGER = LogManager.getLogger(CookiePoolManager.class);
    private static final String POOL_FILE_NAME = "DanmujiAccountPool";
    private static final String POOL_KEY = "account_pool";

    private static volatile CookiePoolManager instance;

    /** 账号池配置 */
    private volatile AccountPoolConf poolConf;

    /** 轮询索引（用于 Round-Robin） */
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    /** 主账号使用次数 */
    private final AtomicLong mainUseCount = new AtomicLong(0);

    /** 主账号被限流次数 */
    private final AtomicLong mainRateLimitedCount = new AtomicLong(0);

    /** OkHttp 客户端（用于验证 Cookie） */
    private final OkHttpClient validationClient;

    private CookiePoolManager() {
        this.validationClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        loadFromFile();
    }

    public static CookiePoolManager getInstance() {
        if (instance == null) {
            synchronized (CookiePoolManager.class) {
                if (instance == null) {
                    instance = new CookiePoolManager();
                }
            }
        }
        return instance;
    }

    // ==================== Cookie 获取 ====================

    /**
     * 获取下一个可用的 Cookie。
     * 优先返回未冷却的子账号 Cookie；如果全部冷却，返回冷却剩余时间最短的；
     * 如果没有可用子账号或池未启用，返回主账号 Cookie（PublicDataConf.USERCOOKIE）。
     *
     * @return Cookie 字符串，可能为 null
     */
    public String getNextCookie() {
        // 如果池未启用或没有子账号，直接用主账号
        if (poolConf == null || !poolConf.isEnabled() || poolConf.getAccounts().isEmpty()) {
            mainUseCount.incrementAndGet();
            return PublicDataConf.USERCOOKIE;
        }

        List<SubAccount> accounts = poolConf.getAccounts();
        List<SubAccount> available = poolConf.getAvailableAccounts();

        if (!available.isEmpty()) {
            // Round-Robin 从可用账号中选择
            int index = Math.abs(roundRobinIndex.getAndIncrement() % available.size());
            SubAccount selected = available.get(index);
            selected.setLastUsedTime(System.currentTimeMillis());
            selected.setUseCount(selected.getUseCount() + 1);
            LOGGER.debug("CookiePool: 使用子账号 [{}] uid={}", selected.getName(), selected.getUid());
            return selected.getCookie();
        }

        // 所有子账号都在冷却中，找冷却剩余最短的
        SubAccount earliestCooldown = null;
        long minRemaining = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        for (SubAccount acc : accounts) {
            if (!acc.isEnabled()) continue;
            long remaining = acc.getCooldownUntil() - now;
            if (remaining > 0 && remaining < minRemaining) {
                minRemaining = remaining;
                earliestCooldown = acc;
            }
        }
        if (earliestCooldown != null) {
            LOGGER.warn("CookiePool: 所有子账号均在冷却中，使用最早恢复的账号 [{}] (剩余{}秒)",
                    earliestCooldown.getName(), minRemaining / 1000);
            earliestCooldown.setLastUsedTime(now);
            earliestCooldown.setUseCount(earliestCooldown.getUseCount() + 1);
            return earliestCooldown.getCookie();
        }

        // 所有账号都禁用了，回退到主账号
        LOGGER.warn("CookiePool: 无可用子账号，回退到主账号");
        mainUseCount.incrementAndGet();
        return PublicDataConf.USERCOOKIE;
    }

    // ==================== 限流处理 ====================

    /**
     * 标记当前使用的 Cookie 触发限流，将其冷却。
     *
     * @param cookie 被限流的 Cookie
     */
    public void markRateLimited(String cookie) {
        if (cookie == null || poolConf == null) return;

        for (SubAccount acc : poolConf.getAccounts()) {
            if (cookie.equals(acc.getCookie())) {
                long cooldownMs = poolConf.getCooldownSeconds() * 1000L;
                acc.setCooldownUntil(System.currentTimeMillis() + cooldownMs);
                acc.setRateLimitedCount(acc.getRateLimitedCount() + 1);
                LOGGER.warn("CookiePool: 账号 [{}] uid={} 触发限流，冷却 {} 秒",
                        acc.getName(), acc.getUid(), poolConf.getCooldownSeconds());
                saveToFile();
                return;
            }
        }

        // 如果是主账号被限流
        if (cookie.equals(PublicDataConf.USERCOOKIE)) {
            mainRateLimitedCount.incrementAndGet();
            LOGGER.warn("CookiePool: 主账号触发限流！(累计{}次) 建议添加子账号来分摊请求。", mainRateLimitedCount.get());
        }
    }

    // ==================== Cookie 验证 ====================

    /**
     * 验证一个 Cookie 是否有效（调用 B站 nav API）。
     *
     * @param cookie 待验证的 Cookie 字符串
     * @return [isValid, uid, uname, face] - 失败时后三项为空字符串
     */
    public String[] validateCookie(String cookie) {
        if (StringUtils.isBlank(cookie)) {
            return new String[]{"false", "", "", ""};
        }

        try {
            Map<String, String> headers = new java.util.HashMap<>(3);
            headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.put("referer", "https://www.bilibili.com/");
            headers.put("cookie", cookie);

            Request request = new Request.Builder()
                    .url("https://api.bilibili.com/x/web-interface/nav")
                    .headers(Headers.of(headers))
                    .get()
                    .build();

            try (Response response = validationClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = JSONObject.parseObject(body);
                    if (json != null && json.getIntValue("code") == 0) {
                        JSONObject data = json.getJSONObject("data");
                        if (data != null && data.getBooleanValue("isLogin")) {
                            String uid = data.getString("mid");
                            String uname = data.getString("uname");
                            String face = data.getString("face");
                            return new String[]{"true",
                                    uid != null ? uid : "",
                                    uname != null ? uname : "",
                                    face != null ? face : ""};
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("CookiePool: 验证 Cookie 异常", e);
        }
        return new String[]{"false", "", "", ""};
    }

    // ==================== 账号管理 ====================

    /**
     * 添加子账号
     */
    public boolean addAccount(SubAccount account) {
        if (account == null || StringUtils.isBlank(account.getCookie())) {
            return false;
        }
        // 检查重复
        for (SubAccount acc : poolConf.getAccounts()) {
            if (account.getUid() != null && account.getUid().equals(acc.getUid())) {
                LOGGER.warn("CookiePool: 账号 uid={} 已存在", account.getUid());
                return false;
            }
        }
        poolConf.getAccounts().add(account);
        saveToFile();
        LOGGER.info("CookiePool: 添加子账号 [{}] uid={}", account.getName(), account.getUid());
        return true;
    }

    /**
     * 更新子账号
     */
    public boolean updateAccount(String uid, SubAccount updated) {
        if (uid == null || updated == null) return false;
        List<SubAccount> accounts = poolConf.getAccounts();
        for (int i = 0; i < accounts.size(); i++) {
            if (uid.equals(accounts.get(i).getUid())) {
                accounts.set(i, updated);
                saveToFile();
                LOGGER.info("CookiePool: 更新子账号 uid={}", uid);
                return true;
            }
        }
        return false;
    }

    /**
     * 删除子账号
     */
    public boolean removeAccount(String uid) {
        if (uid == null) return false;
        boolean removed = poolConf.getAccounts().removeIf(acc -> uid.equals(acc.getUid()));
        if (removed) {
            saveToFile();
            LOGGER.info("CookiePool: 删除子账号 uid={}", uid);
        }
        return removed;
    }

    /**
     * 启用/禁用子账号
     */
    public boolean setAccountEnabled(String uid, boolean enabled) {
        if (uid == null) return false;
        for (SubAccount acc : poolConf.getAccounts()) {
            if (uid.equals(acc.getUid())) {
                acc.setEnabled(enabled);
                if (!enabled) {
                    acc.setCooldownUntil(0); // 禁用时清除冷却状态
                }
                saveToFile();
                return true;
            }
        }
        return false;
    }

    /**
     * 手动清除账号冷却状态
     */
    public boolean clearCooldown(String uid) {
        if (uid == null) return false;
        for (SubAccount acc : poolConf.getAccounts()) {
            if (uid.equals(acc.getUid())) {
                acc.setCooldownUntil(0);
                saveToFile();
                return true;
            }
        }
        return false;
    }

    /**
     * 获取所有子账号
     */
    public List<SubAccount> getAllAccounts() {
        return poolConf != null ? poolConf.getAccounts() : new CopyOnWriteArrayList<>();
    }

    /**
     * 获取账号池配置
     */
    public AccountPoolConf getPoolConf() {
        return poolConf;
    }

    /**
     * 更新账号池配置
     */
    public void updatePoolConf(AccountPoolConf conf) {
        this.poolConf = conf;
        saveToFile();
    }

    /**
     * 获取可用的 Cookie 数量（包含主账号）
     */
    public int getTotalAvailableCount() {
        int count = poolConf != null ? poolConf.getAvailableCount() : 0;
        if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE) && PublicDataConf.USER != null) {
            count++; // 主账号也算可用
        }
        return count;
    }

    /** 主账号使用次数 */
    public long getMainUseCount() { return mainUseCount.get(); }

    /** 主账号被限流次数 */
    public long getMainRateLimitedCount() { return mainRateLimitedCount.get(); }

    /** 主账号是否可用（已登录且未被限流冷却） */
    public boolean isMainAccountAvailable() {
        return StringUtils.isNotBlank(PublicDataConf.USERCOOKIE) && PublicDataConf.USER != null;
    }

    /**
     * 根据UID获取子账号（不含主账号）
     */
    public SubAccount getAccount(String uid) {
        if (uid == null || poolConf == null) return null;
        for (SubAccount acc : poolConf.getAccounts()) {
            if (uid.equals(acc.getUid())) return acc;
        }
        return null;
    }

    /**
     * 切换主账号：将指定子账号提升为主账号，原主账号降级为子账号存入池中。
     * 调用方负责更新 PublicDataConf 并持久化 profile。
     *
     * @param targetUid 要提升为主账号的子账号UID
     * @return [newCookie, newUid, newName, newFace] 或 null（失败时）
     */
    public String[] prepareSwitchMain(String targetUid) {
        if (targetUid == null || poolConf == null) return null;

        SubAccount target = getAccount(targetUid);
        if (target == null || !target.isEnabled()) {
            LOGGER.warn("CookiePool: 切换主账号失败 — 目标账号不存在或已禁用 uid={}", targetUid);
            return null;
        }

        // 验证目标cookie有效性
        String[] validation = validateCookie(target.getCookie());
        if (!"true".equals(validation[0])) {
            LOGGER.warn("CookiePool: 切换主账号失败 — 目标Cookie失效 uid={}", targetUid);
            return null;
        }

        // 提取目标账号信息并保存原始cookie
        String newCookie = target.getCookie();
        String newUid = validation[1];
        String newName = validation[2];
        String newFace = validation[3];

        // 如果原主账号已登录，将其降级为子账号
        if (PublicDataConf.USER != null && StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
            String oldUid = String.valueOf(PublicDataConf.USER.getUid());
            // 避免自己切换到自己
            if (oldUid.equals(newUid)) {
                LOGGER.warn("CookiePool: 切换主账号失败 — 不能切换到同一个账号");
                return null;
            }
            SubAccount oldMain = new SubAccount(
                    oldUid,
                    PublicDataConf.USER.getUname(),
                    PublicDataConf.USERCOOKIE
            );
            oldMain.setFace(PublicDataConf.USER.getFace());
            oldMain.setValidated(true);
            oldMain.setLastValidatedTime(System.currentTimeMillis());
            oldMain.setEnabled(true);
            // 删除旧的主账号子账号条目（如果存在）然后添加
            removeAccount(oldUid);
            addAccountInternal(oldMain);
        }

        // 从池中移除目标（它将成为新主账号）
        removeAccount(targetUid);
        LOGGER.info("CookiePool: 准备切换主账号 {} -> {} ({})",
                PublicDataConf.USER != null ? PublicDataConf.USER.getUname() : "null", newName, newUid);
        return new String[]{newCookie, newUid, newName, newFace};
    }

    /** 内部添加，不触发额外验证 */
    private void addAccountInternal(SubAccount account) {
        if (account == null) return;
        poolConf.getAccounts().add(account);
        saveToFile();
    }

    // ==================== 持久化 ====================

    private void loadFromFile() {
        try {
            Map<String, String> profileMap = ProFileTools.read(POOL_FILE_NAME);
            String encryptedData = profileMap.get(POOL_KEY);
            if (StringUtils.isNotBlank(encryptedData)) {
                String decrypted = CookieEncryptUtils.decrypt(encryptedData);
                if (StringUtils.isNotBlank(decrypted)) {
                    this.poolConf = AccountPoolConf.fromJson(decrypted);
                }
            }
        } catch (Exception e) {
            LOGGER.error("CookiePool: 加载账号池文件失败", e);
        }
        if (this.poolConf == null) {
            this.poolConf = AccountPoolConf.createDefault();
        }
    }

    private void saveToFile() {
        try {
            Map<String, String> profileMap = new ConcurrentHashMap<>();
            String encrypted = CookieEncryptUtils.encrypt(poolConf.toJson());
            if (encrypted != null) {
                profileMap.put(POOL_KEY, encrypted);
                ProFileTools.write(profileMap, POOL_FILE_NAME);
            }
        } catch (Exception e) {
            LOGGER.error("CookiePool: 保存账号池文件失败", e);
        }
    }

    /**
     * 重新加载配置（用于外部文件修改后）
     */
    public void reload() {
        loadFromFile();
        LOGGER.info("CookiePool: 重新加载账号池配置，共 {} 个子账号", poolConf.getAccounts().size());
    }
}
