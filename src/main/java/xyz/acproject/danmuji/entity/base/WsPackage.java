package xyz.acproject.danmuji.entity.base;

import lombok.Data;
import xyz.acproject.danmuji.utils.FastJsonUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
@Data
public class WsPackage implements Serializable,Cloneable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 4807973278850564054L;
	private static final Logger LOGGER = LogManager.getLogger(WsPackage.class);
	private static WsPackage wsPackage = new WsPackage();
	private String cmd;
	private Short status;
	private Object result;
	


	public static WsPackage getWsPackage() {
		try {
			return (WsPackage) wsPackage.clone();
		} catch (Exception e) {
			// TODO 自动生成的 catch 块
			LOGGER.error(e);
		}
		return new WsPackage();
	}
	public static WsPackage getWsPackage(String cmd,Short status,Object result) {
		try {
			WsPackage ws = (WsPackage) wsPackage.clone();
			ws.setCmd(cmd);
			ws.setStatus(status);
			ws.setResult(result);
			return ws;
		} catch (Exception e) {
			// TODO 自动生成的 catch 块
			LOGGER.error(e);
		}
		return new WsPackage();
	}
	public static String toJson(String cmd,Short status,Object result) {
		try {
			WsPackage ws = (WsPackage) wsPackage.clone();
			ws.setCmd(cmd);
			ws.setStatus(status);
			ws.setResult(result);
			return FastJsonUtils.toJson(ws);
		} catch (Exception e) {
			// TODO 自动生成的 catch 块
			LOGGER.error(e);
		}
		return "";
	}
}
