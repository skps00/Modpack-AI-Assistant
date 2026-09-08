# Pack AI — Drop-in Skill System（shader-pack 式方法包）+ 反推來源鏈 v1

Status: **PLAN ONLY — 等 SK review，未改 production Java／lang／jar。**
Generated: 2026-09-08.
Related: [ask-native-tools.md](ask-native-tools.md)（tool 層：真 function-calling）；[worldgen-lookup.md](worldgen-lookup.md)（`worldgen_lookup` tool）；[ITEM_SOURCE_LOOKUP.md](../ITEM_SOURCE_LOOKUP.md)（pack 檔案追查流程）；Scope Y（`AskTool` plugin API 已上 0.2.0，stored-only）。

**Loaders:** Forge 1.19.2 = 主目標（SK 2026-09-08：mainly focus 1.19.2 first）；NeoForge 1.21.1 鎖步 port。
**Log:** repo-root `code_change_log.md`（先寫日誌再改碼）。
**Version:** 實作波 **不 bump** `mod_version`（本地 smoke；公開上傳另見 `docs/RELEASE.md`）。

---

## 0. 使用者意圖（SK 原話鎖定）

1. 反推深度：玩家問「點攞 X」——X 只喺建築物 loot／禮包／生成條件先有。而家 tool 層單層反查就停（實錘：武刃 09-08 case——答「無法確定取得方式」，但 tooltip/禮包鏈其實有資料）。
2. 「tools API system and skill system」——兩者一體：tool = 單一能力（撳得嘅掣），skill = 教 AI 幾時撳、點組合嘅方法包。
3. 「skill system work like a shader pack system? (just need to put a file to it?)」——**drop-in 即用**：玩家／包作者整個 folder／zip 放入指定目錄，mod 自動偵測，零設定、零 Java。

**要：**
- Skill = **文字方法包**（教 AI 點答某類問題），唔係可執行 code／script runner。
- 放 `config/packai/skills/<名>/skill.md` 或 `<名>.zip`（內含 `skill.md`）即生效。
- **Trigger 跟業界（Anthropic Claude Code／Cursor 2）：metadata（name + description）永遠 inject（幾行），body 由 AI 自決 load**——唔用 keyword substring gate（description 寫「做咩＋幾時用」，AI match 問題）。可多個 skill 疊加。
- **永遠生效嘅紀律唔放 skill**（Vercel eval 教訓：skill 自發 load 有 miss 率 53%）——留喺 lang／AGENTS.md（已有）。Skill 只放「某類問題先用」嘅方法包。
- 內建 skill 包喺 jar（namespace `builtin:`）；drop-in 由玩家加（namespace = folder 名）。**同名：drop-in 優先**（跟 Claude：personal > bundled）。
- 第一個內建 skill：**反推來源鏈**——解決武刃／建築物 loot／禮包鏈問題。

**不要：** Scope X（第三方 tool exec visibility——另開 plan／階段）；skill 帶 Java/code 執行；新 GUI；`/locate` 掃世界；bump／CF／CUA。

---

## 1. Goal / Non-goals

### Goal

1. **Skill 載入框架**：每次 ask 掃 `config/packai/skills/`（+ jar 內建 `assets/packai/skills/`），frontmatter `trigger` keyword match 命中先將該 skill body 注入 system prompt。
2. **第一個內建 skill「反推來源鏈」**：玩家問「X 點攞」→ 強制 chain 式追查：
   - `acquire` 第一層；
   - 來源係 structure／dimension → `worldgen_lookup` 追位置／biome；
   - 來源係另一個 item（禮包／代幣／兌換物）→ 再 `acquire` 追落去；
   - 追到源頭先答；追唔到照實話「本包索引只知到呢層」。
3. **驗證方法**：mirror harness 驅動 AskService（唔使 restart game）＋ 遊戲內武刃 smoke。

### Non-goals

- 唔開第三方 tool 俾 AI call（Scope X）——skill 只組合**現有內建 tools**。
- 唔做 skill 編輯器／管理 UI／下載 marketplace。
- Skill 唔可以執行 code（純文字指引；script 型 skill 留後）。
- 唔處理 skill 之間 conflict（同名 drop-in 覆蓋內建）——v1 兩者一齊 load，log 警告。

---

## 2. Skill.md 格式（草案）

```markdown
---
name: 反推來源鏈
# trigger：keyword 命中先 load（入 prompt）；唔中唔 load
trigger: 點攞, 點獲得, 取得, 獲得, 哪里拿, how to get, obtain, 生成條件, 哪裡拿, 获取
# description：畀 debug log／未來 UI 用
description: 玩家問取得方式時，教 AI chain 追查來源
---

# 方法：追 item 來源鏈

1. 先用 `acquire` tool 查 <item> 嘅直接來源（loot/trade/quest/script）。
2. 如果來源係 **structure / dimension** → 用 `worldgen_lookup` 查佢喺邊啲 biome／點搵。
3. 如果來源係 **另一個 item**（禮包／代幣／兌換物）→ 唔好停，再用 `acquire` 查嗰個 item 點攞。
4. 追到「真源頭」（掉落／交易／任務／合成）先答完整鏈。
5. 追唔到／index 冇 → 照實話「本包索引只知到呢層」，唔准作。
```

- Frontmatter：`name`（必）、`description`（必——「做咩＋幾時用」，係 trigger 核心）、`trigger`（選——額外 keyword hint，**唔做 gate**，只係幫 AI 更容易發現）、`namespace`（選——內建自動 `builtin:`）。
- Body：純 markdown，AI 讀（當 instruction 注入）。
- **Trigger v1：metadata（name+description，每 skill 幾行）永遠注入**（list 形式），AI 自決 load 邊個 body；keyword 只做「預篩／debug log 提示」唔做硬 gate。
- Load 上限：每次 ask 最多 load N 個 skill（初定 3），超過按內建優先；total skill body chars cap（初定 1500）防 prompt 爆炸。

---

## 3. 檔案／路徑

- 內建 skill：`forge/1.19.2/src/main/resources/assets/packai/skills/acquire-chain/skill.md`（＋ neo 對應）。
- Drop-in：`<gameDir>/config/packai/skills/**/skill.md` 或 `<gameDir>/config/packai/skills/**/*.zip`（zip 內含 `skill.md`；zip 唔 unpack——zip 讀入 memory）。
- 掃描時機：每次 Ask 開始（skill 檔細；file list + read 少於 10ms 級）。加 `/packai reload skills` command（選，v1 可後補）。

---

## 4. 實作點（Forge 1.19.2；Neo 鎖步）

- 新 `logic/SkillLoader.java`：掃 drop-in + 內建 → parse frontmatter → store `List<PackSkill>`。
- 新 `logic/PackSkill.java`（record）：namespace／name／description／trigger／body。
- `AskEngine`／`AskToolLoop` shot-0 組 prompt 前：`SkillMatcher.metadataList()` → 全部 skill 嘅 name+description append 入 system instructions（每 skill 幾行）；body 唔預載。
- LLM 要 skill body 時：`skill_read` tool（args: namespace+name）→ 返 body（progressive disclosure；register 做內建 AskTool，同其它 tool 一齊喺 ALLOWLIST）。
- lang：加一條 rule 提及 skills 存在（可選）。
- Debug log：`Pack AI skills loaded=N`（metadata list chars）。
- Mirror harness：`tests/check_skill_loader.py`（喂 fake skills/ + fake question，assert 命中／唔命中／cap）。

---

## 5. 驗收

1. **Unit／mirror**：skill metadata 全部 inject（N skills → N×幾行）；body 唔預載（0 bytes body）；`skill_read`（如做）只 load 指定 body；cap（metadata 超長仍受控）；壞 frontmatter 唔 crash。
2. **遊戲內 smoke（Forge 1.19.2／NFWC instance）**：
   - 「武刃（maodlc:wuren）點攞／取得方式」→ 答出禮包鏈（如果 index 有禮包 entry）或誠實話「索引只知禮包層」；**唔再**淨係答「無法確定」。
   - 建築物 loot item → 答埋 structure 位置（worldgen_lookup 有料時）。
   - 礦物生成條件題 → 有料答、冇料誠實 miss。
3. **Token check**：metadata list 加咗之後 prompt overhead 可控（每 skill ≤ ~80 chars × N；N 有 cap）；body 只喺 AI 要求時先入。

---

## 6. Open questions（SK 拍板；已答項標 ✅）

1. ✅ Zip 支援 v1 要（SK 2026-09-08）。
2. ✅ Trigger 跟業界（Anthropic）：metadata list 永遠注入 + AI 自決 body；keyword 唔做 gate（SK 2026-09-08「check how other people's skill system work」→ 已 research，結論如上）。
3. ✅ 撞名：drop-in 優先 over 內建（跟 Claude personal > bundled；SK 2026-09-08「same as 2」→ 已 research）。
4. ✅ **body 用 `skill_read` tool load**（做法 A；SK 2026-09-08「4a」）——AI 見 metadata list 後，需要就 call `skill_read(namespace,name)` 攞 body（progressive disclosure，同 Anthropic 一致）。
5. ✅ **Scope X 獨立 plan**（SK 2026-09-08「5獨立分開先」）——本 plan 聚焦 skill system；第三方 tool discovery（`search_tools` catalog）另開 plan。

---

## 7. 開工門檻

SK 話「開始／ok」→ 先寫 code_change_log entry → cursor-agent 實作（instructions 檔）→ mirror check → 遊戲 smoke → code review 兩輪 →（可選）bump／CF。
