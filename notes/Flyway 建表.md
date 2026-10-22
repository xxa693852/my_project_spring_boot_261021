 ## 1. 準備 Flyway migration

  建立 migration：

  src/main/resources/db/migration/V1__create_core_tables.sql

  用途：

  - 建立專案所需的四張資料表
  - 由 Flyway 管理資料庫版本
  - V1 成功執行後，紀錄會寫進 flyway_schema_history
  - 後續重啟不會重複建立資料表

  Spring Boot 的主要設定：

  spring.flyway.enabled=true
  spring.flyway.locations=classpath:db/migration,classpath:db/seeder
  spring.flyway.baseline-on-migrate=true

  spring.jpa.hibernate.ddl-auto=validate

  其中：

  - Flyway 負責修改資料庫結構
  - Hibernate 只驗證 Entity 和資料表是否一致，不自動建表

  ## 2. 打包 Spring Boot

  在 Windows 專案目錄：

  cd D:\Java26\my_project_spring_boot_261021
  .\mvnw.cmd package

  本機測試曾出現：

  Access denied for user 'dnd_event_prod_test'@'localhost'

  這是 Windows 本機測試資料庫帳號問題，不影響 AWS 正式環境。

  如果只需要產生部署用 JAR、暫時略過測試，可使用：

  .\mvnw.cmd package -DskipTests

  產生的 JAR 通常位於：

  target\my_project_spring_boot_261021-0.0.1-SNAPSHOT.jar

  ## 3. SSH 連線 AWS Ubuntu

  Windows 使用：

  ssh -i "C:\test\test46" ubuntu@43.213.4.235

  進入專案目錄：

  cd /home/ubuntu/my_project_spring_boot_261021

  部署目錄中包含：

  .env
  app.jar
  target/

  先前是手動啟動：

  java -jar app.jar

  缺點是 SSH 中斷或 Ubuntu 重開機後，程式可能停止。

  ## 4. 確認 Java、程序與連接埠

  Java 實際路徑：

  readlink -f "$(command -v java)"

  得到：

  /usr/lib/jvm/java-21-openjdk-amd64/bin/java

  查看 Spring Boot 程序：

  pgrep -af "java.*app.jar"

  查看監聽埠：

  ss -ltnp | grep java

  本專案使用：

  8081

  另一個既有 Java 專案使用 8080，兩者沒有衝突。

  ## 5. 建立 systemd 服務

  服務檔位置：

  /etc/systemd/system/my-project-spring-boot.service

  內容：

  [Unit]
  Description=My Project Spring Boot Application
  After=network-online.target
  Wants=network-online.target

  [Service]
  Type=simple
  User=ubuntu
  Group=ubuntu
  WorkingDirectory=/home/ubuntu/my_project_spring_boot_261021
  EnvironmentFile=/home/ubuntu/my_project_spring_boot_261021/.env
  ExecStart=/usr/lib/jvm/java-21-openjdk-amd64/bin/java -jar /home/ubuntu/my_project_spring_boot_261021/app.jar
  SuccessExitStatus=143
  Restart=on-failure
  RestartSec=5
  TimeoutStopSec=30

  [Install]
  WantedBy=multi-user.target

  重要設定：

  - WorkingDirectory：讓 Spring Boot 從正確目錄執行
  - EnvironmentFile：載入 .env
  - ExecStart：指定 Java 與 JAR 的絕對路徑
  - Restart=on-failure：異常停止時自動重啟
  - WantedBy=multi-user.target：支援開機自動啟動

  ## 6. 啟用並啟動 systemd

  重新讀取 service：

  sudo systemctl daemon-reload

  設定開機啟動：

  sudo systemctl enable my-project-spring-boot.service

  停止原本手動執行的 Java，再由 systemd 接管：

  kill <原本的PID>
  sudo systemctl start my-project-spring-boot.service

  也可以直接使用：

  sudo systemctl restart my-project-spring-boot.service

  ## 7. 驗證服務狀態

  查看狀態：

  sudo systemctl status my-project-spring-boot.service

  確認結果：

  Loaded: loaded
  Active: active (running)

  確認是否開機啟動：

  systemctl is-enabled my-project-spring-boot.service

  結果：

  enabled

  確認服務正在運行：

  systemctl is-active my-project-spring-boot.service

  結果：

  active

  確認 8081：

  ss -ltnp | grep :8081

  HTTP 測試：

  curl -i http://127.0.0.1:8081/

  根路徑回傳 404 是因為目前沒有對應 / 的 Controller；這仍表示 Tomcat 已正常接收請求。

  ## 8. 驗證 Flyway 與資料庫

  查看 systemd 日誌：

  sudo journalctl -u my-project-spring-boot.service -n 100 --no-pager

  本次確認到：

  Successfully validated 1 migration
  Current version of schema `dnd_event_prod`: 1
  Schema `dnd_event_prod` is up to date. No migration necessary.
  Tomcat started on port 8081
  Started MyProjectSpringBoot261021Application

  這代表：

  - AWS MySQL 連線成功
  - V1__create_core_tables.sql 已執行過
  - 四張表仍存在
  - Flyway 沒有重複建表
  - Spring Boot 已完整啟動

  ## 日後最常用指令

  sudo systemctl status my-project-spring-boot
  sudo systemctl restart my-project-spring-boot
  sudo systemctl stop my-project-spring-boot
  sudo systemctl start my-project-spring-boot
  sudo journalctl -u my-project-spring-boot -f

  更新 JAR 後通常執行：

  sudo systemctl restart my-project-spring-boot

  現在整體流程是：

  Ubuntu 開機
  → systemd 啟動 app.jar
  → Spring Boot 載入 .env
  → 連接 AWS MySQL
  → Flyway 檢查 migration
  → V1 已執行，所以不重建資料表
  → Tomcat 在 8081 提供服務