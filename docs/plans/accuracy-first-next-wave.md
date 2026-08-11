# Pack AI — Accuracy-first next wave（計畫 only）

Status: **PLAN LOCKED — implement sequential W1→W5; no code until this doc accepted.**  
Generated: 2026-08-11.  
Upstream: [four-issue-backlog.md](four-issue-backlog.md) (checklist complete thru #5b); [full-item-index.md](full-item-index.md) (item search disk index).  
**Loaders:** Forge 1.19.2 + NeoForge 1.21.1（鎖步同一語意；Forge/NFWC = 主驗收）。  
**Log:** repo-root `code_change_log.md`（先寫日誌再改碼）。  
**Version:** 本波 **不 bump** `mod_version`（本地 jar→dist→NFWC 煙測即可；公開上傳另見 `docs/RELEASE.md`）。

---

## 1. Goal / Non-goals

### Goal

Accuracy-first 下一波：標記可靠、未知誠實、Ask 搜尋可快取、玩家解鎖可核對；外觀 remake 排最後。

優先序（鎖定）：

1. `{{item:}}` / `[[recipe:]]` marker 可靠（FACT 接地 re-attach／repair）
2. Gate / Loot **honest miss** UX
3. Ask item search **disk index**（跟 `full-item-index.md`）
4. Runtime player unlock / advancement checklist（#1C 後 reopen）
5. Remake Pack AI GUI（Ask + settings shells）

### Non-goals（本波明確不做／defer）

| Item | Reason |
|------|--------|
| JEI Create / Hexerei **slot drift** | 已在 four-issue Known issues DEFER；remake 期間除非 trivial 否則仍 defer |
| KubeJS 7 **NativeEvents** / RecipeViewer-as-1.19-truth | backlog L 列 defer |
| Pack-specific hardcodes（mrqx / friend / organ id 表） | accuracy 禁令 |
| Invent ids / invent loot drops / invent stage lists | miss > fiction |
| 新 catalog screen **B**（獨立物品百科 UI） | `full-item-index`：v1 = 加速 Ask search **A** |
| Version bump / CurseForge / Modrinth upload | 本波 N/A |
| 開 PR（除非之後另叫） | plan-only / 實作時再談 |

### Mandatory QA gate（每 WP + 整波結束）

**不得跳過。** 每 work package 宣告 Done 前必須：

1. **2× test run** — 相關 `tests/check_*.py`（及 Java `-ea` fixture 若有）連續跑兩次皆綠  
2. **Logic review** — 對照本 WP「problem / approach / acceptance」走一遍資料流（FACT 進 → 出）  
3. **2× code review** — 同一 diff 自己（或 reviewer）過兩次 checklist：  
   - 無 invent id / pack hardcode  
   - Forge↔Neo 對稱（語意；允許 loader API 差異）  
   - miss 路徑誠實（unknown / empty，非假敘事）  
   - 無無關 refactor  
4. **Playable Forge 變更** → jar → `dist`（versioned + `packai-1.19.2-forge.jar`）→ NFWC mods（單一 packai jar）→ 本 WP smoke（`;` 開助手）；CUA 可選但建議留 `dist/cua_wN_*.png`  
5. 寫入 `code_change_log.md`

整波 DoD 再跑一次「總 QA gate」（見 §4）。

---

## 2. Ordered work packages

### WP0 — Spike / repro inventory（短；不改產品行為）

| | |
|--|--|
| **Problem** | Marker 掉圖、假敘事、搜尋卡頓等症狀需可重複 repro，避免邊寫邊猜 |
| **Approach** | 列 3–5 個 NFWC Ask 句（含 gateway pearl NBT、無 index loot、unknown gate ritual、重搜尋字）；記「現況：有/無 `{{item:}}`、有/無假敘事」；**不修碼** |
| **Files** | 僅筆記可寫入本檔「Repro appendix」附錄或 changelog 備註；不碰 Java |
| **Parity** | N/A |
| **Acceptance** | 有書面 repro 清單；與 WP1–2 對得上 |
| **Test** | 手動 Ask 各 1 次記錄；無單元測試 |
| **QA gate** | 2× 同一 Ask 句確認症狀穩定（非偶發）→ logic N/A → 2× 對照清單與後續 WP 對齊 |

---

### WP1 — Marker reliability（`{{item:}}` / `[[recipe:]]`）

| | |
|--|--|
| **Problem** | FACT／acquire 已含 embed，LLM 常剝／改寫標記 → 聊天無圖或殘缺；不可靠。Prompt-only 已強化仍不足（弱模型服從風險）。需 **FACT-grounded re-attach／repair**：只還原本輪 FACT／card／suggested ids 已出現的標記，**禁止發明 id** |
| **Approach（ponytail）** | 1) 在 `AskResult`／post-LLM 路徑（`AskReplyScrub` 之後、`RecipeEmbed` 之前）加 **薄** repair：從本輪已餵進 prompt 的 FACT 行／`RecipeCard`／`suggestedItemIds` 收集合法 marker 集合；若答案缺對應 `{{item:…}}`／`[[recipe:]]`／`{{RECIPE}}`，按既有 FACT 順序 **re-insert 原字串**（含 NBT 形態 `{{item:ns:id{…}}}`）。2) 損壞但可辨識（空白、半截）→ 僅當 unique 對應到 FACT 集合才 repair。3) **不**從 display name 猜 registry；**不**硬碼 pack id。4) Prompt 保留（雙保險），但真相來源 = FACT 集合 |
| **Files likely** | Forge+Neo：`AskResult.java`、`AskReplyScrub.java`（或新 `AskMarkerRepair.java` 薄類）、`RecipeEmbed.java`、`AskService.java`（呼叫點）、`Plainify.java`／`ItemResolver.java`（僅若 parser 邊界需對齊 NBT）；`tests/check_recipe_embed.py`（擴充 repair fixtures）；可選 `tests/check_ask_marker_repair.py` |
| **Parity** | 邏輯放 `logic/` 雙樹鏡像；UI 解析共用 `RecipeEmbed` |
| **Acceptance** | ① Fixture：FACT 有 marker、LLM stub 剝掉 → repair 後標記原樣回來 ② FACT 無該 id → repair **不**插入 ③ NBT pearl 標記 round-trip ④ 既有 `check_recipe_embed` / Gateway humanize 仍綠 ⑤ NFWC：Ask 已知 gateway／含 FACT embed 的取得 → 正文有圖，非僅 footer |
| **Test plan** | Unit：`python tests/check_recipe_embed.py`（+ 新 check）**×2**；NFWC Ask ×2 句（有 embed / 無 embed 對照）；可選 CUA `dist/cua_w1_markers.png` |
| **QA gate** | 見 §1 Mandatory — 特別查：無 hardcode；repair 輸入集合 ⊆ FACT |

---

### WP2 — Gate / Loot honest miss UX

| | |
|--|--|
| **Problem** | Index／heuristic miss 時，LLM 或文案可能編故事（假 stage、假掉落、假 organ 路徑）。需 **明確 unknown**，禁止假敘事 |
| **Approach（ponytail）** | 1) 盤點 miss 出口：`RecipeUnlockGates.Kind.UNKNOWN` + `ReplyLang.unknownAdvancementGate`；`LootForwardIndex` 空表／無邊；Ask PURPOSE／acquire 無邊。2) 在 FACT／REQUIREMENTS／prompt pin 對 miss **注入固定誠實句**（三語 lang），並加 fact_check 規則：「不得編造未列於 FACT 的 loot／stage／advancement」。3) 若已有 unknown gate 字串但 Ask 仍編故事 → 收緊 prompt +（可選）post-scrub 僅刪明顯「假列表」**太危險則不做 scrub**，寧願強化 FACT pin。4) 不擴 index 範圍（那是已完成 #5b）；本 WP = **UX／誠實** |
| **Files likely** | Forge+Neo：`ReplyLang.java`、lang `en_us`/`zh_tw`/`zh_cn` ×2、`AskService` prompt 組裝、`FormatRequirements`／gate footnote、`LootForwardIndex` 空結果呼叫點（若需一行 miss fact）；`tests/check_recipe_unlock_gates.py`、`check_loot_forward_index.py`、`check_reply_prompt_keys.py` |
| **Parity** | lang + logic 雙樹 |
| **Acceptance** | ① Fixture：UNKNOWN gate → 輸出含 unknown 文案，無假 advancement id 列表 ② Loot miss → 「未索引／未知」類字樣，無編造 drops ③ Organ-only／無 index 路徑仍無假 worm/tumor 敘事（回歸 #5） ④ NFWC：Ask 一題已知 miss + 一題已知 hit，對照 |
| **Test plan** | 上列 check **×2**；NFWC 兩 Ask；可選 `dist/cua_w2_honest_miss.png` |
| **QA gate** | 特別查：miss > invent；無新 pack parser |

**Depends on:** WP1 建議先完成（避免修文案時 marker 仍亂）；可極小重疊但 **merge 順序 WP1→WP2**。

---

### WP3 — Ask item search disk index

| | |
|--|--|
| **Problem** | `ItemSearch` 每鍵可能全走 JEI+registry；大包卡。`full-item-index.md` 已鎖設計，先前 deferred |
| **Approach（ponytail）** | 嚴格跟 `docs/plans/full-item-index.md`：① NFWC **timing spike**（現況 live search vs 建 prefix 一次）寫數字進 changelog ② Async build after client/world ready；disk `config/packai/item-index/` key = `mc + loader + lang + modListFingerprint` ③ 同 fingerprint 載入跳過 rebuild；mod 增刪／lang／resource reload → rebuild ④ Ask UI（既有 A）改查 index；失敗 fallback live scan ⑤ 合併 JEI stacks（NBT 兄弟）同今日來源；不 scrape tooltip 當 craft 真相 ⑥ **不做 B catalog screen** |
| **Files likely** | Forge+Neo：`ItemSearch.java`、新 `ItemIndex`／`ItemIndexCache`（薄）、client join／reload hook（既有 Pack AI client init 附近）、`AiAssistantScreen`（僅接線，不大改 UI）；`tests/check_item_search.py` + 新 fingerprint／cache hit-miss fixtures |
| **Parity** | 快取格式跨 loader 可共用；fingerprint 含 loader id |
| **Acceptance** | 同 `full-item-index.md` Done when v1：首 join 建一次；次 join skip；mod 變動 rebuild；Ask 用 index；NFWC 不凍；tests fingerprint hit/miss |
| **Test plan** | spike 數字；`check_item_search` **×2**；NFWC：冷啟動建 index → 搜尋 → 重進世界確認 skip（log）；可選 ATM10 Neo ceiling **僅當** NFWC OK 仍疑 Neo |
| **QA gate** | 特別查：主執行緒不凍；無新 UI 畫面 |

**Depends on:** 無硬依賴 WP1–2；但鎖定順序 **WP1→WP2→WP3**（easy accuracy → larger）。實作勿與 WP1 同 commit 大碰撞 `AskService`。

**Update:** 實作開始時把 `full-item-index.md` Status 改為 **in progress (accuracy wave WP3)**。

---

### WP4 — Runtime player unlock / advancement checklist

| | |
|--|--|
| **Problem** | #1C 只做 **腳本 heuristic → Gate 列表**（含 UNKNOWN）。Backlog NOT in scope：「Runtime `isAdvancementDone` checklist for player」— 玩家當下是否已完成該 advancement **未**接上 Ask／REQUIREMENTS |
| **Approach（ponytail）** | 1) Reuse `RecipeUnlockGates` 已有 client/server advancement 列舉（`loadAdvancements`）— 擴成 **讀玩家 progress**（client `PlayerAdvancements`／connection progress API；Forge vs Neo 分開反射／軟依賴）。2) 對 `Gate.Kind.ADVANCEMENT` 且 id 為 literal：標 **done / not done / unknown(無法讀)**。3) UNKNOWN sentinel（無 literal id）→ 仍只顯示 unknown gate，**不**假裝 checklist。4) UI：REQUIREMENTS／footnote 一行狀態即可；不做新成就瀏覽器。5) 無 GameStages 玩家 stage API 除非已有軟讀且便宜 — YAGNI：先 advancement literals |
| **Files likely** | Forge+Neo：`RecipeUnlockGates.java`、`FormatRequirements`／`ReplyLang`、`AskService` fact pin、lang；`tests/check_recipe_unlock_gates.py`（mock progress）；可選薄 `PlayerUnlockStatus` |
| **Parity** | 進度 API 不同 → 雙樹適配；標籤字串共用 |
| **Acceptance** | ① Mock：done／not done／unreadable 三態 ② UNKNOWN gate 不出現假勾選 ③ NFWC：有 literal advancement 的 recipe Ask → 看得到完成與否（或明確「無法讀取」） ④ 缺 mod／專服限制 → 不崩潰 |
| **Test plan** | unlock gates check **×2**；NFWC 兩狀態（若可切成就）；`dist/cua_w4_unlock.png` 可選 |
| **QA gate** | 特別查：不引入 mrqx 表；literal-only checklist |

**Depends on:** #1C **已完成**（repo checklist）；本波 **WP2 建議先**（honest unknown 文案穩定後再加 runtime 狀態，避免混訊）。硬依賴：WP2 的 unknown 字串契約。

---

### WP5 — Remake Pack AI GUI（Ask + settings shells）

| | |
|--|--|
| **Problem** | 外觀舊；準確度項目完成後再做。範圍：Ask 主殼 + settings 殼 |
| **Approach（ponytail）** | 視覺重做：層次、間距、對比、字級；**不改** Ask 語意／搜尋契約／marker 語法。保留既有 widget 與 tooltip key（#3）。JEI card **slot drift** = 分開 optional：僅當 remake 時 1:1 blit 順便極小修且不回歸白卡才碰；否則 **仍 defer**。 |
| **Files likely** | Forge+Neo：`AiAssistantScreen`、`PackAiSettingsScreen`、相關 nested settings screens、layout helpers、可選 textures；lang 僅字串微調 |
| **Parity** | 雙樹 UI 對齊觀感 |
| **Acceptance** | ① Ask 開關／送出／搜尋／卡片／marker 圖示行為回歸綠 ② Settings 各頁可進可退 ③ 無新準確度回歸（抽測 WP1–4 Ask 句） ④ slot drift 未修則 changelog 註明仍 defer |
| **Test plan** | 既有 UI 相關 check（tooltips／chat spacing 等）**×2**；NFWC+CUA 主路徑 `dist/cua_w5_gui.png` |
| **QA gate** | 特別查：無行為偷偷改；accuracy 優先回歸 |

**Depends on:** WP1–4 Done（或明確簽署「準確度暫緩、先 GUI」— **預設禁止**）。

---

### Deferred this wave（容量剩餘才碰）

| ID | Item | Note |
|----|------|------|
| D-JEI | Create / Hexerei slot drift | Known issue；ignore unless trivial in WP5 |
| D-L | KubeJS7 NativeEvents | 不作 1.19 真相 |

---

## 3. Dependencies

```
WP0 (repro)
  └─► WP1 (markers) ──► WP2 (honest miss) ──► WP3 (item index)
                              │                    │
                              └─► WP4 (runtime unlock checklist)
                                       │
                                       └─► WP5 (GUI remake)
```

- **WP3** 不依賴 WP4；鎖定序仍 WP3 在 WP4 前（index = 較大 accuracy，checklist = reopen 功能）。  
- **WP5** 閘門：WP1–4 全綠。  
- 雙樹：每 WP Forge 先（NFWC 驗）→ Neo `compileJava` + mirror。

---

## 4. Definition of Done（整波）

- [x] WP1 acceptance + QA gate（單元／compile／jar→NFWC；**NFWC Ask 煙測 user waive / NO CUA**）  
- [x] WP2 acceptance + QA gate（單元×2；play smoke waived / NO CUA）  
- [x] WP3–4 各項 **code** acceptance；**Mandatory QA gate** 單元 ×2 + compile **done**（2026-08-11 23:12）；NFWC／CUA仍 deferred（NO CUA）  
- [x] WP5 **code** 完成（GUI remake）；單元／NFWC／CUA **deferred**（ZERO Shell／no CUA）  
- [x] Deferred 表未誤開（NativeEvents / slot drift 仍 defer）  
- [x] 無 `mod_version` bump（除非另開 release 任務）  
- [x] `full-item-index.md` 狀態與 WP3 結果同步  
- [x] **總 QA gate：** 單元 check 清單（×2）+ compile Forge+Neo OK（2026-08-11 23:12）；NFWC／CUA／play smoke 仍 deferred（user NO CUA）

---

## 5. Logging

- 路徑：`C:\Users\skps9\Documents\Code_Project\super_minecraft_AI_player\code_change_log.md`  
- 每 WP：先（或同步）寫日誌再改碼；格式既有 `## [YYYY-MM-DD HH:MM:SS]`  
- Bug：先查 changelog 再修  

---

## 6. Risk / rollback

| Risk | Mitigation | Rollback |
|------|------------|----------|
| Marker repair 插錯 id | 集合 ⊆ FACT；fixture 負例 | 關 repair flag 或 revert 單類 `AskMarkerRepair` |
| Prompt+repair 仍被弱模型弄亂 | 接受指令服從上限；changelog 註明 | 保留 FACT 原文卡／footer 圖 |
| Item index OOM／凍主線 | async + cap + spike 數字門檻 | fallback live `ItemSearch`；刪 cache 目錄 |
| Fingerprint 誤重建／不重建 | fixtures hit/miss；log 一行 reason | 手動刪 `config/packai/item-index/` |
| Runtime adv 讀取專服空 | unknown／無法讀取 文案；不崩潰 | 隱藏 checklist 行 |
| GUI remake 迴歸 Ask | WP5 最後；行為 freeze list | git revert UI commits only |
| Neo/Forge API 漂移 | 每 WP Neo compile；共用 logic 測試 | 單樹 hotfix + changelog；下 joint 對齊 |

---

## Repro appendix（WP0 填）

| # | Ask / action | Expect after wave | Current (fill in WP0) |
|---|--------------|-------------------|------------------------|
| R1 | 含 gateway／pearl NBT 的取得 | 正文 `{{item:…{…}}}` 圖示 | **baseline (pre-WP1):** FACT 有 pearl embed；弱模型常剝 → 正文無圖、footer 推薦偶有圖（changelog 2026-08-11 prompt #20）。WP1 repair 應還原 FACT 原字串。 |
| R2 | LLM 易剝 marker 的取得句 | repair 後仍有圖 | **baseline:** prompt-only 不足；需 post-LLM FACT re-attach（本波 WP1）。 |
| R3 | 無 index／UNKNOWN gate 物品 | 明確未知；無假列表 | **WP2:** FACT pin `acquire_index_miss` + RULE19；單元覆蓋；NFWC Ask **user waive / NO CUA** |
| R4 | 側欄快速連打搜尋 | 不凍；結果合理 | **WP3 code:** index+fallback；NFWC 連打 **deferred silent / NO CUA** |
| R5 | 有 literal advancement 的配方 | checklist done/not／unreadable | **WP4 code:** literal id → 後綴；UNKNOWN 無勾選；單元／NFWC **deferred**（Shell STOP） |

### WP1 checklist

- [x] `AskMarkerRepair` Forge+Neo（FACT collect + repair；無 invent id）
- [x] `AskEngine` post-LLM hook（AskReplyScrub／Sources 後、`AskResult` 前）
- [x] fixtures：`tests/check_ask_marker_repair.py` + `AskMarkerRepairCheck` ×2 綠
- [x] `check_recipe_embed.py` ×2 綠（回歸）
- [x] Forge jar→dist→NFWC mods（`0.1.5`；SHA256 見 changelog）
- [x] NFWC Ask ×2 煙測 — **user waive（NO CUA）**；以單元×2 驗收
- [x] Logic review + code review ×2（見 changelog／WP1 回報）

### WP2 checklist

- [x] `HonestMiss` Forge+Neo（acquire 空＋取得向＋無 JEI → pin 固定未索引句）
- [x] `AskEngine` online FACT + offline 路徑接線
- [x] lang `acquire_index_miss` + fact_check #19 收緊（三語×雙樹）
- [x] UNKNOWN gate 既有 `unknown_advancement_gate` 保留（無 invent list）
- [x] fixtures：`check_honest_miss` / `check_recipe_unlock_gates` / `check_loot_forward_index` / `check_reply_prompt_keys` + `HonestMissCheck` ×2
- [x] jar→dist→NFWC（可選；無 CUA）
- [x] Logic review + code review ×2
- [x] NFWC Ask 煙測 — **user waive（NO CUA）**

### WP4 checklist

- [x] `PlayerUnlockStatus` Forge+Neo（literal ADVANCEMENT → DONE／NOT_DONE／UNREADABLE；test override）
- [x] `RecipeUnlockGates`：#1B index 存 advancement **id**；`addGate` 存 raw；`formatGateLabel` 解析 title＋checklist
- [x] UNKNOWN／STAGE／title-only → **無**假勾選
- [x] lang `unlock_done`／`unlock_not_done`／`unlock_unreadable` 三語×雙樹 + ReplyLang
- [x] fixtures：`tests/check_recipe_unlock_gates.py` WP4 段 + `PlayerUnlockStatusCheck` + `check_reply_prompt_keys` keys（×2 + PlayerUnlockStatusCheck -ea ×2 OK 2026-08-11 23:12）
- [ ] 單元×2 — **deferred**（user HARD STOP Shell；等「tests OK」）
- [ ] jar→dist→NFWC — **skipped**（ZERO Shell／no CUA）
- [ ] NFWC Ask 煙測 — **deferred**（NO CUA）
- [x] Logic review + code review ×2（見 changelog）

### WP3 checklist

- [x] `ItemIndexCache` Forge+Neo（fingerprint / disk JSON / hit-miss）
- [x] `ItemIndex` async build+load；spam skip；MAX_ENTRIES cap
- [x] `ItemSearch` → index first, live fallback
- [x] `ClientSetup` LoggingIn ensure / LoggingOut invalidate；Ask `init` kick
- [x] fixtures：`tests/check_item_index.py` + `ItemIndexCacheCheck`；`check_item_search` updated（×2 OK 2026-08-11 23:12 Mandatory QA）
- [ ] NFWC timing spike 數字 — **deferred silent mode**（合成 20k 在 check_item_index；未跑）
- [ ] jar→dist→NFWC — **skipped silent**（no focus/deploy）
- [ ] NFWC Ask 搜尋煙測 — **deferred silent / NO CUA**
- [x] Logic review + code review ×2（見 changelog 21:58）
- [ ] 單元×2 — **deferred**（user HARD STOP Shell；等「tests OK」）

### WP5 checklist

- [x] `GuiShell` Forge+Neo（panel／accent／title／nestedShell；視覺 only）
- [x] `AiAssistantScreen`：層次 panel、間距、側欄 hairline、搜尋 popover；**不改** Ask／搜尋／marker
- [x] `PackAiSettingsScreen`：body shell、active-tab 底線、title hierarchy；tooltip keys 不變
- [x] Nested：WebSearch／ModelPicker／RecipeCategory／InvPick 同 chrome
- [x] JEI Create/Hexerei **slot drift 仍 defer**（無 blit 改動）
- [x] Logic review + code review ×2（見 changelog）
- [ ] 單元×2（tooltips／chat spacing 等）— **deferred**（ZERO Shell）
- [ ] jar→dist→NFWC／CUA `dist/cua_w5_gui.png` — **deferred**（NO CUA／ZERO Shell）

---

## Mandatory QA results（2026-08-11 23:12｜NO CUA）

| Suite | Run1 | Run2 |
|-------|------|------|
| `check_ask_marker_repair` | PASS | PASS |
| `check_recipe_embed` | PASS | PASS |
| `AskMarkerRepairCheck -ea` forge | PASS | PASS |
| `AskMarkerRepairCheck -ea` neo | PASS | PASS |
| `check_honest_miss` | PASS | PASS |
| `check_reply_prompt_keys` | PASS | PASS |
| `check_recipe_unlock_gates` | PASS | PASS |
| `check_loot_forward_index` | PASS | PASS |
| `HonestMissCheck -ea` forge | PASS | PASS |
| `HonestMissCheck -ea` neo | PASS | PASS |
| `check_item_index` | PASS | PASS |
| `check_item_search` | PASS | PASS |
| `PlayerUnlockStatusCheck -ea` forge | PASS | PASS |
| `PlayerUnlockStatusCheck -ea` neo | PASS | PASS |
| `check_ask_chat_spacing` | PASS | PASS |
| `check_recipe_cards_mode` | PASS* | PASS* |
| `check_recipe_card_layout` | PASS | PASS |
| `check_scroll_material_card` | PASS | PASS |
| Forge `compileJava`+`compileTestJava` | PASS | — |
| Neo `compileJava`+`compileTestJava` | PASS | — |

\* first fail = Neo lang drift；synced from Forge then ×2 PASS.

**Fixes:** Neo recipe_cards lang keys；`RecipeUnlockGates.formatGateLabel` skip `resolveAdvancementTitle` when `progressOverride` set.

**Still deferred:** NFWC／Prism／CUA／jar deploy／in-game GUI smoke.

**Wave QA status:** unit Mandatory gate **PASS**；play/CUA gate still open by user waive.

---

## GSTACK / process

| Review | Status |
|--------|--------|
| 本計畫（accuracy-first） | 文件鎖定；實作前可再 `/plan-eng-review` 若要硬鎖 API 名 |
| CEO / Design | WP5 前建議短 design 過目（非 blocker for WP1–4） |
