package xyz.acproject.danmuji.thread;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.http.HttpUserData;

/**
 * @author BanqiJane
 * @ClassName SendBarrageThread
 * @Description TODO
 * @date 2020年8月10日 下午12:30:43
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
public class SendBarrageThread extends Thread {
    private Logger LOGGER = LogManager.getLogger(SendBarrageThread.class);
    public volatile boolean FLAG = false;

    private static final long SEND_INTERVAL_MS = 1200;

    @Override
    public void run() {
        super.run();
        String barrageStr = null;
        long lastSendTime = 0;
        while (!FLAG) {
            if (FLAG) {
                return;
            }
            if (PublicDataConf.webSocketProxy == null || !PublicDataConf.webSocketProxy.isOpen()) {
                // WebSocket 未就绪（未连接或已断开），等待重连后继续，而非直接退出线程
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            barrageStr = PublicDataConf.barrageString.poll();
            if (barrageStr != null && StringUtils.isNotBlank(barrageStr)) {
                // Enforce minimum interval since last send
                long elapsed = System.currentTimeMillis() - lastSendTime;
                if (elapsed < SEND_INTERVAL_MS) {
                    try {
                        Thread.sleep(SEND_INTERVAL_MS - elapsed);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                int strLength = barrageStr.length();
                int maxLength = 20;
                if (PublicDataConf.USERBARRAGEMESSAGE != null) {
                    maxLength = PublicDataConf.USERBARRAGEMESSAGE.getDanmu().getLength();
                }
                if (strLength > maxLength) {
                    int num = (int) Math.ceil((float) strLength / (float) maxLength);
                    for (int i = 0; i < num; i++) {
                        if (FLAG) return;
                        // Enforce interval between split chunks
                        if (i > 0) {
                            try {
                                Thread.sleep(SEND_INTERVAL_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        try {
                            String barrageStr_split = StringUtils.substring(barrageStr, i * maxLength, strLength > maxLength * (i + 1) ? maxLength * (i + 1) : strLength);
                            if (HttpUserData.httpPostSendBarrage(barrageStr_split) != 0) {
                                break;
                            }
                        } catch (Exception e) {
                            System.err.println("发送弹幕线程抛出:" + e);
                        }
                    }
                } else {
                    try {
                        HttpUserData.httpPostSendBarrage(barrageStr);
                    } catch (Exception e) {
                    }
                }
                lastSendTime = System.currentTimeMillis();
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
