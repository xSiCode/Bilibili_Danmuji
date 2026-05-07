package xyz.acproject.danmuji.thread;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.conf.set.AutoReplySet;
import xyz.acproject.danmuji.entity.apex.ApexMessage;
import xyz.acproject.danmuji.entity.apex.PredatorResult;
import xyz.acproject.danmuji.entity.auto_reply.AutoReply;
import xyz.acproject.danmuji.http.HttpRoomData;
import xyz.acproject.danmuji.http.HttpUserData;
import xyz.acproject.danmuji.tools.CurrencyTools;
import xyz.acproject.danmuji.utils.JodaTimeUtils;
import xyz.acproject.danmuji.utils.SpringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author BanqiJane
 * @ClassName AutoReplyThread
 * @Description TODO
 * @date 2020年8月10日 下午12:16:13
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
@Getter
@Setter
public class AutoReplyThread extends Thread {

    // API 结果缓存，避免每次自动回复匹配都发起 HTTP 请求
    private static final ConcurrentHashMap<String, CachedEntry> apiCache = new ConcurrentHashMap<>();

    private static class CachedEntry {
        final Object value;
        final long expireTime;
        CachedEntry(Object value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
        }
        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getOrLoad(String key, long ttlMs, Supplier<T> loader) {
        CachedEntry entry = apiCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.value;
        }
        T value = loader.get();
        if (value != null) {
            apiCache.put(key, new CachedEntry(value, System.currentTimeMillis() + ttlMs));
        }
        return value;
    }


    public volatile boolean FLAG = false;

    private double time = 3;
    private HashSet<AutoReplySet> autoReplySets;


    @Override
    public void run() {
        // TODO 自动生成的方法存根
        super.run();
        int keywordSize = 0;
        int noShieldNum = 0;
        String replyString = null;
        boolean is_shield;
        boolean is_send = false;
        String hourString = null;
        String hourReplace = null;
        String keywords[] = null;
        short hour = 1;
        while (!FLAG) {
            if (FLAG) {
                return;
            }
            if (PublicDataConf.webSocketProxy != null && !PublicDataConf.webSocketProxy.isOpen()) {
                return;
            }
            AutoReply autoReply = PublicDataConf.replys.poll();
            if (autoReply != null) {
                for (AutoReplySet autoReplySet : getAutoReplySets()) {
                    //优先级屏蔽词
                    if (!CollectionUtils.isEmpty(autoReplySet.getShields())) {
                        keywordSize = autoReplySet.getKeywords().size();
                        noShieldNum = 0;
                        is_shield = false;
                        for (String shield : autoReplySet.getShields()) {
                            if (autoReply.getBarrage().contains(shield)) {
                                is_shield = true;
                                break;
                            }
                        }
                        if (!is_shield) {
                            for (String keyword : autoReplySet.getKeywords()) {
                                if (StringUtils.indexOf(keyword, "||") != -1) {
                                    keywords = StringUtils.split(keyword, "||");
                                    for (String k : keywords) {
                                        if (autoReply.getBarrage().contains(k)) {
                                            noShieldNum++;
                                            break;
                                        }
                                    }
                                } else {
                                    if (autoReply.getBarrage().contains(keyword)) {
                                        noShieldNum++;
                                    }
                                }
                            }
                            //没有屏蔽则发送
                            if (noShieldNum == keywordSize) {
                                if (StringUtils.isNotBlank(autoReplySet.getReply())) {
                                    is_send =   handle(autoReplySet, null, autoReply, hourString, hour, hourReplace,
                                            is_send);
                                    break;
                                }
                            }
                        }
                    } else {
                        keywordSize = autoReplySet.getKeywords().size();
                        noShieldNum = 0;
                        // 精确匹配
                        if (autoReplySet.getKeywords().size() < 2 && autoReplySet.is_accurate()) {
                            for (String keyword : autoReplySet.getKeywords()) {
                                if (StringUtils.indexOf(keyword, "||") != -1) {
                                    keywords = StringUtils.split(keyword, "||");
                                    for (String k : keywords) {
                                        if (autoReply.getBarrage().equals(k)) {
                                            // do something
                                            is_send = handle(autoReplySet, null, autoReply, hourString, hour, hourReplace,
                                                    is_send);
                                            break;
                                        }
                                    }
                                } else {
                                    if (autoReply.getBarrage().equals(keyword)) {
                                        // do something
                                        is_send = handle(autoReplySet, null, autoReply, hourString, hour, hourReplace,
                                                is_send);
                                    }
                                }
                            }
                        } else {
                            for (String keyword : autoReplySet.getKeywords()) {
                                if (StringUtils.indexOf(keyword, "||") != -1) {
                                    keywords = StringUtils.split(keyword, "||");
                                    for (String k : keywords) {
                                        if (autoReply.getBarrage().contains(k)) {
                                            noShieldNum++;
                                            break;
                                        }
                                    }
                                } else {
                                    if (autoReply.getBarrage().contains(keyword)) {
                                        noShieldNum++;
                                    }
                                }
                            }
                            if (noShieldNum == keywordSize) {
                                if (StringUtils.isNotBlank(autoReplySet.getReply())) {
                                    is_send = handle(autoReplySet, null, autoReply, hourString, hour, hourReplace,
                                            is_send);
                                    break;
                                }
                            }
                        }
                    }
                }
                replyString = null;
                hourString = null;
                hourReplace = null;
                hour = 1;
                if (is_send) {
                    try {
                        Thread.sleep(new BigDecimal(getTime()).multiply(new BigDecimal("1000")).longValue());
                    } catch (Exception e) {
                    }
                }
                is_send = false;
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private synchronized boolean handle(AutoReplySet autoReplySet, String replyString, AutoReply autoReply,
                                     String hourString, short hour, String hourReplace, boolean is_send) {

        //拟议自动回复处理
        //1. 针对特定人?
        //2. 刷屏?
        String handledAutoReplyStr = handleReplyStr(autoReplySet.getReply());
        // 替换%NAME%参数
        if (!handledAutoReplyStr.equals("%NAME%")) {
            replyString = StringUtils.replace(handledAutoReplyStr, "%NAME%", autoReply.getName());
        } else {
            replyString = autoReply.getName();
        }
        // 替换%FANS%
        if (!replyString.equals("%FANS%")) {
            replyString = StringUtils.replace(replyString, "%FANS%", String.valueOf(PublicDataConf.FANSNUM));
        } else {
            replyString = String.valueOf(PublicDataConf.FANSNUM);
        }
        // 替换%TIME%
        if (!replyString.equals("%TIME%")) {
            replyString = StringUtils.replace(replyString, "%TIME%", JodaTimeUtils.format(new Date(),TimeZone.getTimeZone("GMT+08:00"),"yyyy-MM-dd HH:mm:ss"));
        } else {
            replyString =JodaTimeUtils.format(new Date(),TimeZone.getTimeZone("GMT+08:00"),"yyyy-MM-dd HH:mm:ss");
        }
        // 替换%LIVETIME%（缓存5分钟，房间信息不会频繁变化）
        if (!replyString.equals("%LIVETIME%")) {
            if (PublicDataConf.lIVE_STATUS == 1) {
                Long liveTime = getOrLoad("roomInit_" + PublicDataConf.ROOMID, 300_000L,
                        () -> HttpRoomData.httpGetRoomInit(PublicDataConf.ROOMID).getLive_time());
                replyString = StringUtils.replace(replyString, "%LIVETIME%",
                        CurrencyTools.getGapTime(System.currentTimeMillis() - liveTime * 1000));
            } else {
                replyString = StringUtils.replace(replyString, "%LIVETIME%", "0");
            }
        } else {
            if (PublicDataConf.lIVE_STATUS == 1) {
                Long liveTime = getOrLoad("roomInit_" + PublicDataConf.ROOMID, 300_000L,
                        () -> HttpRoomData.httpGetRoomInit(PublicDataConf.ROOMID).getLive_time());
                replyString = CurrencyTools.getGapTime(System.currentTimeMillis() - liveTime * 1000);
            } else {
                replyString = "0";
            }
        }
        // 替换%HOT%
        if (!replyString.equals("%HOT%")) {
            replyString = StringUtils.replace(replyString, "%HOT%", PublicDataConf.ROOM_POPULARITY.toString());
        } else {
            replyString = PublicDataConf.ROOM_POPULARITY.toString();
        }

        // 替换%WATHER%
        if (!replyString.equals("%WATHER%")) {
            replyString = StringUtils.replace(replyString, "%WATHER%", PublicDataConf.ROOM_WATCHER.toString());
        } else {
            replyString = PublicDataConf.ROOM_WATCHER.toString();
        }

        // 替换%LIKE%
        if (!replyString.equals("%LIKE%")) {
            replyString = StringUtils.replace(replyString, "%LIKE%", PublicDataConf.ROOM_LIKE.toString());
        } else {
            replyString = PublicDataConf.ROOM_LIKE.toString();
        }

        // 替换%BLOCK%参数 和 {{time}}时间参数
        if (replyString.contains("%BLOCK%")) {
            replyString = StringUtils.replace(replyString, "%BLOCK%", "");
            if (replyString.contains("{{") && replyString.contains("}}")) {
                hourString = replyString.substring(replyString.indexOf("{{") + 2, replyString.indexOf("}}"));
                if (hourString.matches("[0-9]+")) {
                    if (hour <= 720 && hour > 0) {
                        hour = Short.parseShort(hourString);
                    }
                }
                hourReplace = replyString.substring(replyString.indexOf("{{"), replyString.indexOf("}}") + 2);
                if (!replyString.equals(hourReplace)) {
                    replyString = StringUtils.replace(replyString, hourReplace, "");
                } else {
                    replyString = "";
                }
            }
            if (StringUtils.isNotBlank(PublicDataConf.USERCOOKIE)) {
                try {
                    if (HttpUserData.httpPostAddBlock(autoReply.getUid(), hour) != 0)
                        replyString = "";
                } catch (Exception e) {
                    // TODO: handle exception
                }
            }
        }

        if (StringUtils.isNotBlank(replyString)) {
            if (PublicDataConf.sendBarrageThread != null && !PublicDataConf.sendBarrageThread.FLAG) {
                PublicDataConf.barrageString.offer(replyString);
                is_send = true;
            }
        }
        return is_send;
    }


    public String handleReplyStr(String replyStr) {
        String replyStrs[] = null;
        if (StringUtils.indexOf(replyStr, "\n") != -1) {
            replyStrs = StringUtils.split(replyStr, "\n");
        }
        if(replyStrs!=null&&replyStrs.length>1) {
            return replyStrs[(int) Math.ceil(Math.random() * replyStrs.length)-1];
        }
        return replyStr;
    }


    public HashSet<AutoReplySet> getAutoReplySets() {
        if(!CollectionUtils.isEmpty(autoReplySets)){
            //过滤非空的关键字和回复语句
            return autoReplySets.stream()
                    .filter(autoReplySet -> !CollectionUtils.isEmpty(autoReplySet.getKeywords())&&StringUtils.isNotBlank(StringUtils.trim(autoReplySet.getReply())))
                    .collect(Collectors.toCollection(HashSet::new));
        }
        return autoReplySets;
    }
}
