package xyz.acproject.danmuji;

import java.lang.reflect.Field;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import xyz.acproject.danmuji.conf.CenterSetConf;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.service.impl.SetServiceImpl;

/**
 * @ClassName BiliBiliDanmujiApplication
 * @Description TODO
 * @author BanqiJane
 * @date 2020年8月10日 下午12:31:52
 *
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
@EnableScheduling
@SpringBootApplication
public class BiliBiliDanmujiApplication implements CommandLineRunner{
	private SetServiceImpl checkService;
	
	public static void main(String[] args) {
		SpringApplication.run(BiliBiliDanmujiApplication.class, args);
	}

	private static final Logger LOGGER = LogManager.getLogger(BiliBiliDanmujiApplication.class);

	@Override
	public void run(String... args) throws Exception {
		applyCliOverrides(args);
		checkService.init();
	}

	/**
	 * 解析命令行参数 --danmuji.conf.<字段名>=<值>，反射设置 CenterSetConf 的对应字段。
	 * 参数无效或异常时静默忽略，仅 warn 日志，不影响程序启动。
	 */
	private void applyCliOverrides(String... args) {
		if (args == null) return;
		if (PublicDataConf.centerSetConf == null) return;
		final String prefix = "--danmuji.conf.";
		for (String arg : args) {
			if (!arg.startsWith(prefix)) continue;
			String kv = arg.substring(prefix.length());
			int eq = kv.indexOf('=');
			if (eq <= 0) continue; // 需要 "key=value"，等号不能在开头
			String fieldName = kv.substring(0, eq);
			String value = kv.substring(eq + 1);
			try {
				Field field = CenterSetConf.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				Object converted = convertValue(value, field.getType());
				if (converted != null) {
					field.set(PublicDataConf.centerSetConf, converted);
					LOGGER.info("CLI覆盖: {}={}", fieldName, value);
				} else {
					LOGGER.warn("CLI覆盖跳过(类型不支持或值无效): {}={}", fieldName, value);
				}
			} catch (NoSuchFieldException | IllegalAccessException e) {
				LOGGER.warn("CLI覆盖跳过(字段不存在或无法访问): {}", fieldName);
			}
		}
	}

	/**
	 * 将字符串值转换为目标字段类型。转换失败返回 null，不抛异常。
	 */
	private Object convertValue(String value, Class<?> type) {
		try {
			if (type == boolean.class) return Boolean.parseBoolean(value);
			if (type == long.class || type == Long.class) return Long.parseLong(value);
			if (type == int.class || type == Integer.class) return Integer.parseInt(value);
			if (type == String.class) return value;
		} catch (NumberFormatException ignored) {
			// 值格式不对，如 --danmuji.conf.roomid=abc
		}
		return null;
	}

	@Autowired
	public void setCheckService(SetServiceImpl checkService) {
		this.checkService = checkService;
	}
}
