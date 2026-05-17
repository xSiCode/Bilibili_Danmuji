# 负黑自动小黑屋姬 & 负黑正白打分姬 功能文档

---

## 概述

两个功能均基于「负黑正白判定表」（`负黑正白判定表.json`）运作，在 `processFollowings` 方法中串联调用。触发时机：观众进入直播间（`INTERACT_WORD_V2`，`msg_type=1`），且用户的关注列表可见。

### 调用链

```
ParseMessageThread (INTERACT_WORD_V2)
  └→ HttpRoomData.processFollowings(vmid, uname)
       ├→ 计算 totalScore（遍历关注列表与 pnScoreMap 匹配累加）
       ├→ processAutoBlackList(vmid, uname, totalScore)   // 仅在用户不在 pnScoreMap 中时
       └→ processPnScore(vmid, uname, totalScore)         // 仅在用户不在 pnScoreMap 中时
```

**关键前提**：两个姬只在用户当前 **不在** `pnScoreMap`（负黑正白判定表内存映射）中时才会执行。已在判定表中的用户直接取表内分数，不再触发。

---

## 一、负黑自动小黑屋姬

### 1.1 界面

| 控件 | 说明 |
|------|------|
| 开关 checkbox | 启用/关闭自动拉黑 |
| 拉黑分数 input | 整数，默认 `-1`。输入 `>0` 时前端弹窗告警 |
| 表格 | 时间、被拉黑用户名（可点击跳转 B 站空间）、打分分数（触发拉黑时的 totalScore）、解除拉黑按钮、删除显示按钮 |
| 分页 | 每页 5 条，最多保留 50 条 |

### 1.2 拉黑判定规则

开关打开时，根据 `totalScore` 和设定的 `blackScore` 进行判断：

#### blackScore < 0（如 -1）

| totalScore 范围 | 动作 |
|-----------------|------|
| ≤ blackScore | 拉黑 + 判定表记 -2 |
| blackScore < totalScore ≤ 0 | 不处理 |
| > 0 | 判定表记 +2 |

#### blackScore = 0

| totalScore 范围 | 动作 |
|-----------------|------|
| ≤ -1 | 拉黑 + 判定表记 -2 |
| = 0 | 仅拉黑（不写判定表） |
| > 0 | 判定表记 +2 |

### 1.3 核心方法

```
HttpRoomData.processAutoBlackList(vmid, uname, totalScore) → boolean
```

- 返回 `true` 表示实际执行了拉黑操作（API 调用成功）
- 拉黑 API：`HttpUserData.httpPostAddBadList(vmid)` → B 站接口 `POST /x/relation/modify`（act=5）
- 判定表更新：`updatePnScoreForUser(vmid, uname, newPnScore)` → 写入 `负黑正白判定表.json`

### 1.4 数据存储

| 数据类型 | 存储位置 | 格式 |
|----------|----------|------|
| 开关设置（enabled, black_score） | `DanmujiProfile`（CenterSetConf.autoBlackList） | JSON |
| 拉黑记录 | `自动小黑屋记录.json` | `{"records": [{uid, uname, score, time}]}` |

### 1.5 REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/getAutoBlackList` | GET | 获取设置 + 拉黑记录列表 |
| `/saveAutoBlackList` | POST | 保存开关/拉黑分数（enabled, black_score） |
| `/auto_unblack` | GET | 解除拉黑（调 B 站 API + 删除记录） |
| `/auto_black_delete_display` | GET | 仅删除显示记录，不解除拉黑 |

### 1.6 日志标记

成功拉黑后日志追加 `[已自动拉黑]`。

---

## 二、负黑正白打分姬

### 2.1 界面

| 控件 | 说明 |
|------|------|
| 开关 checkbox | 启用/关闭打分姬，开启后自动记录进入的未打分观众 |
| 启用默认打分 checkbox | 根据 totalScore 正负自动赋值 |
| 表格 | 时间、用户名（可点击跳转 B 站空间）、打分分数（可编辑 input）、删除显示按钮、保存按钮 |
| 分页 | 每页 10 条，最多保留 100 条 |

### 2.2 筛选条件

**仅记录不在「负黑正白判定表」中的用户**。开关打开时，每个进入且不在 `pnScoreMap` 中的观众都会被记录到打分明细中。

### 2.3 默认打分规则

当「启用默认打分」打开时：

| totalScore | 自动打分 | 是否写入判定表 |
|------------|----------|---------------|
| < 0 | -2 | 是 |
| > 0 | +2 | 是 |
| = 0 | 不处理 | 否 |

### 2.4 手动操作

| 按钮 | 行为 |
|------|------|
| **保存** | 将当前行编辑的分数写入 `负黑正白判定表.json`，同时从打分记录中移除该用户（同一 uid 全部移除） |
| **删除显示** | 仅从打分记录中移除该行（按 uid + time 精确匹配），不修改判定表 |

### 2.5 核心方法

```
HttpRoomData.processPnScore(vmid, uname, totalScore) → boolean
```

- 返回 `true` 表示默认打分已执行（写入了判定表）
- 总是添加记录到 `addPnScoreRecord`
- 若 `default_scoring=true` 且 totalScore ≠ 0，调用 `updatePnScoreForUser` 写入判定表

### 2.6 数据存储

| 数据类型 | 存储位置 | 格式 |
|----------|----------|------|
| 开关设置（enabled, default_scoring） | `DanmujiProfile`（CenterSetConf.pnScore） | JSON |
| 打分记录 | `负黑正白打分记录.json` | `{"records": [{uid, uname, total_score, score, time}]}` |

### 2.7 REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/getPnScoreRecords` | GET | 获取设置 + 打分记录列表 |
| `/savePnScoreSettings` | POST | 保存开关/默认打分（enabled, default_scoring） |
| `/savePnScoreToTable` | POST | 保存单用户分数到判定表（uid, uname, score），并移除记录 |
| `/deletePnScoreRecord` | GET | 删除显示记录（uid, time），不修改判定表 |

### 2.8 日志标记

默认打分执行后日志追加 `[已自动打分]`。

---

## 三、共享机制

### 判定表更新（updatePnScoreForUser）

两个功能共用此方法，操作 `负黑正白判定表.json`：

- 若 uid 已存在 → 更新 score 和 name
- 若 uid 不存在 → 在列表头部插入新条目
- 写入后立即调用 `reloadPnScoreMap()` 刷新内存映射

文件格式：
```json
{
  "type": "负黑正白判定表",
  "followings_list": [
    {"uid": 123456, "score": -2, "name": "用户名"}
  ]
}
```

### 执行顺序

```
processFollowings 内（用户不在 pnScoreMap 时）：
  1. processAutoBlackList  → 可能拉黑 + 写入判定表
  2. processPnScore        → 记录 + 可能默认打分写入判定表
```

两个姬**独立判断，互不阻塞**。自动拉黑成功不影响打分记录；反之亦然。

---

## 四、配置文件汇总

| 文件 | 用途 |
|------|------|
| `DanmujiProfile` | 所有姬的开关设置持久化（Base64 编码） |
| `负黑正白判定表.json` | uid → score 映射，公开编辑 |
| `自动小黑屋记录.json` | 自动拉黑历史，仅展示 |
| `负黑正白打分记录.json` | 待打分观众明细，展示+操作 |
