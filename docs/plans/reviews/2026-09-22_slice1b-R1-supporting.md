# R1 正方 review 紀錄 — Slice 1b plan v1

- 日期：2026-09-22（subagent，leaf，~3 分鐘，26 API calls）
- 裁決：**比分 正 6.5 : 反 3.5**（認同三類問題真存在；但指出兩條載重機制寫錯）
- 全文：`C:\Users\skps9\AppData\Local\hermes\cache\delegation\subagent-summary-1-20260922_020105_148839.txt`
  （live transcript：`...\cache\delegation\live\deleg_a88efeee\task-1.log`）

## 已核實／已推翻（逐條）

| 主張 | 判定 | 證據 |
|---|---|---|
| P1 玩家答案出現「未索引」（真） | ✅ 確認 | `ask-20260922-000814 diamond` / `001206 amethyst` 嘅 `display.body.final`（src=prose）原文 |
| P1 源自 `acquire_index_miss`（因果） | ❌ **推翻** | 全 trace 掃「本包事实无此物」「请明说未知」＝**0 次**；真源頭＝`tool.result worldgen_lookup`（`[WORLDGEN] 此包未索引到 … worldgen`）＋`send.system`（`fact_check` 規則 19 含「未索引」×2） |
| B1 假設擴充 marker 表即可 | ❌ **推翻** | prose 走 `scrubPromptEcho`（`AskReplyScrub.java:906-930`；`AskEngine.java:879/921`、`AskResult.java:90/193`、`AskService.java:2117`、`RecipeEmbed.java:1729`）；`PLAYER_UNSAFE_MARKERS` 只 gate FACT（`:1522-1536`→`playerSafeFacts`）；全檔搜「索引／worldgen」＝0 |
| `Plainify.lootLine` 對 `chests/*` 出 raw | ✅ 確認 | `Plainify.java:182-198`（`:197` `lootTableObtain`）＋`ReplyLang.java:454-456`＋zh_cn `"packai.reply.loot_table_obtain": "掉落表：%s"`；live 路徑 `Plainify.java:232-235`→`AskEngine.java:1463`／`LlmClient.java:467`；trace `tool.result`：`掉落表：bygonenether:chests/catacomb/treasure_rib` |
| 驗收工具鏈真存在 | ✅ 確認 | `forge/1.19.2/gradlew.bat`、JDK 路徑、`tmp-check.gradle`（57 entry）、`research/gen_tmp_check.py`（掃 `*Check.java` 生成 task）、57 個 `*Check.java`、125 個 `tests/check_*.py`、沙盒 trace 樣本齊 |
| NC3（改 `acquire_index_miss` → python 禁字閘紅） | ❌ **推翻** | `check_slice1_reply_keys.py` BANNED 只比對 KEYS（6 個 key，唔含 `acquire_index_miss`）→ 真正會紅嘅係 `check_honest_miss.py:80-89`（且 `:73-75` 對 **forge＋neoforge 兩棵樹 × 三語** 全掃）＋`HonestMissCheck.java:19-22` |
| C8 有保障 | ✅ 確認 | `AskEngine.java:1011` → `AskJeiHints.java:267-275`（只 `MODIFIED` 插 canonical 行）＋`packai.reply.tool_build_canonical` 唔在 1b 白名單 → 改 `llm_style` 唔會拆 MODIFIED 硬保證；但「STANDARD 要寫肯定句」只存在於 prompt 文字（冇 harness 守）|
| P2 機翻真入玩家文字 | ✅ 確認 | `tool.result`：`掉落表「treasure rib」`／`bygonenether:chests/catacomb/treasure_rib` → 玩家正文「下界墓穴宝箱（**宝藏肋骨**）」 |
| `WorldgenFacts.java:96-103`＋`WorldgenFactsCheck.java:209/212` 反而保護舊措辭 | ✅ 確認 | `:209` en「no indexed worldgen」、`:212` zh「此包未索引到」 |
| `AskJeiHints.looksLikeAbsenceClaim` 可作偵測器 | ⚠️ 名稱對、能力唔對 | 該檔全文搜「索引」＝0 → 唔覆蓋「未索引」 |

## 新增關鍵情報（v3 已吸收）

1. **`tests/check_honest_miss.py:73-75` 同時掃 neoforge 樹**；neoforge 樹係 PAUSED（唔准 mirror）→ B2 改 forge 措辭後，呢個閘會因 neoforge 舊文字而紅 → 必須明確決定（改閘／豁免 neoforge），v3 已列入白名單處理。
2. **`plainify` 有第二出口**：`ReplyLang.jarLoot:847-853`（非 `blocks/` → 「掉落：<raw>」）→ B3 要一齊改。
3. **真機答案非確定性**：同一物品兩次結果唔同 → 只可 spot check（同反方觀察一致）。

## 我方獨立核實（唔照抄）

反方／正方共用嘅關鍵事實（marker 表 scope、`lootTableObtain` raw、「未索引」真源頭、python 閘 baseline 1 紅、generator 會覆寫 lang、`AcquireJarRoutesCheck:104-106` 會紅）我已逐條自己跑／自己讀檔核實，詳見 plan v3 §0 同 `2026-09-22_slice1b-R1-opposing.md` 尾段。
