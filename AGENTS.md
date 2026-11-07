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

## 教學與修改說明

不需要每次從最基礎的 Java 重新教起。請假設開發者已具備基本 Java、Spring Boot、JPA 概念，但對複雜的 Service 邏輯、方法呼叫、回傳型別及 Exception 處理仍需要練習。

修改涉及較不熟悉的語法或邏輯時，簡短說明：

- 這段程式碼負責什麼。
- 為什麼需要呼叫這個方法。
- 這個方法會回傳什麼。
- 發生錯誤時，如何進入 ExceptionHandler。
