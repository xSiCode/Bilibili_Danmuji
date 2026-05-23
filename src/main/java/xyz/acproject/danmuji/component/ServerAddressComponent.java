package xyz.acproject.danmuji.component;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import xyz.acproject.danmuji.conf.PublicDataConf;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Component
public class ServerAddressComponent implements ApplicationListener<WebServerInitializedEvent>{
	private static final Logger LOGGER = LogManager.getLogger(ServerAddressComponent.class);
	private int serverPort;
	
	
	public int getPort() {
		return this.serverPort;
	}
	public String getAddress() {
		InetAddress address = null;
		String addressStr = "";
		try {
			address=InetAddress.getLocalHost();
			addressStr = address.getHostAddress();
		} catch (UnknownHostException e) {
			// TODO 自动生成的 catch 块
			LOGGER.error(e);
			addressStr = "获取失败";
		}
		return "http://"+ addressStr +":"+this.serverPort;
	}

	public String getLocalAddress() {
		return "http://localhost:" +this.serverPort;
	}
	/**
	 * @return
	 */
	public String getRemoteAddress() {
		return "http://远程地址不可用:"+this.serverPort;
	}
	@Override
	public void onApplicationEvent(WebServerInitializedEvent event) {
		// TODO 自动生成的方法存根
		this.serverPort = event.getWebServer().getPort();
	}
	
}
