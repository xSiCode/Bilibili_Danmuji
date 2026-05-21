package xyz.acproject.danmuji.component.black;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.auto_reply.AutoReply;
import xyz.acproject.danmuji.entity.danmu_data.Gift;
import xyz.acproject.danmuji.entity.danmu_data.Interact;

@Component
public class BlackParseComponent {

    private static Logger LOGGER = LogManager.getLogger(BlackParseComponent.class);

    public boolean autoReplay_parse(AutoReply autoReply) {
        boolean blackEnabled = isBlackEnabled(null, null, null, true);
        boolean whiteEnabled = isWhiteEnabled(null, null, null, true);
        if (!blackEnabled && !whiteEnabled) return true;
        return this.parse(autoReply);
    }

    public boolean interact_parse(Interact interact) {
        boolean blackEnabled = isBlackEnabled(null, true, null, null);
        boolean whiteEnabled = isWhiteEnabled(null, true, null, null);
        if (!blackEnabled && !whiteEnabled) return true;
        return this.parse(interact);
    }

    public boolean gift_parse(Gift gift) {
        boolean blackEnabled = isBlackEnabled(true, null, null, null);
        boolean whiteEnabled = isWhiteEnabled(true, null, null, null);
        if (!blackEnabled && !whiteEnabled) return true;
        return this.parse(gift);
    }

    public <T> boolean global_parse(T t) {
        boolean blackEnabled = PublicDataConf.centerSetConf.getBlack() != null
                && PublicDataConf.centerSetConf.getBlack().isAll();
        boolean whiteEnabled = PublicDataConf.centerSetConf.getWhite() != null
                && PublicDataConf.centerSetConf.getWhite().isAll();
        if (!blackEnabled && !whiteEnabled) return true;
        return this.parse(t);
    }

    public <T> boolean parse(T t) {
        String name = extractName(t);
        String uid = extractUid(t);

        // Blacklist check (highest priority)
        if (PublicDataConf.centerSetConf.getBlack() != null) {
            for (String s : PublicDataConf.centerSetConf.getBlack().getNames()) {
                if (StringUtils.isBlank(s)) continue;
                if (StringUtils.contains(name, s)) {
                    LOGGER.info("黑名单过滤姬拦截到了一条数据：{} - {}", t.getClass().getName(), t);
                    return false;
                }
            }
            for (String s : PublicDataConf.centerSetConf.getBlack().getUids()) {
                if (StringUtils.isBlank(s)) continue;
                if (s.equals(uid)) {
                    LOGGER.info("黑名单过滤姬拦截到了一条数据：{} - {}", t.getClass().getName(), t);
                    return false;
                }
            }
        }

        // Whitelist check
        if (PublicDataConf.centerSetConf.getWhite() != null) {
            for (String s : PublicDataConf.centerSetConf.getWhite().getNames()) {
                if (StringUtils.isBlank(s)) continue;
                if (StringUtils.contains(name, s)) {
                    return true;
                }
            }
            for (String s : PublicDataConf.centerSetConf.getWhite().getUids()) {
                if (StringUtils.isBlank(s)) continue;
                if (s.equals(uid)) {
                    return true;
                }
            }
        }

        return true;
    }

    private String extractName(Object t) {
        if (t instanceof AutoReply) return ((AutoReply) t).getName();
        if (t instanceof Interact) return ((Interact) t).getUname();
        if (t instanceof Gift) return ((Gift) t).getUname();
        return "";
    }

    private String extractUid(Object t) {
        if (t instanceof AutoReply) return ((AutoReply) t).getUid() + "";
        if (t instanceof Interact) return ((Interact) t).getUid() + "";
        if (t instanceof Gift) return ((Gift) t).getUid() + "";
        return "";
    }

    private boolean isBlackEnabled(Boolean gift, Boolean follow, Boolean welcome, Boolean reply) {
        if (PublicDataConf.centerSetConf.getBlack() == null) return false;
        if (PublicDataConf.centerSetConf.getBlack().isAll()) return true;
        if (gift != null && PublicDataConf.centerSetConf.getBlack().isThank_gift()) return true;
        if (follow != null && PublicDataConf.centerSetConf.getBlack().isThank_follow()) return true;
        if (welcome != null && PublicDataConf.centerSetConf.getBlack().isThank_welcome()) return true;
        if (reply != null && PublicDataConf.centerSetConf.getBlack().isAuto_reply()) return true;
        return false;
    }

    private boolean isWhiteEnabled(Boolean gift, Boolean follow, Boolean welcome, Boolean reply) {
        if (PublicDataConf.centerSetConf.getWhite() == null) return false;
        if (PublicDataConf.centerSetConf.getWhite().isAll()) return true;
        if (gift != null && PublicDataConf.centerSetConf.getWhite().isThank_gift()) return true;
        if (follow != null && PublicDataConf.centerSetConf.getWhite().isThank_follow()) return true;
        if (welcome != null && PublicDataConf.centerSetConf.getWhite().isThank_welcome()) return true;
        if (reply != null && PublicDataConf.centerSetConf.getWhite().isAuto_reply()) return true;
        return false;
    }
}
