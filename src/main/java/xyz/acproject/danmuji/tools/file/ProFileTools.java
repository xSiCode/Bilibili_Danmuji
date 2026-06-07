package xyz.acproject.danmuji.tools.file;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @ClassName ProFileTools
 * @Description TODO
 * @author BanqiJane
 * @date 2020年8月10日 下午12:28:42
 *
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
@Slf4j
public class ProFileTools {
	private static final String STORE_DIR;
	static {
		String override = System.getProperty("danmuji.store.dir");
		if (override != null && !override.isEmpty()) {
			STORE_DIR = override;
		} else {
			FileTools fileTools = new FileTools();
			String tmp;
			try {
				tmp = URLDecoder.decode(fileTools.getBaseJarPath().toString(), "utf-8");
			} catch (Exception e1) {
				log.warn(e1.getMessage(),e1);
				tmp = System.getProperty("user.dir");
			}
			STORE_DIR = tmp;
		}
	}

	public static String getStoreDir() {
		return STORE_DIR;
	}

	/**
	 * 读取profile文件内容 转为 Map对象
	 * @param filename 文件名称,非绝对地址
	 * @return is not null
	 * @throws IOException io流处理异常
	 * @throws FileNotFoundException 文件未找到
	 */
	public static Map<String, String> read(String filename) throws IOException{
		File file = new File(STORE_DIR);
		file.mkdirs();
		file = new File(STORE_DIR + "/" + filename);
		Map<String, String> profileMap = new ConcurrentHashMap<>();
		if (file.createNewFile()){
			//如果成功创建了空文件则直接返回空Map
			return profileMap;
		}

		String dataString;
		String[] strings;
		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))){
			while ((dataString = bufferedReader.readLine()) != null) {
				strings = dataString.split(":@:", 2);
				if (strings.length == 2) {
					String key = strings[0];
					String val = strings[1];
					// set 的值可能跨多行（格式化 JSON），读到 EOF 为止
					if ("set".equals(key)) {
						StringBuilder sb = new StringBuilder(val);
						String nextLine;
						while ((nextLine = bufferedReader.readLine()) != null) {
							sb.append("\n").append(nextLine);
						}
						val = sb.toString();
					}
					profileMap.put(key, val);
				}
			}
		} catch (FileNotFoundException e) {
			log.warn("文件{}不存在!",file.getAbsolutePath());
			throw e;
		} catch(IOException e) {
			log.error(e.getMessage(),e);
			throw e;
		}
		return profileMap;

	}

	public static void write(Map<String, String> profileMap, String filename) {
		File file = new File(STORE_DIR);
		file.mkdirs();
		file = new File(STORE_DIR + "/" + filename);
		try {
			file.createNewFile();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		try (OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
			 BufferedWriter bufferedWriter = new BufferedWriter(os)) {
			// 先写非 set 的键值对
			for (Map.Entry<String, String> entry : profileMap.entrySet()) {
				if (!"set".equals(entry.getKey())) {
					bufferedWriter.write(entry.getKey());
					bufferedWriter.write(":@:");
					bufferedWriter.write(entry.getValue());
					bufferedWriter.write("\r\n");
				}
			}
			// set 键放最后，支持多行值
			String setValue = profileMap.get("set");
			if (setValue != null) {
				bufferedWriter.write("set:@:");
				bufferedWriter.write(setValue);
				bufferedWriter.write("\r\n");
			}
			bufferedWriter.flush();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
	}
}
