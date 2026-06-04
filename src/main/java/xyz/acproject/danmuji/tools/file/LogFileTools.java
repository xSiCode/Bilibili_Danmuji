package xyz.acproject.danmuji.tools.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import xyz.acproject.danmuji.conf.LogPathConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LogFileTools {
	private static final Logger LOGGER = LogManager.getLogger(LogFileTools.class);
	private volatile static LogFileTools logFileTools;

	// ---- 路径缓存，避免每次调用都重复计算 ----
	private static volatile String baseDirPath;

	private static String getBaseDirPath() {
		String p = baseDirPath;
		if (p == null) {
			p = LogPathConf.getLogDir() + "/";
			new File(p).mkdirs();
			baseDirPath = p;
		}
		return p;
	}

	// 各日志方法的路径缓存
	private volatile String filePathCache;
	private volatile String filePathKey;
	private volatile String followingsPathCache;
	private volatile String followingsPathKey;
	private volatile String testPathCache;
	private volatile String testPathKey;

	// 批量日志写入条目
	private static class LogEntry {
		final String filePath;
		final String message;
		LogEntry(String filePath, String message) {
			this.filePath = filePath;
			this.message = message;
		}
	}

	// 日志批量写入队列 + 后台写入线程
	private static final LinkedBlockingQueue<LogEntry> batchQueue = new LinkedBlockingQueue<>(20000);

	static {
		Thread writer = new Thread(() -> {
			while (true) {
				try {
					LogEntry first = batchQueue.poll(1, TimeUnit.SECONDS);
					if (first != null) {
						Map<String, List<String>> batches = new HashMap<>();
						List<String> list = new ArrayList<>();
						list.add(first.message);
						batches.put(first.filePath, list);

						List<LogEntry> more = new ArrayList<>();
						batchQueue.drainTo(more, 500);
						for (LogEntry e : more) {
							batches.computeIfAbsent(e.filePath, k -> new ArrayList<>()).add(e.message);
						}

						for (Map.Entry<String, List<String>> batch : batches.entrySet()) {
							try (BufferedWriter bw = new BufferedWriter(
									new OutputStreamWriter(new FileOutputStream(batch.getKey(), true), "utf-8"), 8192)) {
								for (String msg : batch.getValue()) {
									bw.write(msg);
									bw.newLine();
								}
							} catch (Exception e) {
								LOGGER.error(e);
							}
						}
					}
				} catch (InterruptedException e) {
					break;
				}
			}
		});
		writer.setDaemon(true);
		writer.setName("LogFileWriter");
		writer.start();
	}

	private LogFileTools() {}

	public static LogFileTools getlogFileTools() {
		if (logFileTools == null) {
			synchronized (LogFileTools.class) {
				if (logFileTools == null) {
					logFileTools = new LogFileTools();
				}
			}
		}
		return logFileTools;
	}

	private static String safeFileName(String s) {
		if (s == null || s.isEmpty()) return "unknown";
		return s.replaceAll("[\\\\/:*?\"<>|]", "_");
	}

	public void logFile(String msg) {
		String key = PublicDataConf.ROOMID + "_" + safeFileName(PublicDataConf.ANCHOR_NAME) + "_8_" + "log";
		String fp = filePathCache;
		if (fp == null || !key.equals(filePathKey)) {
			fp = getBaseDirPath() + key + ".txt";
			filePathCache = fp;
			filePathKey = key;
		}
		batchQueue.offer(new LogEntry(fp, msg));
	}

	public void logFollowingsFile(String msg) {
		String key = PublicDataConf.ROOMID + "_"+ safeFileName(PublicDataConf.ANCHOR_NAME) + "_9_"  + "followings";
		String fp = followingsPathCache;
		if (fp == null || !key.equals(followingsPathKey)) {
			fp = getBaseDirPath() + key + ".txt";
			followingsPathCache = fp;
			followingsPathKey = key;
		}
		batchQueue.offer(new LogEntry(fp, msg));
	}

	public void logTestFile(String msg) {
		String key =PublicDataConf.ROOMID + "_" + safeFileName(PublicDataConf.ANCHOR_NAME) + "_10_" + "testLog";
		String fp = testPathCache;
		if (fp == null || !key.equals(testPathKey)) {
			fp = getBaseDirPath() + key + ".txt";
			testPathCache = fp;
			testPathKey = key;
		}
		batchQueue.offer(new LogEntry(fp, msg));
	}
}
