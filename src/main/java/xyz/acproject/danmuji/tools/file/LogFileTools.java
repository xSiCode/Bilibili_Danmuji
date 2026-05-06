package xyz.acproject.danmuji.tools.file;

import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.utils.JodaTimeUtils;

import java.io.*;
import java.net.URLDecoder;

/**
 * @ClassName LogFileTools
 * @Description TODO
 * @author BanqiJane
 * @date 2020年8月10日 下午12:28:39
 *
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
public class LogFileTools {
	private volatile static LogFileTools logFileTools;
	// 1. 单例模式，确保全局只有一个日志工具实例
	private static final LogFileTools instance = new LogFileTools();

	// 2. 锁对象，专门用于文件写入同步
	private final Object fileLock = new Object();

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
//		FileWriter fw = null;
		OutputStreamWriter os= null;
		BufferedWriter bw = null;
		PrintWriter pw = null;
		String path = System.getProperty("user.dir");
		FileTools fileTools = new FileTools();
		StringBuilder stringBuilder = new StringBuilder();
		try {
			path = URLDecoder.decode(fileTools.getBaseJarPath().toString(), "utf-8");
		} catch (Exception e1) {
			// TODO 自动生成的 catch 块
			e1.printStackTrace();
		}
		try {
			// 如果文件存在，则追加内容；如果文件不存在，则创建文件
			path = path + "/Danmuji_log/";
			File file = new File(path);
//			file.setWritable(true, false);
			if (file.exists() == false)
				file.mkdirs();
			stringBuilder.append(JodaTimeUtils.getCurrentDateString());
			stringBuilder.append("(");
			stringBuilder.append(PublicDataConf.ROOMID);
			stringBuilder.append(")");
			file = new File(path + stringBuilder.toString() + ".txt");
//			file.setWritable(true, false);
			stringBuilder.delete(0, stringBuilder.length());
			if (file.exists() == false)
				try {
					file.createNewFile();
				} catch (IOException e) {
					// TODO 自动生成的 catch 块
					e.printStackTrace();
				}
			os = new OutputStreamWriter(new FileOutputStream(file,true),"utf-8");
			bw = new BufferedWriter(os);
//			fw = new FileWriter(file, true);
			pw = new PrintWriter(bw);
			pw.println(msg);
			os.flush();
			bw.flush();
			pw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					// TODO 自动生成的 catch 块
					e.printStackTrace();
				}
			}
			if (bw != null) {
				try {
					bw.close();
				} catch (IOException e) {
					// TODO 自动生成的 catch 块
					e.printStackTrace();
				}
			}
			if (pw != null) {
				pw.close();
			}
//			if (fw != null) {
//				try {
//					fw.close();
//				} catch (IOException e) {
//					// TODO 自动生成的 catch 块
//					e.printStackTrace();
//				}
//			}
		}
	}

	public void logWatcherFile(String msg) {
		OutputStreamWriter os = null;
		BufferedWriter bw = null;
		PrintWriter pw = null;
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
			os = new OutputStreamWriter(new FileOutputStream(file, true), "utf-8");
			bw = new BufferedWriter(os);
			pw = new PrintWriter(bw);
			pw.println(msg);
			os.flush();
			bw.flush();
			pw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (bw != null) {
				try {
					bw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (pw != null) {
				pw.close();
			}
		}
	}

	/**
	 * 写入关注列表日志
	 * 使用 synchronized 确保同一时间只有一个线程能进入这个方法
	 */
	public void logFollowingsFile(String msg) {
		// 3. 加上同步锁，防止多线程并发写入导致的内容交叉
		synchronized (fileLock) {
			OutputStreamWriter os = null;
			BufferedWriter bw = null;
			PrintWriter pw = null;

			try {
				// --- 路径处理逻辑保持不变 ---
				String path = System.getProperty("user.dir");
				FileTools fileTools = new FileTools();
				try {
					// 注意：getBaseJarPath 需要确保不为空
					path = URLDecoder.decode(fileTools.getBaseJarPath().toString(), "utf-8");
				} catch (Exception e1) {
					e1.printStackTrace();
				}

				path = path + "/Danmuji_log/";
				File dir = new File(path);
				if (!dir.exists()) {
					dir.mkdirs();
				}

				// --- 文件名构建 ---
				StringBuilder stringBuilder = new StringBuilder();
				stringBuilder.append(JodaTimeUtils.getCurrentDateString());
				stringBuilder.append("(").append(PublicDataConf.ROOMID).append(")");
				stringBuilder.append("followings");

				File file = new File(path + stringBuilder.toString() + ".txt");

				// --- 核心写入逻辑 ---
				// 使用 true 开启追加模式
				os = new OutputStreamWriter(new FileOutputStream(file, true), "utf-8");
				bw = new BufferedWriter(os);
				pw = new PrintWriter(bw);

				// 写入数据
				pw.println(msg);

				// 立即刷新
				pw.flush();

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				// 4. 务必在 finally 中关闭流，释放文件锁
				if (pw != null) try { pw.close(); } catch (Exception e) {}
				if (bw != null) try { bw.close(); } catch (Exception e) {}
				if (os != null) try { os.close(); } catch (Exception e) {}
			}
		}
	}
	/**
	 * 写入粉丝日志
	 * 添加 synchronized 关键字，确保线程安全，防止并发写入导致文件内容错乱
	 */
	public synchronized void logFollowersFile(String msg) {
		OutputStreamWriter os = null;
		BufferedWriter bw = null;
		PrintWriter pw = null;
		String path = System.getProperty("user.dir");
		FileTools fileTools = new FileTools();
		StringBuilder stringBuilder = new StringBuilder();

		// 1. 获取基础路径
		try {
			path = URLDecoder.decode(fileTools.getBaseJarPath().toString(), "utf-8");
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		try {
			// 2. 拼接日志目录
			path = path + "/Danmuji_log/";
			File file = new File(path);
			if (file.exists() == false)
				file.mkdirs();

			// 3. 构建文件名
			stringBuilder.append(JodaTimeUtils.getCurrentDateString());
			stringBuilder.append("(");
			stringBuilder.append(PublicDataConf.ROOMID);
			stringBuilder.append(")");
			stringBuilder.append("followers");

			file = new File(path + stringBuilder.toString() + ".txt");
			stringBuilder.delete(0, stringBuilder.length());

			if (file.exists() == false)
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}

			// 4. 创建流 (追加模式)
			os = new OutputStreamWriter(new FileOutputStream(file, true), "utf-8");
			bw = new BufferedWriter(os);
			pw = new PrintWriter(bw);

			// 5. 写入数据
			pw.println(msg);

			// 6. 刷新缓冲区
			os.flush();
			bw.flush();
			pw.flush();

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// 7. 关闭资源
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (bw != null) {
				try {
					bw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (pw != null) {
				pw.close();
			}
		}
	}
}
