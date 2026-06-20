package xyz.acproject.danmuji.entity.user_data;

import java.io.Serializable;

/**
 * 子账号实体 — 用于 Cookie 池轮换。
 * 主账号功能不变，子账号仅用于 HttpRoomData 中的动态和卡片 API。
 *
 * @author BanqiJane
 */
public class SubAccount implements Serializable {
    private static final long serialVersionUID = -5504658465538219139L;

    /** B站 UID */
    private String uid;

    /** 显示名称（用于识别） */
    private String name;

    /** 头像URL */
    private String face;

    /** B站用户等级 (0-6) */
    private int level;

    /** 原始 Cookie 字符串 */
    private String cookie;

    /** 账号是否启用 */
    private boolean enabled = true;

    /** 冷却结束时间戳（毫秒），0 表示未在冷却中 */
    private long cooldownUntil = 0L;

    /** 最近一次使用时间戳（毫秒） */
    private long lastUsedTime = 0L;

    /** 使用次数统计 */
    private long useCount = 0L;

    /** 被限流次数统计 */
    private long rateLimitedCount = 0L;

    /** Cookie 最后验证时间 */
    private long lastValidatedTime = 0L;

    /** Cookie 是否验证通过 */
    private boolean validated = false;

    /** 是否参与共同关注API轮询 (默认 false，需手动勾选) */
    private boolean sameFollowEnabled = false;

    /** 共同关注API专用冷却结束时间戳（毫秒），0 表示未在冷却中 */
    private long sameFollowCooldownUntil = 0L;

    /** 共同关注API被限流次数统计 */
    private long sameFollowRateLimitedCount = 0L;

    /** 卡片API专用冷却结束时间戳（毫秒），0 表示未在冷却中 */
    private long cardCooldownUntil = 0L;

    /** 卡片API被限流次数统计 */
    private long cardRateLimitedCount = 0L;

    /** 动态API专用冷却结束时间戳（毫秒），0 表示未在冷却中 */
    private long dynamicCooldownUntil = 0L;

    /** 动态API被限流次数统计 */
    private long dynamicRateLimitedCount = 0L;

    public SubAccount() {
    }

    public SubAccount(String uid, String name, String cookie) {
        this.uid = uid;
        this.name = name;
        this.cookie = cookie;
        this.enabled = true;
    }

    // ---- Getters & Setters ----

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFace() {
        return face;
    }

    public void setFace(String face) {
        this.face = face;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public long getLastUsedTime() {
        return lastUsedTime;
    }

    public void setLastUsedTime(long lastUsedTime) {
        this.lastUsedTime = lastUsedTime;
    }

    public long getUseCount() {
        return useCount;
    }

    public void setUseCount(long useCount) {
        this.useCount = useCount;
    }

    public long getRateLimitedCount() {
        return rateLimitedCount;
    }

    public void setRateLimitedCount(long rateLimitedCount) {
        this.rateLimitedCount = rateLimitedCount;
    }

    public long getLastValidatedTime() {
        return lastValidatedTime;
    }

    public void setLastValidatedTime(long lastValidatedTime) {
        this.lastValidatedTime = lastValidatedTime;
    }

    public boolean isValidated() {
        return validated;
    }

    public void setValidated(boolean validated) {
        this.validated = validated;
    }

    public boolean isSameFollowEnabled() {
        return sameFollowEnabled;
    }

    public void setSameFollowEnabled(boolean sameFollowEnabled) {
        this.sameFollowEnabled = sameFollowEnabled;
    }

    public long getSameFollowCooldownUntil() {
        return sameFollowCooldownUntil;
    }

    public void setSameFollowCooldownUntil(long sameFollowCooldownUntil) {
        this.sameFollowCooldownUntil = sameFollowCooldownUntil;
    }

    public long getSameFollowRateLimitedCount() {
        return sameFollowRateLimitedCount;
    }

    public void setSameFollowRateLimitedCount(long sameFollowRateLimitedCount) {
        this.sameFollowRateLimitedCount = sameFollowRateLimitedCount;
    }

    /** 共同关注API是否正在冷却中 */
    public boolean isSameFollowCoolingDown() {
        return sameFollowCooldownUntil > 0 && System.currentTimeMillis() < sameFollowCooldownUntil;
    }

    // ---- 卡片API冷却 ----
    public long getCardCooldownUntil() { return cardCooldownUntil; }
    public void setCardCooldownUntil(long v) { this.cardCooldownUntil = v; }
    public long getCardRateLimitedCount() { return cardRateLimitedCount; }
    public void setCardRateLimitedCount(long v) { this.cardRateLimitedCount = v; }
    public boolean isCardCoolingDown() {
        return cardCooldownUntil > 0 && System.currentTimeMillis() < cardCooldownUntil;
    }

    // ---- 动态API冷却 ----
    public long getDynamicCooldownUntil() { return dynamicCooldownUntil; }
    public void setDynamicCooldownUntil(long v) { this.dynamicCooldownUntil = v; }
    public long getDynamicRateLimitedCount() { return dynamicRateLimitedCount; }
    public void setDynamicRateLimitedCount(long v) { this.dynamicRateLimitedCount = v; }
    public boolean isDynamicCoolingDown() {
        return dynamicCooldownUntil > 0 && System.currentTimeMillis() < dynamicCooldownUntil;
    }

    /**
     * 是否正在冷却中
     */
    public boolean isCoolingDown() {
        return cooldownUntil > 0 && System.currentTimeMillis() < cooldownUntil;
    }

    /**
     * 获取剩余冷却时间（秒），未冷却返回 0
     */
    public long getCooldownRemainingSeconds() {
        if (cooldownUntil <= 0) return 0;
        long remaining = (cooldownUntil - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 0);
    }

    /**
     * 获取可显示的状态字符串
     */
    public String getStatusText() {
        if (!enabled) return "已禁用";
        if (isCoolingDown()) {
            long sec = getCooldownRemainingSeconds();
            if (sec > 60) return "冷却中(" + (sec / 60) + "分)";
            return "冷却中(" + sec + "秒)";
        }
        if (!validated) return "未验证";
        return "可用";
    }

    /**
     * 获取状态颜色标识
     */
    public String getStatusColor() {
        if (!enabled) return "secondary";
        if (isCoolingDown()) return "warning";
        if (!validated) return "info";
        return "success";
    }
}
