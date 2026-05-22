package xyz.acproject.danmuji.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;

/**
 * @author Jane
 * @ClassName UrlUtils
 * @Description TODO
 * @date 2021/3/29 16:57
 * @Copyright:2021
 */
public class UrlUtils {
	private static final Logger LOGGER = LogManager.getLogger(UrlUtils.class);


    public static String URLEncoderString(String str,String charset) {
        String result = "";
        if (StringUtils.isBlank(str)) {
            return "";
        }
        if(StringUtils.isBlank(charset)){
            charset = "UTF-8";
        }
        try {
            result = java.net.URLEncoder.encode(str, charset);
        } catch (UnsupportedEncodingException e) {
            LOGGER.error(e);
        }
        return result;
    }

}
