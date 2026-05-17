package xyz.acproject.danmuji.thread;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import xyz.acproject.danmuji.conf.PublicDataConf;
import xyz.acproject.danmuji.entity.danmu_data.Interact;

import java.util.Vector;

/**
 * @ClassName ParseThankFollowThread
 * @Description TODO
 * @author BanqiJane
 * @date 2020年8月10日 下午12:30:34
 *
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
@Getter
@Setter
public class ParseThankFollowThread extends Thread {
//	private Logger LOGGER = LogManager.getLogger(ParseThankFollowThread.class);
	public volatile boolean FLAG = false;
	public volatile boolean COOLDOWN = false;
	private String thankFollowString = "感谢 %uNames% 的关注";
	private Short num = 1;
	private Long delaytime = 3000L;
	private Long timestamp = System.currentTimeMillis();
	@Override
	public void run() {
		String thankFollowStr = null;
		StringBuilder stringBuilder = new StringBuilder(300);
		java.util.List<Interact> interacts = new java.util.ArrayList<Interact>();
		synchronized (timestamp) {
			while (!FLAG) {
				if (FLAG) {
					return;
				}
				if(PublicDataConf.webSocketProxy!=null&&!PublicDataConf.webSocketProxy.isOpen()) {
					return;
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO 自动生成的 catch 块
				}

				if (COOLDOWN) {
					continue;
				}

				PublicDataConf.interacts.drainTo(interacts);
				if(interacts.size()>0) {
					// 立刻进入间隔期，丢弃间隔期内新来的事件
					COOLDOWN = true;
					for (int i = 0; i < interacts.size(); i += getNum()) {
						for (int j = i; j < i + getNum(); j++) {
							if (j >= interacts.size()) {
								break;
							}
							stringBuilder.append(interacts.get(j).getUname()).append(",");
						}
						stringBuilder.delete(stringBuilder.length() - 1, stringBuilder.length());

						thankFollowStr =StringUtils.replace(handleThankStr(getThankFollowString()), "%uNames%", stringBuilder.toString());
						stringBuilder.delete(0, stringBuilder.length());
						if (PublicDataConf.sendBarrageThread != null
								&& !PublicDataConf.sendBarrageThread.FLAG) {
							PublicDataConf.barrageString.offer(thankFollowStr);
						}
						thankFollowStr = null;
					}
					interacts.clear();
					try {
						Thread.sleep(getDelaytime());
					} catch (InterruptedException e) {
					}
					COOLDOWN = false;
				}
			}
		}

	}

	public String handleThankStr(String thankStr) {
		String thankFollowStrs[] = null;
		if (StringUtils.indexOf(thankStr, "\n") != -1) {
			thankFollowStrs = StringUtils.split(thankStr, "\n");
		}
		if(thankFollowStrs!=null&&thankFollowStrs.length>1) {
			return thankFollowStrs[(int) Math.ceil(Math.random() * thankFollowStrs.length)-1];
		}
		return thankStr;
	}


}
