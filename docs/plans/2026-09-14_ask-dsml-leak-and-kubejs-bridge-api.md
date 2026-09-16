# Plan：Ask 回覆「DSML 洩漏」＋ KubeJS bridge 零命中（2026-09-14）

狀態：**cursor 已實作（待真機驗收）** — 2026-09-14 19:10
來源：SK 實機測試截圖（問「过去之章」）＋ `latest.log`（`AI_test_NFWC_DIM`）

---

## 一、症狀（實機證據）

`logs/latest.log`（2026-09-14 18:44–18:54）：

| 時間 | 事件 | 判定 |
|---|---|---|
| 18:44:41 | `Pack AI kubejs bridge reload=mtime` | bridge 有載入 |
| 18:48:58 / 18:49:00 / 18:49:02 | `raw reply … toolCalls=4` | ✔ 正常（native tool calls 解析到）|
| 18:49:09 | `chars=836 toolCalls=0`（純 prose） | ✔ 正常（直接答）|
| 18:49:48 / 18:49:51 | `toolCalls=4 / 2` | ✔ |
| 18:49:57 / 18:52:20 / 18:52:27 / 18:52:36 | `toolCalls=4 / 4 / 2`，cards 3 / 2 | ✔ |
| 18:54:25 / 18:54:28 | `toolCalls=4 / 3` | ✔ |
| **18:54:30** | **`chars=488 toolCalls=0`，body = `<｜DSML｜ calls>` + `<｜DSML｜ invoke name="item_search">…** | ❌ **洩漏** |
| 18:54:30 | `display body ver=0.2.1 src=playerfacts` | ❌ 最終顯示跌落 deterministic 事實卡（唔係自然答覆）|
| 18:48:54 / 18:49:46 / 18:52:19 / 18:54:23 | **`kubejs bridge hits=0 mode=scan`**（4/4）| ⚠️ M1e API 路徑**零命中**，每次都跌落 M1c 掃描 |

即：**SK 講「一開始好正常」係對嘅** — 10 次 LLM round 之中 9 次正常，最後一次壞。

---

## 二、網上研究（SK 指示：先查線上）

### 2.1 DSML 洩漏＝DeepSeek V4 **已知** model/serving 層缺陷（唔係我哋獨有）

- **官方格式定義**（A）：DeepSeek 官方 repo `DeepSeek-V4-Flash-0731/encoding/encoding_dsv4.py` 明確定義 DSML 編碼 —
  `<{dsml_token}tool_calls>` → `<{dsml_token}invoke name="$TOOL_NAME">` → `<{dsml_token}parameter name="{key}" string="{is_str}">{value}</{dsml_token}parameter>`
  https://huggingface.co/deepseek-ai/DeepSeek-V4-Flash-0731/blob/main/encoding/encoding_dsv4.py
- **serving 層必須顯式轉換**：vLLM/SGLang 要用 `--tool-call-parser deepseek_v4`；冇 parser 就 markup 原樣流入 `content`
- **同類個案（B/C，逐條有 URL）**
  - cline#13348：**~1/4 有 tool 嘅 run** 洩漏 DSML 落 `delta.content`（同時 `tool_calls.arguments` 退化成 `{}`）→「client 信 tool_calls 就得空 call，client 渲染 content 就得垃圾」
  - cherry-studio#14714：`deepseek-v4-pro` 開內建搜尋後，最終回覆位置顯示 `<|DSML| tool_calls>…` 而唔係答案
  - vllm#53227：**streaming** 模式 DSML markup 洩漏入 tool call arguments（非 streaming 正常）
  - vllm#28219 / sglang#17561：tool call 放喺 `content`、`tool_calls` 空、`finish_reason=stop`
- **我哋 mod 現狀**：已經有 `AskToolLoop.parseLeakedToolXml(...)`、`parseEmbeddedToolCalls(...)`、`hasEmbeddedToolDump(...)`；call sites 喺 `AskToolLoop.java:437 / 551 / 570`。但今次仍然以 `toolCalls=0` ＋ `src=playerfacts` 收場 → **需要逐行 trace 呢條路徑點解冇救返**（候選：round budget 到頂、`state.canLlm()` 為 false、`hasEmbeddedToolDump` 判 false、或 scrub fail-closed 清空答案）

### 2.2 M1e bridge 零命中＝應該用 **KubeJS public API**（我對**本機安裝版本**驗證）

`javap` 直接讀 `mods/kubejs-forge-1902.6.2-build.73.jar`（唔係靠 GitHub 猜）：

```
EventGroup:  public static Map<String,EventGroup> getGroups()
             public Map<String,EventHandler> getHandlers()
EventHandler: public Set<Object> findUniqueExtraIds(ScriptType)          ← public!
              public void forEachListener(ScriptType, Consumer<EventHandlerContainer>)  ← public!
              private Map<Object,EventHandlerContainer[]> extraEventContainers        ← 現時反射呢個（private）
EventHandlerContainer: public final Object extraId; public final IEventHandler handler;
                       public final String source; public final int line;               ← 我哋要嘅 A-tier 出處
ScriptType:  STARTUP / SERVER / CLIENT
```
→ **唔需要反射 private 欄位**：`findUniqueExtraIds` + `forEachListener` 就係官方支援入口，且同時拎到 `extraId`＋`source`＋`line`。
（`EventGroupWrapper` 係 script 面向嘅 wrapper；`Extra.ID/REQUIRES_ID` 解釋點解 item 事件會有 extra id。）

---

## 三、建議改動（bounded，兩項獨立）

### Fix A — DSML 洩漏救援（`LlmClient` / `AskToolLoop`）
1. `LlmClient.completeWithTools()`：`parseNativeToolCalls()` 之後，**若 `calls.isEmpty()` 且 content 有洩漏標記** → 呼叫既有 `AskToolLoop.parseEmbeddedToolCalls(content)` 補回 calls；log 加 `dsmlRecovered=N`（單行，方便現場核）
2. 若補回 0 個 call：維持現有 scrub fail-closed，但**唔准**靜靜清空成條答案 —— 改成 fallback 顯示原文 scrub 後內容（若空白才出事實卡）
3. 新增 harness `AskDsmlLeakCheck`：**用 log 內實際洩漏字串**（fullwidth `｜` 版本）→ 期望解析出 `item_search` ×1（item=`mrqx_extra_pack`）
4. 既有 `AskToolLoopCheck` 個案（GRAVEYARD_DSML）保留，新增 ASCII `|` + 空格變體（`< | DSML | calls>`）

### Fix B — KubeJS bridge 轉 public API（`KubeJsApiBridge`）
1. 改用 `EventGroup.getGroups()` → `getHandlers()` → 每個 handler：
   `findUniqueExtraIds(ScriptType.SERVER)`（＋`CLIENT`）→ 對每個 id：`forEachListener(ScriptType, c -> { c.extraId / c.source / c.line })`
2. item id 正規化：`extraId` 係 `Object`（可能係 `Item` / `Ingredient` / `String`）→ 統一轉 `toString()` 再抽 `ns:path`
3. 加診斷 log（**一次**，ask 時）：`Pack AI kubejs bridge probe groups=N handlers=M extraIds=J entries=K matched=X`
4. private 反射路徑**保留做 fallback**（版本差異保險），優先序：public API → 反射 → 磁碟掃描
5. 擴充 harness `AskKubeJsBridgeCheck`：期望 `mode=api` 且 `hits>0`（有 KubeJS 時）；無 KubeJS 時要老實 `mode=scan` / `hits=0`

---

## 四、驗收標準（做完要逐項實測）

1. `./gradlew.bat compileJava compileTestJava` RC=0
2. 全部 harness（31 個 → 32 個）**Check OK**，包括新 `AskDsmlLeakCheck`
3. 真機：重問「过去之章」x5 → log 唔再出現 `toolCalls=0` 而係 `dsmlRecovered≥1` 或正常 native calls；顯示層唔再跌落 `src=playerfacts`
4. 真機：`kubejs bridge hits>0 mode=api`（第一次見到 api 模式）
5. python `tests/check_*.py` = baseline（3 個既有 FAIL 唔可以變多）

## 五、風險 / 還原
- 風險：極低（純讀 API＋解析 fallback）；最壞情況＝改動後 bridge 攞唔到嘢 → 自動跌落現有掃描（行為同今日一樣）
- 還原：`git revert <commit>`；jar 還原用 `%TEMP%\packai_deploy_backup_20260914_1842\`（現行 0.2.1 已在 mods）
- 派工規則：**只改 `forge/1.19.2`**；一個 writer；唔准 commit／push；冇 shell 唔准聲稱跑過 build；用完清 `tmp-check.gradle`
