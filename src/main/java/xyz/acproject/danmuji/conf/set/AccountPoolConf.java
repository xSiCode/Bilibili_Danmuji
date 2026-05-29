package xyz.acproject.danmuji.conf.set;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import xyz.acproject.danmuji.entity.user_data.SubAccount;
import xyz.acproject.danmuji.utils.FastJsonUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 账号池配置 — 管理主账号下的多个子账号 cookie。
 * 持久化到 DanmujiAccountPool 文件，JSON 序列化。
 *
 * @author BanqiJane
 */
public class AccountPoolConf implements Serializable {
    private static final long serialVersionUID = 1898327115269223770L;

    /** 配置版本 */
    @JSONField(name = "version")
    private int version = 1;

    /** 子账号列表 */
    @JSONField(name = "accounts")
    private List<SubAccount> accounts = new CopyOnWriteArrayList<>();

    /** 是否启用 Cookie 池轮换 */
    @JSONField(name = "enabled")
    private boolean enabled = true;

    /** 冷却时间（秒），默认 5 分钟 */
    @JSONField(name = "cooldown_seconds")
    private int cooldownSeconds = 300;

    /** 动态 API 限流速率（每秒请求数），默认 0.5 */
    @JSONField(name = "dynamic_rate")
    private double dynamicRate = 0.5;

    /** 卡片 API 限流速率（每秒请求数），默认 1.0 */
    @JSONField(name = "card_rate")
    private double cardRate = 1.0;

    /** 缓存 TTL（秒），默认 300 秒 = 5 分钟 */
    @JSONField(name = "cache_ttl_seconds")
    private int cacheTtlSeconds = 300;

    public AccountPoolConf() {
        this.accounts = new CopyOnWriteArrayList<>();
    }

    // ---- Getters & Setters ----

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<SubAccount> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<SubAccount> accounts) {
        this.accounts = accounts != null ? new CopyOnWriteArrayList<>(accounts) : new CopyOnWriteArrayList<>();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public double getDynamicRate() {
        return dynamicRate;
    }

    public void setDynamicRate(double dynamicRate) {
        this.dynamicRate = dynamicRate;
    }

    public double getCardRate() {
        return cardRate;
    }

    public void setCardRate(double cardRate) {
        this.cardRate = cardRate;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    /**
     * 获取所有启用的账号（不含冷却中的）
     */
    public List<SubAccount> getAvailableAccounts() {
        List<SubAccount> available = new ArrayList<>();
        for (SubAccount acc : accounts) {
            if (acc.isEnabled() && !acc.isCoolingDown() && acc.isValidated()) {
                available.add(acc);
            }
        }
        return available;
    }

    /**
     * 获取启用的账号数量
     */
    public int getEnabledCount() {
        int count = 0;
        for (SubAccount acc : accounts) {
            if (acc.isEnabled()) count++;
        }
        return count;
    }

    /**
     * 获取可用（未冷却）的账号数量
     */
    public int getAvailableCount() {
        return getAvailableAccounts().size();
    }

    /**
     * 序列化为 JSON 字符串
     */
    public String toJson() {
        return FastJsonUtils.toJson(this);
    }

    /**
     * 从 JSON 字符串反序列化
     */
    public static AccountPoolConf fromJson(String json) {
        AccountPoolConf conf = FastJsonUtils.parseObject(json, AccountPoolConf.class);
        if (conf == null) {
            conf = new AccountPoolConf();
        }
        if (conf.getAccounts() == null) {
            conf.setAccounts(new CopyOnWriteArrayList<>());
        }
        // 旧配置升级
        if (conf.getCooldownSeconds() <= 0) {
            conf.setCooldownSeconds(300);
        }
        if (conf.getDynamicRate() <= 0) {
            conf.setDynamicRate(0.5);
        }
        if (conf.getCardRate() <= 0) {
            conf.setCardRate(1.0);
        }
        if (conf.getCacheTtlSeconds() <= 0) {
            conf.setCacheTtlSeconds(300);
        }
        return conf;
    }

    /**
     * 转换为前端展示用的 JSON 对象（默认隐藏完整 Cookie，包含主账号信息和头像）
     */
    public JSONObject toDisplayJson() {
        JSONObject json = new JSONObject();
        json.put("enabled", enabled);
        json.put("cooldownSeconds", cooldownSeconds);
        json.put("dynamicRate", dynamicRate);
        json.put("cardRate", cardRate);
        json.put("cacheTtlSeconds", cacheTtlSeconds);
        json.put("availableCount", getAvailableCount());
        json.put("enabledCount", getEnabledCount());
        json.put("totalCount", accounts.size());

        // 主账号信息
        JSONObject mainJson = new JSONObject();
        if (xyz.acproject.danmuji.conf.PublicDataConf.USER != null) {
            mainJson.put("uid", String.valueOf(xyz.acproject.danmuji.conf.PublicDataConf.USER.getUid()));
            mainJson.put("name", xyz.acproject.danmuji.conf.PublicDataConf.USER.getUname());
            mainJson.put("face", xyz.acproject.danmuji.conf.PublicDataConf.USER.getFace());
            mainJson.put("level", xyz.acproject.danmuji.conf.PublicDataConf.USER.getUser_level());
        }
        json.put("mainAccount", mainJson);

        JSONArray arr = new JSONArray();

        // 将主账号作为第一行插入列表
        if (xyz.acproject.danmuji.conf.PublicDataConf.USER != null
                && org.apache.commons.lang3.StringUtils.isNotBlank(xyz.acproject.danmuji.conf.PublicDataConf.USERCOOKIE)) {
            JSONObject mainRow = new JSONObject();
            mainRow.put("uid", String.valueOf(xyz.acproject.danmuji.conf.PublicDataConf.USER.getUid()));
            mainRow.put("name", xyz.acproject.danmuji.conf.PublicDataConf.USER.getUname());
            mainRow.put("face", xyz.acproject.danmuji.conf.PublicDataConf.USER.getFace() != null
                    ? xyz.acproject.danmuji.conf.PublicDataConf.USER.getFace() : "");
            mainRow.put("cookiePreview", "***主账号***");
            mainRow.put("enabled", true);
            mainRow.put("status", "主账号");
            mainRow.put("statusColor", "primary");
            mainRow.put("coolingDown", false);
            mainRow.put("cooldownRemaining", 0);
            mainRow.put("isMain", true);
            // 主账号的统计
            xyz.acproject.danmuji.http.CookiePoolManager pool = xyz.acproject.danmuji.http.CookiePoolManager.getInstance();
            mainRow.put("useCount", pool.getMainUseCount());
            mainRow.put("rateLimitedCount", pool.getMainRateLimitedCount());
            mainRow.put("lastUsedTime", 0L);
            arr.add(mainRow);
        }

        for (SubAccount acc : accounts) {
            JSONObject accJson = new JSONObject();
            accJson.put("uid", acc.getUid());
            accJson.put("name", acc.getName());
            // 头像
            accJson.put("face", acc.getFace() != null ? acc.getFace() : "");
            // 只显示 cookie 的前后各8个字符
            String cookie = acc.getCookie();
            if (cookie != null && cookie.length() > 20) {
                accJson.put("cookiePreview", cookie.substring(0, 10) + "..." + cookie.substring(cookie.length() - 8));
            } else if (cookie != null) {
                accJson.put("cookiePreview", cookie.substring(0, Math.min(10, cookie.length())) + "...");
            } else {
                accJson.put("cookiePreview", "");
            }
            accJson.put("enabled", acc.isEnabled());
            accJson.put("isMain", false);
            accJson.put("status", acc.getStatusText());
            accJson.put("statusColor", acc.getStatusColor());
            accJson.put("coolingDown", acc.isCoolingDown());
            accJson.put("cooldownRemaining", acc.getCooldownRemainingSeconds());
            accJson.put("lastUsedTime", acc.getLastUsedTime());
            accJson.put("useCount", acc.getUseCount());
            accJson.put("rateLimitedCount", acc.getRateLimitedCount());
            accJson.put("validated", acc.isValidated());
            arr.add(accJson);
        }
        json.put("accounts", arr);
        return json;
    }

    /**
     * 创建一个默认的空配置
     */
    public static AccountPoolConf createDefault() {
        return new AccountPoolConf();
    }
}
