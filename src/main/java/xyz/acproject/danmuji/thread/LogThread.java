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
			if(PublicDataConf.webSocketProxy!=null&&!PublicDataConf.webSocketProxy.isOpen()) {
				return;
			}
			logString = PublicDataConf.logString.poll();
			if(logString != null && StringUtils.isNotBlank(logString)) {
				LogFileTools.getlogFileTools().logFile(logString);
			} else {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// TODO 自动生成的 catch 块
				LOGGER.error(e);
			}
		}
	}
}
