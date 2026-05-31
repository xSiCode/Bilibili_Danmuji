package xyz.acproject.danmuji.thread;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.tools.file.LogFileTools;

public class WatcherLogThread extends Thread {
	private static final Logger LOGGER = LogManager.getLogger(WatcherLogThread.class);
	public volatile boolean FLAG = false;

	@Override
	public void run() {
		String logString = null;
		super.run();
		while (!FLAG) {
			if (FLAG) {
				return;
			}
			// WebSocket 断开时不退出线程，继续消费队列中残留的日志
			try {
				logString = PublicDataConf.watcherLogString.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			if (logString != null && StringUtils.isNotBlank(logString)) {
				LogFileTools.getlogFileTools().logWatcherFile(logString);
			}
		}
	}
}
