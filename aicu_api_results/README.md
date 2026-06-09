# aicu.cc API 测试结果

**测试 UID**: 9846871
**测试时间**: 2026-06-09
**主 API 地址**: https://api.aicu.cc
**备用 API 地址**: https://apibackup2.aicu.cc:88

---

## API 端点汇总

| # | 端点 | 状态 | 说明 |
|---|------|------|------|
| 1 | `GET /api/v3/home/getnotice` | ✅ 可用 | 获取公告 |
| 2 | `GET /api/v3/help/getlist` | ✅ 可用 | 获取帮助文档列表 |
| 3 | `GET /api/v3/user/getusermark?uid=` | ✅ 可用 | 用户标记（公会/设备/历史用户名） |
| 4 | `GET /api/v3/user/getmedal?uid=` | ✅ 可用 | 用户粉丝牌 |
| 5 | `GET /api/v3/user/getcollection?uid=` | ✅ 可用 | 用户装扮 |
| 6 | `GET /api/v3/search/getreply?uid=&pn=&ps=&mode=&keyword=` | ✅ 可用 | 查评论（210条） |
| 7 | `GET /api/v3/search/getvideodm?uid=&pn=&ps=&keyword=` | ✅ 可用 | 查视频弹幕（32条） |
| 8 | `GET /api/v3/search/getlivedm?uid=&pn=&ps=&keyword=` | ✅ 可用 | 查直播弹幕（2条） |
| 9 | `GET https://worker.aicu.cc/api/bili/space?mid=` | ❌ Cloudflare | B站空间信息（被CF 5秒盾拦截） |
| 10 | `POST /ai` | ⚠️ 未测试 | AI 流式接口 |

## 结论

- 主 API (api.aicu.cc) **全部无认证即可访问**，无需 API Key
- worker.aicu.cc 被 Cloudflare 保护，curl 无法直接调用
- `getlivedm` 返回了用户名"洛洛无痕"（与 getusermark 中的历史用户名吻合），证明确实是 UID 9846871 的数据
