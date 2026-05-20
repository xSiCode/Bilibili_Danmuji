package xyz.acproject.danmuji.tools.file;

import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.*;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LogFileTools {
	private volatile static LogFileTools logFileTools;
	private static final LogFileTools instance = new LogFileTools();
	private final Object fileLock = new Object();

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
								e.printStackTrace();
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

	public void logFile(String msg) {
		String path = System.getProperty("user.dir");
		FileTools fileTools = new FileTools();
		StringBuilder stringBuilder = new StringBuilder();
		try {
			path = URLDecoder.decode(fileTools.getBaseJarPath().toString(), "utf-8");
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		try {
			path = path + "/Danmuji_log/";
			File file = new File(path);
			if (file.exists() == false)
				file.mkdirs();
			stringBuilder.append(JodaTimeUtils.getCurrentDateString());
			stringBuilder.append("(");
			stringBuilder.append(PublicDataConf.ROOMID);
			stringBuilder.append(")");
			file = new File(path + stringBuilder.toString() + ".txt");
			stringBuilder.delete(0, stringBuilder.length());
			if (file.exists() == false)
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			batchQueue.offer(new LogEntry(file.getAbsolutePath(), msg));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void logWatcherFile(String msg) {
		String path = System.getProperty("user.dir");
		FileTools fileTools = new FileTools();
		StringBuilder stringBuilder = new StringBuilder();
		try {
			path = URLDecoder.decode(fileTools.getBaseJarPath().toString(), "utf-8");
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		try {
			path = path + "/Danmuji_log/";
			File file = new File(path);
			if (file.exists() == false)
				file.mkdirs();
			stringBuilder.append(JodaTimeUtils.getCurrentDateString());
			stringBuilder.append("(");
			stringBuilder.append(PublicDataConf.ROOMID);
			stringBuilder.append(")");
			stringBuilder.append("viewers");
			file = new File(path + stringBuilder.toString() + ".txt");
			stringBuilder.delete(0, stringBuilder.length());
			if (file.exists() == false)
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			batchQueue.offer(new LogEntry(file.getAbsolutePath(), msg));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void logFollowingsFile(String msg) {
		synchronized (fileLock) {
			String path = System.getProperty("user.dir");
			FileTools fileTools = new FileTools();
			try {
				path = URLDecoder.decode(fileTools.getBaseJarPath().toString(), "utf-8");
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			path = path + "/Danmuji_log/";
			File dir = new File(path);
			if (!dir.exists()) {
				dir.mkdirs();
			}
			StringBuilder stringBuilder = new StringBuilder();
			stringBuilder.append(JodaTimeUtils.getCurrentDateString());
			stringBuilder.append("(").append(PublicDataConf.ROOMID).append(")");
			stringBuilder.append("followings");
			File file = new File(path + stringBuilder.toString() + ".txt");
			batchQueue.offer(new LogEntry(file.getAbsolutePath(), msg));
		}
	}

	public synchronized void logTestFile(String msg) {
		String path = System.getProperty("user.dir");
		FileTools fileTools = new FileTools();
		StringBuilder stringBuilder = new StringBuilder();
		try {
			path = URLDecoder.decode(fileTools.getBaseJarPath().toString(), "utf-8");
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		try {
			path = path + "/Danmuji_log/";
			File file = new File(path);
			if (file.exists() == false)
				file.mkdirs();
			stringBuilder.append(JodaTimeUtils.getCurrentDateString());
			stringBuilder.append("(");
			stringBuilder.append(PublicDataConf.ROOMID);
			stringBuilder.append(")");
			stringBuilder.append("testLog");
			file = new File(path + stringBuilder.toString() + ".txt");
			stringBuilder.delete(0, stringBuilder.length());
			if (file.exists() == false)
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			batchQueue.offer(new LogEntry(file.getAbsolutePath(), msg));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
