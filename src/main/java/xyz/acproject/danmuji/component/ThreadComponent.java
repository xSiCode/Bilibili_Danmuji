package xyz.acproject.danmuji.component;

import xyz.acproject.danmuji.conf.CenterSetConf;
import xyz.acproject.danmuji.conf.set.*;

import java.util.HashSet;

/**
 * @ClassName ThreadComponent
 * @Description TODO
 * @author BanqiJane
 * @date 2020年8月10日 下午12:20:47
 *
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
public interface ThreadComponent {

	void closeAll();

	void closeUser(boolean close);

	// 开启处理弹幕包线程 core
	boolean startParseMessageThread(
			CenterSetConf centerSetConf);

	// 开启心跳线程 core
	boolean startHeartByteThread();

	// 开启日志线程
	boolean startLogThread();
 

	// 开启公告线程 need login
	boolean startAdvertThread(AdvertSetConf advertSetConf);

	// 开启自动回复线程 need login
//	boolean startAutoReplyThread(CenterSetConf centerSetConf);
	boolean startAutoReplyThread(AutoReplySetConf autoReplySetConf);
	// 开启发送弹幕线程 need login
	boolean startSendBarrageThread();


	// 开启public的礼物感谢线程
	void startParseThankGiftThread(ThankGiftSetConf thankGiftSetConf, HashSet<ThankGiftRuleSet> thankGiftRuleSets);

	// 开启public的关注感谢线程
	void startParseThankFollowThread(ThankFollowSetConf thankFollowSetConf);

	void startParseThankWelcomeThread(ThankWelcomeSetConf thankWelcomeSetConf);

	// 设置处理弹幕包线程
	void setParseMessageThread(
			CenterSetConf centerSetConf);

	// 设置公告线程 need login
//	void setAdvertThread(CenterSetConf centerSetConf);
	void setAdvertThread(AdvertSetConf advertThread);
	// 设置自动回复线程 need login
//	void setAutoReplyThread(CenterSetConf centerSetConf);
	void setAutoReplyThread(AutoReplySetConf autoReplySetConf);
	// 关闭处理弹幕包线程 core
	void closeParseMessageThread();

	// 关闭心跳线程 core
	void closeHeartByteThread();
	
	// 开启观众日志线程

	// 关闭日志线程
	void closeLogThread();

	// 关闭观众日志线程

	void closeAdvertThread();

	void closeAutoReplyThread();

	void closeSendBarrageThread();

	void closeGiftShieldThread();

	void closeFollowShieldThread();

	void closeWelcomeShieldThread();

}
