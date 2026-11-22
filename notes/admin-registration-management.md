# 第一批：活動報名名單與個別取消

完成日期：2026-11-22。僅修改本機，未部署 AWS。

## 操作方式

1. 重新啟動本機 Spring Boot；使用原有 Flyway 設定套用 V17（請先確認使用正確的本機資料庫）。
2. 開啟 `/admin-events.html`，登入後台。
3. 點活動卡片的「已報名／剩餘名額」或「管理報名」。
4. 名單顯示姓名、帳號、報名時間、報名狀態、最新訂單付款狀態、取消時間。
5. 按個別取消，填寫原因並確認。成功後重新載入名單和活動卡片名額。
6. 已付款者變為待全額退款。於「修改活動 → 付款與退款紀錄」登記已在金流後台完成的退款；本站不直接發動金流退款。

取消前會檢查管理員、活動、報名所屬活動及報名狀態；管理員可在報名截止後取消，但活動必須仍為 OPEN 且尚未開始。逾期待付款不占名額，也不提供再次取消。一般玩家報名與自行取消規則不變，補位仍受報名截止時間限制。

## 程式閱讀位置

- `AdminRegistrationController`：GET 名單、POST 個別取消；操作者取自後台 Session 屬性，原因由 Validation 驗證。
- `AdminRegistrationService.list`：批次取得會員與付款，以最新訂單建立回應，分開計算報名、有效保留和剩餘席位。
- `AdminRegistrationService.cancel`：先驗證管理員，再取得活動悲觀鎖；同一交易寫入取消快照、取消報名、清除保留期限、沿用 PaymentLifecycle.cancelLatest 更新付款狀態。
- 找不到活動或指定活動的報名時拋 ResourceNotFoundException；條件不符拋 IllegalArgumentException，交給既有 GlobalExceptionHandler。

## 紀錄與資料庫

正式 migration 僅新增 `V17__create_registration_cancellations.sql`，V1～V16 未修改。新表與各欄位均有繁體中文 COMMENT，含查詢索引與外鍵；學習對照 README 已指向此檔案。

管理員每次個別取消新增一筆快照，包含報名、活動、會員、操作者、原因、原報名時間、取消前狀態、最新付款狀態及取消時間。重新報名不覆蓋此表。不回填過往紀錄，也不代表已建立所有報名、會員自行取消或整場取消的完整事件歷程。後續報表若需要每次報名事件，仍須另行設計。

此批未加入個別取消通知、自動候補、管理員代報名、恢復席位或 Excel 報表。

## 驗證結果

- Maven 編譯與 31 項相關 Java 測試通過：AdminRegistrationTests（8）、AdminAuthIsolationTests（10）、RegistrationHistoryTests（6）、TraditionalStyleRegressionTests（6）、HistoricalMigrationIntegrityTests（1）。
- 前端名單測試 3 項通過，涵蓋安全文字顯示、取消原因與正確 API／重新載入，以及不可取消紀錄不顯示操作。
- JavaScript 語法與 git diff --check 通過。
- 未啟動服務或套用 V17 至實際 MySQL；未進行實際資料庫交易、並行壓力或桌面／手機瀏覽器視覺驗收。

重現指令（PowerShell）：

```powershell
.\mvnw.cmd -o -q '-Daether.syncContext.named.factory=rwlock-local' '-Dtest=AdminRegistrationTests,RegistrationHistoryTests,HistoricalMigrationIntegrityTests,AdminAuthIsolationTests,TraditionalStyleRegressionTests' test
node --check src/main/resources/static/admin-events.js
node --test --test-isolation=none src/test/js/admin-roster.test.cjs
```

本次環境預設 Maven 檔案鎖無法寫入使用者快取，改用本程序內鎖與離線既有依賴完成驗證；Node 測試使用同程序模式，避免環境限制建立子程序。

## 2026-11-22 介面調整

- 活動卡片的名額入口改用淺綠樣式，與操作列加上間距與分隔線。
- 名單標題與活動名稱分開，調整摘要、表格與歷程間距。
- 選中玩家列、取消確認框及送出按鈕採紅色警示；保留報名維持綠色。關閉或保留時清除選取狀態。
- 取消原因加入共用 data-char-limit=255 計數，開啟確認區時刷新計數。
- JS 語法、3 項名單測試及2 項既有字數／草稿測試通過，git diff --check 通過；未進行瀏覽器視覺驗收。
- 個別取消 Email 尚未實作。本輪先討論原因的對外可見性；建議資料提交後由持久化通知佇列寄出，通知失敗不恢復已取消的席位，後台可查看寄送結果。現有整場取消模板不可直接套用，避免誤稱整場活動取消；既有通知會優先 LINE，若要求一定 Email，需明確使用 Email 流程。

### 個別取消 Email（使用者確認後）

使用者已確認取消原因直接寄給玩家，欄位已明示。新增 V18 專用持久化佇列，Email 與取消紀錄共用交易，排程於提交後每5秒檢查並寄送。信件包含活動、原訂時間、取消時間、取消原因及既有付款處理說明。後台歷程顯示寄送狀態，需按重新整理查看更新。

只使用 Email，不以 LINE 取代。SENT 僅表示郵件服務接受，非保證入信匣；SMTP 例外標記 UNKNOWN，缺信箱標記 FAILED，不自動重寄。若程序於 SENDING 中斷，保留該狀態供人工確認，避免重寄。尚未提供後台手動重寄操作。

此節取代上方「個別取消通知尚未實作」的舊狀態。V17、V18 均須透過正常 Flyway 啟動套用，未修改既有 migration，未操作真實收件人。

本輪最終驗證：22 項相關 Java 測試通過（名單8、後台驗證10、歷史校驗1、Email排程3）；5 項前端測試通過。JS 語法與 diff 空白檢查通過。V18 尚未實際套用，未進行真實 SMTP 或瀏覽器視覺驗收。

## 第二次取消確認

2026-11-22：填寫原因按送出後，先開啟置中原生 dialog，列出活動、玩家、原因與釋出名額／通知的影響。預設焦點在綠色「保留報名」，僅再次按紅色「確認取消報名」才呼叫 API；保留或 Escape 關閉不送出，保留原輸入內容。等待確認與送出時鎖住原表單，防止重複操作。4 項名單前端測試、JS 語法與 diff 檢查通過；未做瀏覽器視覺驗收。未修改後端、migration 或部署。

2026-11-22：管理員取消歷程改為付款紀錄風格的六欄 table，包含取消時間、會員編號、管理員編號、原報名時間、取消原因與通知狀態；保留純文字安全呈現、空資料提示與手機水平捲動。4 項前端測試、語法與 diff 檢查通過，未做瀏覽器視覺驗收。

## 2026-11-22 個別取消增加 LINE

- 依使用者要求補齊綁定 LINE 玩家的取消通知，保留 Email 固定寄送；兩管道獨立排程與狀態，不以 LINE 成功為由略過 Email，也不因 Email 失敗省略 LINE。
- `RegistrationCancellationMail.enqueue` 在既有取消交易內將相同訊息快照寫入 Email 與 LINE 佇列，兩者提交後才會被排程看見。LINE 對象取自取消當時的 members.notification_line_user_id。
- 新增 `RegistrationCancellationLine`，使用既有 LineNotificationClient 的設定、收件人確認和推播方法。透過條件更新領取通知，避免多個工作者重複推播。錯誤或結果不明保留狀態，不自動重試；長時間停在 SENDING 須人工確認。
- 新增正式 V19 `V19__create_registration_cancellation_lines.sql`，新表、欄位含繁體中文 COMMENT 與索引、外鍵學習說明；對照 README 已指向正式檔。V1～V18 未為本次 LINE 功能修改。V18 已部署，故不用修改 V18 解決需求。
- 不回填或補發既有取消紀錄，後台舊資料顯示「舊紀錄（未啟用 LINE 通知）」。綁定後的新取消才會安排推播。
- 取消歷程改為七欄，分開顯示 Email / LINE 結果；SENT 表示服務已接受，不能保證使用者已讀。
- Java 測試涵蓋已綁定成功、未綁定、未設定、收件人錯誤、推播遭拒、逾時結果不明、重複領取，以及雙管道使用相同訊息快照。前端涵蓋兩管道狀態與舊紀錄標示。
- 未套用 V19 至實際 MySQL，未部署 AWS，也未做真實 LINE、SMTP 或瀏覽器視覺驗收。需部署新版並正常套用 V19 後，以測試會員新報名／新取消驗收；不必重新綁定既有有效 LINE。

本次驗證結果：30 項相關 Java 測試及5 項名單前端測試通過；JS 語法與 git diff --check 通過。V17、V18 與前次已打包版本內容相同。
