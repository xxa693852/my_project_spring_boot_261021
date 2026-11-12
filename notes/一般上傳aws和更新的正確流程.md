# LINE 機器人 Gemini 部署流程

AWS 專案目錄為 `/home/ubuntu/gtalent_spring_boot_260801`。

日常更新走「GitHub Actions 自動部署」。需要直接在 AWS 手動部署時，再使用「手動部署」。

## 手動部署

| 順序 | 在哪裡做 | 實際操作 | 完成確認 |
|---|---|---|---|
| 1. 打包新版程式 | Windows 專案根目錄的 PowerShell | 執行 `.\mvnw.cmd clean package`。 | 看到 `BUILD SUCCESS`，`target` 內產生新版 JAR。 |
| 2. 上傳新版 JAR | Windows → AWS | 把新版 JAR 上傳到 `/home/ubuntu/gtalent_spring_boot_260801/target/app.jar`。 | AWS 上的 `target/app.jar` 是這次打包的版本。 |
| 3. 設定 Gemini 金鑰 | AWS SSH 終端機 | 執行 `cd /home/ubuntu/gtalent_spring_boot_260801 && nano app.env`，新增或更新 `GEMINI_API_KEY=你的實際金鑰`。按 `Ctrl+O`、`Enter` 存檔，再按 `Ctrl+X` 離開。 | `app.env` 已存檔，並保留原有的 `LINE_CHANNEL_ACCESS_TOKEN` 和 `LINE_CHANNEL_SECRET`。 |
| 4. 重建應用容器 | AWS SSH 終端機 | 在專案目錄執行 `docker compose up -d --force-recreate app`，再執行 `docker compose logs --tail=100 app`。 | 日誌顯示 Spring Boot 啟動完成。容器使用 AWS 上的新版 JAR 並載入 `app.env`。 |
| 5. 確認 ngrok 隧道 | AWS SSH 終端機 | 如果使用 ngrok，確認隧道在 AWS 上運行，並轉發到 AWS 的 `8080`。既有隧道正常時可沿用。 | ngrok 的公開 HTTPS 網址指向 AWS 上的服務。 |
| 6. 驗證 LINE webhook | LINE Developers → Messaging API → Webhook settings | 若沿用原本的 ngrok 網域，Webhook URL 填 `https://opposing-catcher-oxidation.ngrok-free.dev/api/line/webhook`。按 **Verify**，確認 **Use webhook** 已開啟。 | 顯示 **Success**。 |
| 7. 測試 AI 回覆 | LINE 私訊機器人 | 傳「請用繁體中文介紹你自己」。若沒有回覆，在 AWS 專案目錄執行 `docker compose logs --tail=100 app` 查看錯誤。 | LINE 收到 Gemini 生成的回覆。 |

第 3 步適用於首次設定或更換金鑰。第 5、6 步適用於首次串接、隧道異常或網址變更。

## GitHub Actions 自動部署

| 順序 | 你要做什麼 | CI/CD 會做什麼 |
|---|---|---|
| 1. 確認 AWS 環境設定 | 確認 AWS 專案目錄的 `app.env` 已加入 `GEMINI_API_KEY`。已存好就直接進到下一步。 | 部署時使用 AWS 上現有的 `app.env`。 |
| 2. 從介面 commit 並 push | 用你平常的介面把新版程式推到 GitHub 的 `main`。若先推到其他分支，合併進 `main` 時會觸發部署。 | 執行 Maven 打包、上傳 `app.jar` 和 `compose.yml`、重建 AWS 容器。 |
| 3. 查看部署結果 | 到 GitHub 的 **Actions** 確認這次部署成功。 | 檢查 AWS 上的登入頁能否回應。若新版未通過部署檢查，workflow 會嘗試回滾。 |
| 4. 測試 LINE AI 回覆 | 若既有 webhook 已指向 AWS，直接在 LINE 傳「請用繁體中文介紹你自己」。 | Workflow 檢查登入頁。實際收到 AI 回覆才完成 LINE 到 Gemini 的測試。 |

走 CI/CD 時，可以省略本機手動打包、手動上傳 JAR 和手動重建容器。

如果只改了 AWS 的 `app.env`，而且 Actions 已經跑完，在 AWS 專案目錄執行 `docker compose up -d --force-recreate app`，讓容器載入新設定。