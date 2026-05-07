package xyz.acproject.danmuji.utils;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.springframework.lang.Nullable;

import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

public final class JodaTimeUtils {
	private static final String FORMAT_DATETIME = "yyyy-MM-dd HH:mm:ss";
	private static final String FORMAT_DATE ="yyyy-MM-dd";


	/**
	 * 获取10位时间戳
	 * @return
	 */
	public static long getTimestamp() {
		return System.currentTimeMillis()/1000;
	}

	/**
	 * 获取当前系统时间字符串
	 * @return yyyy-MM-dd HH:mm:ss
	 */
	public static String getCurrentDateTimeString() {
	    DateTime dt = new DateTime();
	    String time = dt.toString(FORMAT_DATETIME);
	    return time;
	}

	/**
	 * 获取当前日期字符串
	 * @return
	 */
	public static String getCurrentDateString() {
	    DateTime dt = new DateTime();
	    String date = dt.toString(FORMAT_DATE);
	    return date;
	}

    /**
     * 获取当前毫秒
     * @return
     */
    public static long getcurrMills() {
    	Instant instant = Instant.now();
    	return instant.getMillis();
    }


	/**
	 * 按照时区转换时间
	 * @param date
	 * @param timeZone 时区
	 * @param parrten
	 * @return
	 */
	@Nullable
	public static String format(Date date, TimeZone timeZone, String parrten) {
		if (date == null) {
			return null;
		}
		DateTime dateTime = new DateTime(date);
		dateTime.withZone(DateTimeZone.forTimeZone(timeZone));
		return dateTime.toString(parrten);
	}

	/**
	 * 格式化日期字符串
	 * @param date 日期
	 * @param pattern 日期格式
	 * @return
	 */
	@Nullable
	public static String format(Date date, String pattern) {
	    if (date == null) {
	        return null;
	    }
		DateTime dateTime = new DateTime(date);
		return dateTime.toString(pattern);
	}

	public static Integer formatToInt(Date date, String pattern) {
		if (date == null) {
			return null;
		}
		DateTime dateTime = new DateTime(date);
		return Integer.parseInt(dateTime.toString(pattern));
	}

	/**
	 * 解析日期
	 * @param date 日期字符串
	 * @return
	 */
	@Nullable
	public static Date parse(String date) {
	    if (date == null) {
	        return null;
	    }
	    Date resultDate = DateTime.parse(date).toDate();
	    return resultDate;
	}

	public static Date parse(String date,String pattern){
		if(StringUtils.isBlank(pattern)){
			return parse(date);
		}
		DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(pattern);
		return DateTime.parse(date,dateTimeFormatter).toDate();
	}


	/**
	 * 解析日期 yyyy-MM-dd HH:mm:ss
	 * @param mills
	 * @return
	 */
	public static String format(Long mills, String pattern) {
	    String dateStr = "";
	    if (null == mills || mills.longValue() < 0) {
	        return dateStr;
	    }
	    try {
	        DateTime dateTime = new DateTime(mills);
	        dateStr = dateTime.toString(pattern);
	    } catch (Exception e) {
	        // ignore
	    }

	    return dateStr;
	}

	/**
	 * 解析日期 yyyy-MM-dd HH:mm:ss
	 * @param mills
	 * @return
	 */
	public static String formatDateTime(Long mills) {
	    String dateStr = "";
	    if (null == mills || mills.longValue() < 0) {
	        return dateStr;
	    }
	    try {
			DateTime dateTime = new DateTime(mills);
			dateStr = dateTime.toString(FORMAT_DATETIME);
	    } catch (Exception e) {
	        // ignore
	    }

	    return dateStr;
	}

	/**
	 * 获取某月之前,之后某一个月最后一天,24:59:59
	 * @return
	 */
	public static Date monthLastDay(Date date, Integer month) {
	    DateTime dt1;
	    if (month == null) {
	        month = 0;
	    }
		if(month!=0){
			month = -month;
		}
	    if (date == null) {
	        dt1 = new DateTime().minusMonths(month);
	    } else {
	        dt1 = new DateTime(date).minusMonths(month);
	    }
	    DateTime lastDay = dt1.dayOfMonth().withMaximumValue().
	            withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
	    return lastDay.toDate();
	}

	/**
	 *获取某月月之前,之后某一个月第一天,00:00:00
	 * @return
	 */
	public static Date monthFirstDay(Date date, Integer month) {
	    DateTime dt1;
	    if (month == null) {
	        month = 0;
	    }
	    if(month!=0){
	    	month = -month;
		}
	    if (date == null) {
	        dt1 = new DateTime().minusMonths(month);
	    } else {
	        dt1 = new DateTime(date).minusMonths(month);
	    }
	    DateTime lastDay = dt1.dayOfMonth().withMinimumValue().
	            withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0);
	    return lastDay.toDate();
	}

	/**
	 * @param date
	 * @param offset
	 * @return
	 */
	public static Date changeDay(Date date, int offset) {
	    DateTime dt1;
	    if(offset>=0) {
			if (date == null) {
				dt1 = new DateTime().plusDays(offset);
				return dt1.toDate();
			}
			dt1 = new DateTime(date).plusDays(offset);
		}else{
			if (date == null) {
				dt1 = new DateTime().minusDays(-1*offset);
				return dt1.toDate();
			}
			dt1 = new DateTime(date).minusDays(-1*offset);
		}
	    return dt1.toDate();

	}
	public static int getMonthDay(Date date){
		DateTime dateTime = new DateTime(date);
		return dateTime.dayOfMonth().getMaximumValue();
	}

	/**
	* 从20210416起 弃用 请使用getYear(null)
	*/
	@Deprecated
	public static int getCurrentYear(){
		DateTime dateTime = new DateTime(new Date());
		return dateTime.getYear();
	}
	public static int getYear(Date date){
		if(date==null){
			date = new Date();
		}
		DateTime dateTime = new DateTime(date);
		return dateTime.getYear();
	}

	public static int getMoth(Date date){
		if(date==null){
			date = new Date();
		}

		DateTime dateTime = new DateTime(date);

		return dateTime.getMonthOfYear();
	}


	public static Date getZero(Date date){
		DateTime dateTime = new DateTime(date);
		dateTime = dateTime.withMillisOfDay(0);
		return dateTime.toDate();
	}

}
