package xyz.acproject.danmuji.thread;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.tools.file.LogFileTools;

/**
 * @ClassName LogThread
 * @Description TODO
 * @author BanqiJane
 * @date 2020年8月10日 下午12:30:30
 *
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
public class LogThread extends Thread{
	private static final Logger LOGGER = LogManager.getLogger(LogThread.class);
//	@SuppressWarnings("unused")
//	private Logger LOGGER = LogManager.getLogger(LogThread.class);
	public volatile boolean FLAG = false;
	@Override
	public void run() {
		// TODO 自动生成的方法存根
		String logString = null;
		super.run();
		while (!FLAG) {
			if (FLAG) {
				return;
			}
			// WebSocket 断开时不退出线程，继续消费队列中残留的日志
			try {
				logString = PublicDataConf.logString.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			if (logString != null && StringUtils.isNotBlank(logString)) {
				LogFileTools.getlogFileTools().logFile(logString);
			}
		}
	}
}
