# D&D 活動報名平台｜專案開發規範

本文件適用於本專案後續新增、修改及維護程式碼的工作。執行任務時，請同時遵守使用者當次指定的工作範圍；本文件中的檢查與調整流程，不代表可以主動進行全專案重構或部署。

## 開發目的與學習背景

這是 Java / Spring Boot 個人課程專案，開發者目前仍處於學習階段，已具備基本 Java、Spring Boot、JPA 概念，並大致理解 Controller、Service、Repository、Entity 之間的關係。

開發者仍在練習獨立組合方法呼叫、條件判斷、例外處理與回傳值，尤其需要熟悉 Service 與 GlobalExceptionHandler。請以容易閱讀、方便逐步理解的傳統 Java 寫法為優先，不刻意追求精簡或減少程式碼行數。

目標是在維持專案正常運作的同時，協助開發者逐漸具備自行閱讀、維護及撰寫 Java / Spring Boot 程式碼的能力。

## Java 程式碼風格

1. 優先使用傳統 `if / else` 條件判斷，明確呈現每個檢查步驟。
2. 優先使用一般 `for`、`for-each` 迴圈，不為了縮短程式碼而使用 Stream API。
3. 盡量避免不必要的 Lambda Expression（`->`）、Method Reference（`::`）及其他影響理解的簡寫。
4. `Optional` 優先使用 `isPresent()`、`isEmpty()`、`get()` 等明確方式處理；呼叫 `get()` 前，必須先確認資料存在。
5. 將查詢、驗證、資料處理、儲存與回傳分成清楚的步驟，避免為精簡而串接過多方法呼叫。
6. 使用有意義的變數名稱，避免過度簡化命名。
7. 可讀性與學習便利性優先於程式碼行數，但不得犧牲正確性與安全性。
8. 繼續使用專案既有的 Spring Boot、JPA、Lombok 等框架與註解。Lombok 與 Lambda 是不同技術，不因避免 Lambda 而移除 Lombok。
9. 除非使用者明確要求，不主動將原本的 `if / else`、`for` 或明確的 `Optional` 判斷改寫成 Lambda、Stream API 或其他精簡語法。
10. 若 Lambda 屬於框架必要用法，或改用傳統寫法反而明顯增加複雜度，可以保留，並簡短說明原因。

### Optional 處理範例

對於以下寫法：

```java
Member member = memberRepository.findById(memberId)
        .orElseThrow(() ->
            new ResourceNotFoundException("40002")
        );
```

優先使用以下容易逐步理解的寫法，維持相同的例外類型與錯誤代碼：

```java
Optional<Member> optionalMember =
        memberRepository.findById(memberId);

if (optionalMember.isEmpty()) {
    throw new ResourceNotFoundException("40002");
}

Member member = optionalMember.get();
```

## 現有程式碼的檢查與調整流程

當任務包含程式碼風格檢查或調整時，先檢查授權範圍內的 Java 程式碼，特別留意：

- Service。
- Controller。
- GlobalExceptionHandler。
- Repository 中自行撰寫的程式邏輯。

找出 Lambda、Stream API、Method Reference 或其他不必要簡寫，評估是否能改成更容易理解的傳統寫法。先列出預計修改的檔案與內容，再進行必要調整；不因本文件而擴大當次任務範圍。

調整時必須遵守：

1. 不改變原有業務邏輯與功能。
2. 不改變 API 路徑、請求參數與回傳格式。
3. 不改變 Exception 類型、錯誤代碼及 HTTP 狀態碼。
4. 不改變資料庫結構與 Entity 關聯。
5. 保留原有權限、Token、報名名額及取消報名規則。
6. 不為了統一風格而大幅重構原本正常運作的架構。
7. 對保留的框架必要用法或較簡單的 Lambda 用法，說明保留原因。

完成程式碼調整後，執行可行且與變更相關的編譯及測試，並回報結果；若無法執行，應說明限制，不宣稱已通過。

目前以本機專案為主，不更新 AWS 或進行部署；部署須由使用者另行明確要求。

## 後續新增與修改功能

之後新增或修改 Java 程式碼時，持續遵守上述傳統寫法與行為保留原則。當有多種實作方式時，優先選擇適合 Java / Spring Boot 學習者理解，且不犧牲正確性與安全性的版本。

若任務本身需要變更業務功能、API 或資料模型，依使用者明確指定的需求處理，不將單純的風格調整擴大為功能變更。

## 資料庫中文註解與 Flyway 維護

之後在授權範圍內新增或修改資料表、欄位及 migration 時，必須一併維護下列中文說明，不需使用者每次提醒。這項規則不代表可以主動重寫全部歷史 migration 或部署 AWS。

### 名稱與中文說明的意義

1. 保留 SQL 檔名、資料表及欄位的英文名稱；「補中文註解／中文對照」不表示重新命名檔案，或將資料庫識別名稱改成中文。
2. 資料庫中的中文說明使用 MySQL 的資料表 `COMMENT` 與欄位 `COMMENT`，沿用 V14 的做法，讓資料庫工具可以查看用途。例如 `members` 的資料表 COMMENT 為「會員資料」，`account` 的欄位 COMMENT 為「登入帳號」。
3. 學習用 SQL 在原指令旁使用 `-- 中文說明`，解釋資料表、欄位、索引及限制條件。例如 `account VARCHAR(50) NOT NULL, -- 登入帳號`。這類原始碼註解不會寫入資料庫，不能代替資料庫 COMMENT。

### 正式 migration 與學習對照檔

1. 正式執行檔位於 `src/main/resources/db/migration`。已套用的版本不可為了補註解、改排版或改檔名而回頭修改；即使只改註解，也可能造成 Flyway 校驗碼不一致。V14 同樣是歷史版本，不因它負責中文 COMMENT 就直接修改它。
2. 新增資料表或欄位時，在新的 migration 內一併提供繁體中文 COMMENT。修改欄位時，保留或更新符合實際用途的 COMMENT，不可遺失既有中文說明。
3. 若已套用的資料表或欄位缺少中文 COMMENT，透過下一個未使用版本的 migration 補齊。使用 `MODIFY COLUMN` 時，必須核對並保留型別、長度、空值、預設值、自動編號及自動更新等設定，避免只補註解卻改變資料結構或行為。
4. 已套用版本的學習用中文註解另存於 `notes/migration-chinese-reference`，以相同 SQL 檔名建立對照檔。新增 migration 時同步維護需要的中文學習對照；正式新檔已有完整中文說明時，可在對照目錄 README 指向正式檔，避免重複維護。
5. 目前 V1～V13、V15、V16 的學習對照位於上述 notes 目錄；V14 直接閱讀正式檔。未來版本應同步更新該目錄的 README，維持清楚的閱讀位置。
6. 對照檔僅供閱讀，不手動執行、不覆蓋回正式目錄，也不加入 Flyway 掃描位置或應用程式資源。若對照檔重排換行，須確認移除說明註解與空白後，SQL 指令仍與正式版本一致。

### 驗證與交付

1. 修改前確認 migration 的套用狀態；無法確認時，保留既有版本並使用新版本處理，不假設舊檔尚未執行。
2. 修改後檢查歷史 migration 未變動，並執行可行的相關驗證。專案既有 `HistoricalMigrationIntegrityTests` 鎖定 V1～V13 校驗碼；不可為了讓註解修改通過而更新預期校驗碼或移除測試。
3. 不以直接修改 `flyway_schema_history`、關閉 Flyway 驗證或擅自執行 `repair` 解決註解造成的校驗錯誤。應保留學習對照，還原本次誤改的正式檔，且不得覆蓋使用者其他修改。
4. 回報時分別說明資料庫 COMMENT、學習用註解及正式 migration 的變更與驗證結果。只有新增或修改學習對照及文件時，不需要重新部署；需要套用新 migration 時，依使用者授權提供適用的部署指令或執行部署，不擅自更新 AWS。

## 教學與修改說明

不需要每次從最基礎的 Java 重新教起。請假設開發者已具備基本 Java、Spring Boot、JPA 概念，但對複雜的 Service 邏輯、方法呼叫、回傳型別及 Exception 處理仍需要練習。

修改涉及較不熟悉的語法或邏輯時，簡短說明：

- 這段程式碼負責什麼。
- 為什麼需要呼叫這個方法。
- 這個方法會回傳什麼。
- 發生錯誤時，如何進入 ExceptionHandler。
