package xyz.acproject.danmuji.thread.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.service.impl.ClientServiceImpl;
import xyz.acproject.danmuji.utils.SpringUtils;

/**
 * @ClassName ReConnThread
 * @Description 断线重连线程，指数退避策略
 * @author BanqiJane
 * @date 2020年8月10日 下午12:29:52
 *
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
public class ReConnThread extends Thread {
	public volatile boolean RFLAG = false;
	private static final Logger LOGGER = LogManager.getLogger(ReConnThread.class);
	private volatile int num = 0;
	private static final int MAX_RETRIES = 100;
	private static final int INITIAL_DELAY_MS = 5000;
	private static final int MAX_DELAY_MS = 300000;
	private ClientServiceImpl clientService = SpringUtils.getBean(ClientServiceImpl.class);
	private int currentDelay = INITIAL_DELAY_MS;

	@Override
	public void run() {
		super.run();
		while (!RFLAG) {
			try {
				Thread.sleep(currentDelay);
			} catch (InterruptedException e) {
				LOGGER.error("重连线程被中断", e);
				Thread.currentThread().interrupt();
				return;
			}
			if (RFLAG) {
				return;
			}
			// 指数退避：5s → 10s → 20s → 40s → 80s → 160s → 300s (上限)
			if (num < MAX_RETRIES) {
				currentDelay = Math.min(INITIAL_DELAY_MS * (1 << Math.min(num, 6)), MAX_DELAY_MS);
			}
			if (PublicDataConf.webSocketProxy != null && PublicDataConf.webSocketProxy.isOpen()) {
				num = 0;
				currentDelay = INITIAL_DELAY_MS;
				RFLAG = true;
				return;
			}
			try {
				clientService.reConnService();
			} catch (Exception e) {
				LOGGER.error("重连失败", e);
			}
			num++;
			LOGGER.info("每{}秒,进行重连第{}次", currentDelay / 1000, num);
		}
	}

}
