# 2026-09-14 — NeoForge（1.21.1）支援「暫停」計畫

> 觸發：SK 2026-09-14 07:2x 決定 —「**將成套 neo 暫時停止支援，同時在 GitHub、CurseForge 說明這件事**」。
> 狀態：**計畫（未執行）**。等 SK 揀範圍（A/B/C）＋ 兩個具體問題答覆先動手；對外發佈同刪檔一律要 SK 明確 go。
> 前置：trace（S0）＋ K2（Tetra 單件模式）已落地雙樹、全部閘綠、未 commit；真機煙測進行中（SK 熄 MC 後做 forge 側）。

## 1. 目的（一句）
減少維護負擔：唔再為 NeoForge 1.21.1 樹做同步＋編譯＋測試＋出 jar，並對外向玩家講清楚「Neo 版暫停支援」。

## 2. 現況事實（2026-09-14 實查）
- 雙樹 lockstep：`forge/1.19.2` ＋ `neoforge/1.21.1`，Java 檔要求字面相同（只有 MC shim 例外），每次改動都鏡射。
- neo 樹**測試編譯自 8 月中壞**（test classpath 冇 gson → `compileTestJava` 全紅；`build/classes/java/test` 停留 08-14）；即 neo 側嘅 Java harness（含 `AskToolLoopCheck` K30–K34）**一直跑唔到**。
- neo 樹 `compileJava`（主 jar）**係綠**；`build/libs/packai-0.2.1.jar`（sha 前綴 `6863cd28`）build 得到。
- 歷史成本：每次雙樹改動都要 mirror；雙樹 shim 差異令對稱 check 要維護 normalize map。

## 3. 範圍選項（SK 揀一個）

### A. 最小（純宣佈，唔改 repo 結構）
1. README／CurseForge 專案描述加「NeoForge 1.21.1 暫停支援（現有版本仍可下載，但唔會再有新功能／修復）」。
2. 兩樹**仍然鏡射**（即工作流程唔變），只係唔出 neo jar／唔做 neo 煙測。
- 好處：最易還原、零風險。壞處：省唔到維護成本（違背原意）。

### B. 中等（**建議**）：凍結 neo 樹 ＋ 對外宣佈
1. **停止鏡射**：新改動只落 forge；`neoforge/1.21.1` 樹凍結（保留在 repo，唔刪，加 `neoforge/README_PAUSED.md` 註明）。
2. 契約更新：`AGENTS.md`／skill 內「dual-tree lockstep 必須鏡射」改成「neo 暫停：改動只落 forge；如需恢復，先做 neo 側差異盤點」。
3. **唔再 build／發佈 neo jar**（保留 build.gradle 檔，唔刪任務；只係唔做）。
4. 對外：
   - GitHub `README.md` 加 Notices 段落；另開一個 **pinned issue**（NeoForge support paused）寫清楚：點解、現有 1.21.1 版本仍可玩、唔會再更新、恢復條件（有人要用／維護成本回落）。
   - CurseForge 專案描述加同一段（用 Core API `update_description`）；1.21.1 檔案**保留**（唔刪）＋在描述註明 unsupported。
5. 現有未 commit 嘅 neo 側改動（trace＋K2）：**照 commit**（已驗 compile 綠，保留歷史一致性）——除非 SK 話唔要。
- 好處：真係省成本、可逆（保留檔案＋git 歷史）。壞處：1.21.1 玩家冇新功能。

### C. 最大（移離 repo）
B ＋ 將 `neoforge/1.21.1` 由 main tree 移走（另開 `legacy/neoforge-1.21.1` 分支／archive tag，或整個目錄刪除）＋ CurseForge 1.21.1 檔案設為 **unsupported／隱藏**。
- 好處：repo 最乾淨。**壞處：不可逆性高**（目錄刪除、CF 檔案狀態變更影響玩家下載面），恢復成本高（要由 git 歷史重建）。
- ⚠️ 依 SK 第一規則：呢個選項要**明確批准**先做，而且做之前要指名 backup（tag／分支名）。

## 4. 風險評估（B 為例）

| 風險 | 最壞情況 | 緩解 |
|---|---|---|
| 停止鏡射後 forge 側改動同 neo 樹分叉 | 之後想恢復 neo 要人手 merge 幾十個 commit | 記錄「暫停起點」commit SHA 落 `neoforge/README_PAUSED.md`；恢復時以該 SHA 為 diff 基準 |
| CF／GitHub 措辭令人以為 mod 停更 | 1.19.2 玩家流失 | 措辭講明**只係 Neo 1.21.1 暫停**，Forge 1.19.2 照更新 |
| CF 描述 API 出錯（cookie 過期） | 描述更新失敗／改壞 | 先 `GET` 讀返現描述存檔（`.bak-<ts>`）再寫；失敗即還原 |
| 誤刪 neo 樹 | 需要由 git 重建 | B 唔刪檔；C 一定先 tag／分支 |

## 5. 還原方案（B）
- 所有對外文字：改之前先存原文（GitHub README 由 git；CF 描述由 API 讀出存檔）。
- 代碼：neo 樹原封不動喺 repo → 恢復＝改返 `AGENTS.md` 規則＋重新鏡射。
- 完全回退：`git revert <本計畫 commit>`（docs＋README 改動）。

## 6. 執行順序（SK 批 B 後）
1. 煙測通過 → commit trace＋K2（forge＋neo 兩邊，保持歷史一致）。
2. 加 `neoforge/README_PAUSED.md`（含暫停起點 SHA）＋改 `AGENTS.md`／skill 契約。
3. 出 `docs/plans/` 本計畫（已入 git）＋ commit（`docs(neoforge): pause 1.21.1 support`）。
4. GitHub：README 段落 ＋ pinned issue（**要 SK 過目文字先出**）。
5. CurseForge：讀現描述 → 存檔 → 加同一段 → 寫返（**要 SK 過目文字先出**）。
6. 回報：逐個表面（repo／GitHub／CF）貼證據（commit SHA、issue URL、API 回應）。

## 7. 等 SK 決定
1. **範圍**：A 最小／**B 中等（建議）**／C 最大（移離 repo）
2. **GitHub／CurseForge 文字**：我起草 → 你過目先出？定直接出？
3. **版本號**：今次煙測嘅 jar 仲係 `0.2.1`；發布時要唔要 bump（例如 `0.2.2`）並改 README／CURSEFORGE_DESCRIPTION？
