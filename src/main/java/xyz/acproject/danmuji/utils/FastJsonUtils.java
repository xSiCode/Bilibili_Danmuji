package xyz.acproject.danmuji.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.serializer.SimpleDateFormatSerializer;

import java.util.Date;
import java.util.List;

/**
 * 依赖于fastjson包
 * @author zjian
 * @version fastjsonTools v1.0
 */
public class FastJsonUtils {
	private static final SerializeConfig CONFIG = new SerializeConfig();

	private static final String FORMAT_TIME = "yyyy-MM-dd HH:mm:ss";

	@SuppressWarnings("unused")
	private static final String FORMAT_DATE = "yyyy-MM-dd";

	private static final SerializerFeature[] FEATURES = {

				SerializerFeature.WriteMapNullValue,

				SerializerFeature.WriteDateUseDateFormat,

				SerializerFeature.WriteNullListAsEmpty

	}; 

	

//	WriteMapNullValue

//	WriteDateUseDateFormat

	static {

		CONFIG.put(Date.class, new SimpleDateFormatSerializer(FORMAT_TIME));

	}

	

	public static <T> T parseObject(String json, Class<T> clazz) {

		try {

			T t = JSON.parseObject(json, clazz);

			return t;

		} catch (Exception e) {

			e.printStackTrace();

		}

		return null;

	}



	/**

	 * 某人转为yyyy-MM-dd HH:mm:ss格式

	 * @param object

	 * @return

	 */

	public static String toJson(Object object) {

		try {

			String json = JSON.toJSONString(object, CONFIG, FEATURES);

			return json;

		} catch (Exception e) {

			e.printStackTrace();

		}

		return null;

	}

}

