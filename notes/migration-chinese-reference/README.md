# Migration 中文學習對照

本目錄保留 V1～V13、V15、V16 的中文註解版本，方便對照資料表、欄位、索引與限制條件；V14 本來已有中文說明，請直接閱讀正式檔案。

正式執行的 SQL 位於 `src/main/resources/db/migration`。已套用的遷移檔即使只改註解，也會改變 Flyway 校驗碼，因此正式檔維持原版。本目錄位於 notes，不在 Flyway 掃描位置，也不會隨 Maven 標準資源打包進 JAR。

本目錄僅供閱讀，請勿手動執行這些 SQL，也不要覆蓋回正式 migration 目錄。資料庫結構有新需求時，應新增下一個版本的遷移檔。

此次只整理本機學習註解，未修改資料庫、不需執行 Flyway repair，也不需重新部署 AWS。

## V17

管理員個別取消報名歷程：請閱讀正式檔 `src/main/resources/db/migration/V17__create_registration_cancellations.sql`，已含資料庫繁體中文 COMMENT 與學習說明。歷程自啟用功能後累積，不回填過去的取消紀錄。

## V18

個別取消 Email 通知：正式檔 `src/main/resources/db/migration/V18__create_registration_cancellation_emails.sql` 已有完整中文 COMMENT 與學習註解。

## V19

個別取消 LINE 通知：正式檔 `src/main/resources/db/migration/V19__create_registration_cancellation_lines.sql` 已有完整繁體中文 COMMENT、索引與限制條件的學習說明。
