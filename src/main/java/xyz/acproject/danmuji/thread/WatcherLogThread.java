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
			if (PublicDataConf.webSocketProxy != null && !PublicDataConf.webSocketProxy.isOpen()) {
				return;
			}
			logString = PublicDataConf.watcherLogString.poll();
			if (logString != null && StringUtils.isNotBlank(logString)) {
				LogFileTools.getlogFileTools().logWatcherFile(logString);
			} else {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				LOGGER.error(e);
			}
		}
	}
}
