# R2 review 紀錄 — Slice 1b plan v2

- 日期：2026-09-22（subagent ×2 並行；反方 26 calls／裁判断 27 calls）
- 裁決：**反方 7 : 正方 3（v2 唔過）**；中立裁判：`v2_adequate = false`，但「唔係方向錯，係仲差 5 個具體修訂」
- 標的：plan v2（B1 prose 層＋B2 lang 措辭 via generator＋B3 顯示層＋B4 唯一性）
- 全文：`C:\Users\skps9\AppData\Local\hermes\cache\delegation\subagent-summary-0-20260922_020724_745037.txt`（反方）／`...-1-20260922_020724_745547.txt`（裁判断）

## 反方（7）卡死點（Hermes 逐條親核 → 全部成立）
1. **B3 邊界假設錯**：`Plainify.lootLine` **同時**餵模型 tool result 同玩家顯示 → 改非 `blocks` 分支，`AcquireJarRoutesCheck.java:104-106`（斷言 acquire 輸出含 raw `chests/village/moon/blacksmith`）必紅。**親核**：`AcquireJarRoutesCheck:88-107` 斷言 `AcquireAskTool` 輸出含 raw path；`Plainify` 非 blocks 分支＝`ReplyLang.lootTableObtain`（「掉落表：<raw id>」）；`ReplyLang:849-853`（jarLoot）同樣輸 raw。
2. **B2 會打爛 runtime 偵測器**：`AskLoopState.isEmptyOrMiss:632-634` 靠「未索引／not indexed」字樣判斷 miss → 改措辭即失效。
3. **白名單漏**：`tests/check_reply_prompt_keys.py:50-53`（**要求** `acquire_index_miss` 含「未索引／not indexed」）／`WorldgenFactsCheck.java:209-212`／`tests/check_worldgen_lookup.py:352-359,457-459`／`tests/update_reply_prompts.py:364-377`（**lang 生成器，手改會被無聲覆寫**）。
4. **neoforge 樹**：python 閘（`check_honest_miss.py:12-16` 等）掃 forge＋neoforge×3 lang，而 `AGENTS.md` 禁改 neoforge → B2 冇解。
5. **NC2／NC4 唔會紅**、§4.3「FAIL=0」今日 baseline 已有 1 紅（`check_ask_display_leak` RC=2）。

## 中立裁判（親量）
- R1 反方強、正方機制修正對但估分偏高；**v2 三處致命不自洽**（B2 撞 `check_reply_prompt_keys`／B3 撞 `AcquireJarRoutesCheck`／lang 生成器覆寫）。
- 建議：出 v2.1 修 5 項，**或收窄本 slice 只做 B1＋B4**（只動 `AskReplyScrub.java`），B2／B3 拆去 Slice 1c。
- 親量：19 條 ask trace 全部有 body；`check.scrub` 37 條、`before!=after` **2** 條。

→ **Hermes 決定收窄**（v4）：只做 B1＋B4，B2／B3／neoforge 決策全部移 Slice 1c。
