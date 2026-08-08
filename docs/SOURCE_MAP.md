# 去哪找碼（SOURCE_MAP）

Client-only Pack AI。**兩條獨立樹**，勿合併 Gradle / 勿未核准抽 `common`。

## Runtime 原始碼

| 線 | 路徑 |
| --- | --- |
| Forge 1.19.2 | `forge/1.19.2/src/main/java/com/skps9/packai/` |
| NeoForge 1.21.1 | `neoforge/1.21.1/src/main/java/com/skps9/packai/` |

### 關鍵套件（兩線同構）

| 套件 | 內容 |
| --- | --- |
| `client/knowledge` | **PackKnowledge** 等知識後端 |
| `client/service` | AskService 等客戶端服務 |
| `client/jei` | JEI 查詢／配方卡 |
| `logic` | LlmClient、AskEngine、配方／物品邏輯 |

例：PackKnowledge →  
`…/com/skps9/packai/client/knowledge/PackKnowledge.java`（forge 與 neo 各一份）

## 瀏覽時可略過

| 路徑 | 原因 |
| --- | --- |
| `bridge/` | **LEGACY** 參考／舊測試，非 runtime Pack AI |
| `common/shared/` | 空殼佔位 — **禁止**未核准抽 shared／Architectury |
| `mezz/` | 垃圾／誤放（應 gitignore） |
| `dist/` | 本機 jar 副本（gitignore） |
| `**/build`、`**/run` | 建置／執行產物 |
| `.codegraph` | 工具索引，非專案源碼 |
