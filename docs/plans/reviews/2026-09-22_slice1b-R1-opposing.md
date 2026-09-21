# R1 反方 review 紀錄 — Slice 1b plan v1

- 日期：2026-09-22（subagent，leaf，~4 分鐘，25 API calls）
- 裁決：**verdict = fail｜比分 正 3 : 反 7**
- 標的：`docs/plans/2026-09-22-slice1b-player-text-closeout.md`（v1）
- 全文：`C:\Users\skps9\AppData\Local\hermes\cache\delegation\subagent-summary-0-20260922_020105_147804.txt`
  （live transcript：`...\cache\delegation\live\deleg_a88efeee\task-0.log`）

## 阻塞清單（8 條，全部已吸收入 plan v2/v3）

1. **B1 做錯層**：`PLAYER_UNSAFE_MARKERS` 只 gate FACT fallback（`AskReplyScrub.java:170` 註釋；呼叫點 `AskEngine.java:734/946/1231/1746`、`AskMissFallback.java:74`）；玩家 body 走 `proseOrFacts`(`:1578`)→`scrubPromptEcho`(`:906`)，該檔「索引」pattern = 0。真機：`ask-20260922-000814-minecraft_diamond.jsonl` 兩條 `check.scrub` `before==after`，body 仍含「本包未索引到钻石的世界生成资料」。
   → 修：B1 改 prose 層 rewrite；`InternalJargonCheck` 斷言 `scrubPromptEcho(prose)` 而唔係 marker 表。
2. **B2 撞兩個未列白名單嘅硬 python 閘**：`check_reply_prompt_keys.py:50-53`（`acquire_index_miss` 必須含「not indexed／未索引」）＋`:455-467`（`fact_check` 含「not indexed／未索引／unknown advancement gate」）；`check_honest_miss.py:83-88`、`:112-115` 同。
3. **B3 會令兩個 harness 紅**：`LootLineHumanizeCheck.java:35-36`（斷言 `lootTableObtain` 回 raw 表名＝正正 B3 要改嘅行為）；`AcquireJarRoutesCheck.java:104-106`（斷言 acquire 輸出含 raw path）。`research/gen_tmp_check.py` 每個 harness `-ea` → assert 真跑。
4. **B4 冇實作點＋靶唔中**：白名單冇任何檔負責；真機中招個案**有配方卡**（`ask-20260921-074545-ino_dlc_saber…:61`「黑暗祭坛就是目前已知的唯一来源」）；v1 引用嘅 crying_obsidian trace 全檔冇「唯一」。
5. **禁字表太窄**：真機 9 條句式（「本包索引没有…」「…索引都没有…」）一條都唔中 v1 嘅 5 phrase → 真機判準會假 PASS。
6. **驗收工具唔存在＋baseline 假設錯**：`tests/check_ask_display_leak.py` docstring 明言**永不掃 model prose**；實跑 RC=2 `NO LOG LINES`；全部 python 閘實測 **125 個、1 紅**（就係佢）→ §4.3「FAIL=0」唔成立。
7. **lang 有 generator 會覆寫**：`tests/update_reply_prompts.py` `:371/:377` 仍含「未索引」→ 手改會無聲回退；另 `tests/check_worldgen_lookup.py:457/:459` 係 `WorldgenFacts.missLine` 鏡像，硬斷舊措辭 → 唔更新就變空轉（測自己）。
8. **NC3 假負控**：`check_slice1_reply_keys.py` KEYS 唔含 `acquire_index_miss`，BANNED 亦早已含「未索引／not indexed」→ NC3 永遠唔會紅。

## 非阻塞觀察（已參考）

- `PLAYER_UNSAFE_MARKERS` 加「未索引」對 FACT fallback 幾乎 no-op（`acquire_index_miss` 本身含「請明說未知」，已被現有 marker 踢走）。
- 真機 LLM 答案非確定性（同一物品兩次結果唔同）→ 只可當 spot check，唔可當 regression gate。
- harness 數字要寫 58/58（57＋新 check）。
- en 側同樣有 jargon（`WorldgenFacts.java:102`「this pack has no indexed worldgen for: X」＋`WorldgenFactsCheck.java:209` 保護佢）。
- B3 斷言邊界：只能證明我們嘅行無 raw，證明唔到模型唔會自行翻譯（例：見到 `citadel` 自己寫「城堡」）→ plan 要明寫。
- 還原方法要照 Slice 1 寫法（sha＋backup 路徑）。

## 我方獨立核實（唔照抄）

| 反方 claim | 我實測 | 結果 |
|---|---|---|
| marker 表只 gate FACT fallback | 讀 `AskReplyScrub.java:170` 註釋＋`:1578-1584` flow | ✅ 成立 |
| `check_reply_prompt_keys.py` 要「未索引」 | 讀 `:50-52` | ✅ 成立 |
| `AcquireJarRoutesCheck:104-106` 會紅 | 讀 `:104-106`＋`AcquireAskTool.humanJarRoute`→`Plainify.lootLine:197` | ✅ 成立（v2 我寫錯，v3 已改） |
| python 閘 1 個已知紅 | 親跑全部 125 個 | ✅ 成立（`check_ask_display_leak.py` RC=2） |
| generator 會覆寫 lang | 讀 `update_reply_prompts.py:371/377` | ✅ 成立 |
| 13 條 trace body 句式清單 | 自己掃 `display.body.final`（頂層 `body` 欄位！`data.body` 係空） | ✅ 成立，另加 sandbox 3 條 |
