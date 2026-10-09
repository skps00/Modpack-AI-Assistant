# Pack AI — 按鍵查詢（keybind_lookup）

- **Status**：DRAFT — 等 adversarial review（≥8:2）＋ SK go，未改任何 production 碼
- Generated：2026-10-10（覆蓋缺口審計 §8 次序第 1 項）
- 父文件：`docs/research/2026-10-10-coverage-gap-audit.md`、`docs/plans/2026-10-05-player-question-set.md` K 類
- 需求證據：題型研究（ViewBoard mod 存在＝按鍵衝突係真痛點；YT「Top 10 Clever HotKeys」1.86M；bili「按键设置教程」338k）
- Loaders：**Forge 1.19.2 先做**（NeoForge 1.21.1 暫停，issue #20）→ 恢復時鏡像

## 1. Goal / Non-goals

### Goal
玩家問「**要撳咩掣先用得到 X**」時，答到**真掣**；並且答「**我個掣係唔係撞咗**」同「**呢個功能未綁掣**」。

### Non-goals（明確唔做）
| 唔做 | 理由 |
|---|---|
| 改玩家按鍵／自動幫佢綁 | 唔屬問答範圍，有風險 |
| 猜未綁功能嘅「預設掣」 | 遊戲內根本冇呢個資訊（預設值喺 code，未綁＝UNKNOWN）→ 一定係 `miss > fiction` |
| NeoForge 樹 | 1.21.1 線暫停 |
| 記住玩家自訂鍵名（例如滑鼠側鍵中文名） | 靠 client 現成顯示 |

## 2. 資料來源（全部 runtime、client 側、唔使開新檔）

| 來源 | 攞咩 | 為何可靠 |
|---|---|---|
| `net.minecraft.client.KeyMapping`（`ALL`／`Options.keyMappings`） | **目前綁定**（live） | 玩家改鍵即刻反映；唔似 `options.txt` 只喺退出遊戲先寫入（實測 ATM8 個檔係 2023 年舊檔、0 條） |
| mod lang `key.<mod>.<name>`（經 Component.translatable，玩家語言） | 功能**人話名** | 用包自己嘅語言（en_us／zh_cn／zh_tw），唔准我們硬編碼英文名 |
| 同一 key 出現喺多個 KeyMapping | **衝突**清單 | 直接由 registry 推 |
| `InputConstants.UNKNOWN` | **未綁**清單 | 同上 |

**離線 oracle（已存在）**：`tools/extract_keybinds.py`
實測基線：ATM8 258 條登記（options.txt 舊檔、0 綁定）、StarTech 148 條／60 已綁／**12 組衝突**、NFWC 201 條／72 已綁／**11 組衝突**。→ 用嚟驗證工具輸出（兩邊數量要對得上，唔一致就要查）。

## 3. 實作範圍

| # | 檔 | 做咩 |
|---|---|---|
| 1 | `forge/1.19.2/.../logic/KeybindAskTool.java`（新） | 實作 `api.AskTool`：`name="keybind_lookup"`；args：`query?`（功能名／關鍵詞）／`key?`（例如 `key.keyboard.m`）／`mod?`；回傳：命中清單＋目前掣＋衝突標記；搵唔到＝誠實 miss |
| 2 | `logic/AskEngine.java` | `AskToolLoop.INSTANCE.register(new KeybindAskTool());`（**14 → 15 個**） |
| 3 | `logic/PackIndex.java` | 加 `isKeybindQuestion(String)`（同 `:1156 isMachineQuestion` 同風格）＋ **deterministic FACT 注入**：唔靠弱模型揀 tool |
| 4 | `assets/packai/lang/{en_us,zh_cn,zh_tw}.json` | 新字串 `packai.keybind.*`（三語同步） |
| 5 | `tests/check_keybind_lookup.py`（新） | 離線 oracle 對照（數量／衝突組數）＋ trace 斷言（FACT 有冇出） |

**唔碰**：現有 14 個 tool 行為、`options.txt`、玩家設定檔。

## 4. 驗收標準（開工前定，逐項要證據）

| # | 條件 | 證據要求 |
|---|---|---|
| A1 | 問「點開 mod 清單」→ 答到**真掣** | ask trace 有 FACT 行＋答案含正確 key |
| A2 | 問「我個掣有冇撞」→ 列出**衝突**（同 oracle 數量一致） | trace＋`check_keybind_lookup.py` 對照 |
| A3 | 問一個**未綁**功能 → 講「未綁，要去設定綁」 | trace |
| A4 | 問**唔存在**功能 → 答「我唔確定」 | trace（**唔准作**） |
| A5 | **跨包**：ATM8 沙盒 ＋ StarTech 各跑一次（兩包 keybind 集唔同） | 兩個 trace 檔名 |
| A6 | 冇 secrets／API key 流入 prompt | 自掃（`sk-`／`ghp_`／token） |
| A7 | `compileJava` OK；`tests/check_*.py` 全部綠（除已知 baseline 1 紅） | 命令輸出 |

## 5. 風險 / Rollback

| 風險 | 對策 |
|---|---|
| KeyMapping 未載入就讀（時序） | **只喺 query 時讀**（唔喺 index build 讀） |
| 玩家語言無該功能字串 | fallback：顯示 raw `key.<mod>.<name>`＋mod 名，**唔准**自己譯 |
| 誤報衝突（同一 key 但唔同時用） | 只列事實（同 key 多個功能），唔判斷嚴重性 |
| 影響現有 tool 選擇準確率（14→15） | 驗收 A1–A4 要覆核舊題（P0 §5⑥ 做法） |

**Rollback**：`git revert` 單一 commit；jar 只喺沙盒換（換前備份去 `%TEMP%`）；實機唔部署。

## 6. 工作量 / 依賴

- 工作量：**1 個工作段**（1 新檔 ～150 行、1 行註冊、1 個 intent 方法＋FACT、3 語 lang、1 測試）
- 依賴：無（唔使新 lib、唔使改 config 預設）
- 實作方式：**一律經 cursor-agent**（packai 規矩）

## 7. Review 記錄

| 輪 | 判決 | 備註 |
|---|---|---|
| R1 | 待 | adversarial（本檔） |

## 8. 已自行核實嘅 API 面（唔靠印象；mapping 實證）

**方法**：`javap` 打真 build 用嘅 jar（`forge-1.19.2-43.4.0-binpatched.jar`）＋查 build 真正用嘅 SRG→official mapping
（`.gradle/caches/forge_gradle/minecraft_user_repo/.../srg_to_official_1.19.2.tsrg`）。

| 用嘅 member | SRG 名 | 型別／作用 | 結論 |
|---|---|---|---|
| `Minecraft.getInstance().options.keyMappings` | `f_92059_` | `KeyMapping[]`（**public**） | ✅ 用呢個做「全部功能」來源 |
| `KeyMapping#getName()` | `m_90860_` | `String`＝**翻譯 key**（例 `key.jade.toggle`） | ✅ 配 `Component.translatable(...)` 出玩家語言人話名 |
| `KeyMapping#getTranslatedKeyMessage()` | `m_90863_` | `Component`＝**目前按鍵顯示**（例 `M`） | ✅ 用嚟答「撳咩掣」 |
| `KeyMapping#isUnbound()` | `m_90862_` | `boolean` | ✅ 直接判「未綁」，唔使自己比對 `UNKNOWN` |
| ~~`KeyMapping.ALL`~~ | — | — | ❌ **1.19.2 冇呢個 field**（mapping 檔查唔到）→ **唔准用**（常見陷阱） |

**其他已核實**：
- `forge/1.19.2/build.gradle:16` → `mappings channel: 'official'`（Mojang 名；唔使搞 SRG，mixin 除外）。
- `client/ClientSetup.java:21,95` 已經有 `import net.minecraft.client.KeyMapping` ＋ `RegisterKeyMappingsEvent` → client 側取用冇問題。
- 測試慣例：`tests/` 有 130 個檔，型式＝純 Python mirror ＋ fixture（例 `tests/check_worldgen_lookup.py`）→ 新測試照跟。

（本節為 review 進行中自行補上嘅核實附錄，唔改變 §1–§7 內容。）
