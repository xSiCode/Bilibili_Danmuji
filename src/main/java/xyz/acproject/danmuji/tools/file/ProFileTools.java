package xyz.acproject.danmuji.tools.file;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URLDecoder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @ClassName ProFileTools
 * @Description profile 文件读写。新格式: 纯 JSON 对象；兼容旧 key:@:value 行格式。
 * @author BanqiJane
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
	 * 读取 profile 文件为 Map。优先尝试 JSON 格式，失败则回退到旧 key:@:value 行格式。
	 */
	public static Map<String, String> read(String filename) throws IOException{
		File file = new File(STORE_DIR);
		file.mkdirs();
		file = new File(STORE_DIR + "/" + filename);
		Map<String, String> profileMap = new ConcurrentHashMap<>();
		if (file.createNewFile()){
			return profileMap;
		}

		// 读取整个文件内容
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
		}
		String content = sb.toString().trim();

		// 1. 尝试 JSON 格式: {"key":"val", "set":{...}}
		if (content.startsWith("{")) {
			try {
				JSONObject json = JSON.parseObject(content);
				for (String key : json.keySet()) {
					Object val = json.get(key);
					if (val instanceof JSONObject) {
						profileMap.put(key, ((JSONObject) val).toJSONString());
					} else {
						profileMap.put(key, json.getString(key));
					}
				}
				return profileMap;
			} catch (Exception e) {
				log.warn("JSON profile parse failed, trying legacy format: {}", e.getMessage());
			}
		}

		// 2. 回退: 旧 key:@:value 行格式
		for (String dataString : content.split("\n")) {
			dataString = dataString.trim();
			if (dataString.isEmpty()) continue;
			String[] strings = dataString.split(":@:", 2);
			if (strings.length == 2) {
				profileMap.put(strings[0], strings[1]);
			}
		}
		return profileMap;
	}

	/**
	 * 以格式化 JSON 对象写入 profile 文件，直接可读。
	 */
	public static void write(Map<String, String> profileMap, String filename) {
		File file = new File(STORE_DIR);
		file.mkdirs();
		file = new File(STORE_DIR + "/" + filename);
		try {
			file.createNewFile();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		JSONObject json = new JSONObject();
		for (Map.Entry<String, String> e : profileMap.entrySet()) {
			// set 键的值是 JSON 字符串，尝试反序列化为对象嵌入
			if ("set".equals(e.getKey())) {
				try {
					json.put(e.getKey(), JSON.parseObject(e.getValue()));
				} catch (Exception ex) {
					json.put(e.getKey(), e.getValue());
				}
			} else {
				json.put(e.getKey(), e.getValue());
			}
		}
		try (OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
			 BufferedWriter writer = new BufferedWriter(os)) {
			writer.write(JSON.toJSONString(json, true));
			writer.flush();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
	}
}
