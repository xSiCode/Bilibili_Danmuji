package xyz.acproject.danmuji.tools.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import xyz.acproject.danmuji.conf.LogPathConf;
import xyz.acproject.danmuji.conf.PublicDataConf;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @ClassName GuardFileTools
 * @Description TODO
 * @author BanqiJane
 * @date 2020年8月10日 下午12:28:31
 *
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
public class GuardFileTools {
	private static final Logger LOGGER = LogManager.getLogger(GuardFileTools.class);
	public static Map<Long, String> read() {
		String path = LogPathConf.getLogDir() + "/guardFile/";
		Map<Long, String> guardMap = new ConcurrentHashMap<>();
		File file = new File(path);
//		file.setWritable(true, false);
		if (file.exists() == false)
			file.mkdirs();
		file = new File(path + "/guards("+PublicDataConf.ROOMID +")"+ ".txt");
//		file.setWritable(true, false);
		if (file.exists() == false)
			try {
				file.createNewFile();
			} catch (IOException e) {
				// TODO 自动生成的 catch 块
				LOGGER.error(e);
			}
		BufferedReader bufReader = null;
		try {
			bufReader = new BufferedReader(new FileReader(file));
			String s = "";
			while ((s = bufReader.readLine()) != null) {
				String[] str = s.split(",");
				guardMap.put(Long.valueOf(str[0]), str[1]);
			}
		} catch (IOException e) {
			// TODO 自动生成的 catch 块
			LOGGER.error(e);
		} finally {
			try {
				if (bufReader != null)
					bufReader.close();
			} catch (IOException e) {
				// TODO 自动生成的 catch 块
				LOGGER.error(e);
			}
		}
		return guardMap;
	}

	public static void write(String msg) {
//		FileWriter fw = null;
		OutputStreamWriter os= null;
		BufferedWriter bw = null;
		PrintWriter pw = null;
		String path = LogPathConf.getLogDir() + "/guardFile/";
		try {
			// 如果文件存在，则追加内容；如果文件不存在，则创建文件
			File file = new File(path);
//			file.setWritable(true, false);
			if (file.exists() == false)
				file.mkdirs();
			file = new File(path + "guards("+PublicDataConf.ROOMID +")"+ ".txt");
//			file.setWritable(true, false);
			if (file.exists() == false)
				try {
					file.createNewFile();
				} catch (IOException e) {
					// TODO 自动生成的 catch 块
					LOGGER.error(e);
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
			LOGGER.error(e);
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					// TODO 自动生成的 catch 块
					LOGGER.error(e);
				}
			}
			if (bw != null) {
				try {
					bw.close();
				} catch (IOException e) {
					// TODO 自动生成的 catch 块
					LOGGER.error(e);
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
//					LOGGER.error(e);
//				}
//			}
		}
	}
}
