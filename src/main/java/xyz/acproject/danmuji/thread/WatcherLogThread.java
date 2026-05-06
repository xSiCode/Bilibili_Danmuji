package xyz.acproject.danmuji.thread;

import org.apache.commons.lang3.StringUtils;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.tools.file.LogFileTools;

public class WatcherLogThread extends Thread {
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
			if (null != PublicDataConf.watcherLogString && !PublicDataConf.watcherLogString.isEmpty()
					&& StringUtils.isNotBlank(PublicDataConf.watcherLogString.get(0))) {
				logString = PublicDataConf.watcherLogString.get(0);
				LogFileTools.getlogFileTools().logWatcherFile(logString);
				PublicDataConf.watcherLogString.remove(0);
			} else {
				synchronized (PublicDataConf.watcherLogThread) {
					try {
						PublicDataConf.watcherLogThread.wait();
					} catch (InterruptedException e) {
					}
				}
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
