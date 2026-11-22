const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const { spawn } = require("node:child_process");

const staticDir = path.resolve(__dirname, "../../main/resources/static");
const chrome = process.env.CHROME_PATH || [
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
].find(candidate => fs.existsSync(candidate));

// 使用真正的瀏覽器量測 CSS，DOM 模擬無法確認 Grid 與圖片縮放。
test("衝突、重試與取消報名不撐高卡片或改變封面，詳細清單放在對話框", {
    skip: !chrome && "需要 Chrome 或 Edge，可用 CHROME_PATH 指定",
    timeout: 60000
}, async () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "dnd-card-layout-"));
    const source = fs.readFileSync(path.join(staticDir, "events.js"), "utf8");
    const presentation = fs.readFileSync(path.join(staticDir, "event-presentation.js"), "utf8");
    const detailSource = fs.readFileSync(path.join(staticDir, "event-detail.js"), "utf8");
    const renderDetailSource = detailSource.slice(detailSource.indexOf("function addFact("), detailSource.indexOf("function time("));
    const css = fs.readFileSync(path.join(staticDir, "site.css"), "utf8");
    const coverUrl = pathToFileURL(path.join(staticDir, "tavern-cover.jpg")).href;
    const fixture = `<!DOCTYPE html><meta charset="utf-8">
        <style>${css}</style><p id="message"></p><main class="page" id="event-list"></main>
        <dialog id="registration-conflict-dialog" aria-labelledby="registration-conflict-title">
            <h2 id="registration-conflict-title">報名時段衝突</h2>
            <div id="registration-conflict-content"></div>
            <form method="dialog"><button autofocus>關閉</button></form>
        </dialog>
        <pre id="result">RUNNING</pre><script>
        window.addEventListener("error", error => { document.getElementById("result").textContent = "FAIL " + error.message; });
        </script><script>
        const DndTime = { format: value => value || "" };
        const DndAuth = { session: () => true, errorMessage: async () => "取消失敗" };
        function showEventFee(node) { node.textContent = "免費"; }
        function listParams() { return new URLSearchParams(); }
        async function confirmRegistrationCancellation() { return true; }
        ${presentation}
        ${source.slice(0, source.indexOf("const searchForm ="))}
        const event = { id: 1, title: "新手友善團 王道征途：摧毀帝國", status: "OPEN",
            startAt: "2099-12-15T12:30:00", endAt: "2099-12-15T17:30:00",
            registrationDeadline: "2099-12-15T10:00:00", remainingSlots: 5,
            capacity: 6, canRegister: true, registrationFee: 0,
            hostName: "主持人", location: "台北公館店 B桌", description: "活動說明",
            tags: ["新手友善", "劇情向", "攻城向", "戰爭向", "提供骰子、飲食"] };
        let requests = 0;
        let mode = "conflict";
        let releaseRequest;
        let pauseRequest = false;
        let conflictCount = 1;
        DndAuth.request = async (requestPath) => {
            requests++;
            if (pauseRequest) await new Promise(resolve => { releaseRequest = resolve; });
            if (mode === "success") return new Response("{}", { status: 200 });
            if (mode === "failure") return new Response("{}", { status: 500 });
            return new Response(JSON.stringify({ message: "你已報名另一場時段重疊的活動",
                conflicts: Array.from({ length: conflictCount }, () => ({
                    title: requestPath + "新手歡樂場 帝國殞落".repeat(conflictCount), startAt: event.startAt, endAt: event.endAt }))
            }), { status: 400 });
        };
        window.fetch = async () => new Response(JSON.stringify(event), { status: 200 });
        function check(value, message) { if (!value) throw new Error(message); }
        async function settled() { for (let i = 0; i < 20; i++) await new Promise(resolve => setTimeout(resolve, 0)); }
        async function run() {
            const measurements = [];
            for (const width of [1280, 768, 681, 680, 390, 320]) {
                // iframe 具有獨立 viewport，能實際套用手機 media query。
                const frame = document.createElement("iframe");
                frame.style.cssText = "border:0;width:" + width + "px;height:1600px";
                document.body.append(frame);
                const doc = frame.contentDocument;
                doc.open(); doc.write('<html class="events-page"><style>' + ${JSON.stringify(css)} + '</style><main class="page" id="cards"></main></html>'); doc.close();
                const target = doc.getElementById("cards");
                const card = createEventCard({ ...event }, 1, null, true);
                const other = createEventCard({ ...event, id: 2 }, 1, { status: "REGISTERED" }, true);
                target.append(card, other);
                // 取消流程會在主列表內查找舊衝突，因此將 iframe 視為列表的子節點來源。
                eventList.querySelectorAll = selector => target.querySelectorAll(selector);
                const cover = card.querySelector("img");
                cover.loading = "eager";
                cover.src = ${JSON.stringify(coverUrl)};
                await settled();
                const buttons = card.querySelectorAll("button");
                const feedback = card.querySelector('[role="status"]');
                const cancel = other.querySelectorAll("button")[1];
                const size = () => {
                    const r = cover.getBoundingClientRect();
                    return [r.width, r.height, card.getBoundingClientRect().height,
                        other.getBoundingClientRect().top];
                };
                const initial = size();
                function checkCoverFrame(item) {
                    const image = item.querySelector("img");
                    const imageBounds = image.getBoundingClientRect();
                    const frameBounds = item.querySelector(".event-cover-frame").getBoundingClientRect();
                    check(imageBounds.width === frameBounds.width && imageBounds.height === frameBounds.height,
                        width + "px 圖片未填滿封面框");
                    check(frame.contentWindow.getComputedStyle(image).objectFit === "cover", "圖片未保持等比例裁切");
                    if (width > 680) {
                        check(Math.abs(frameBounds.height - item.clientHeight) <= 1, "桌面封面底部留下空白");
                    } else {
                        check(frameBounds.height <= 285, "手機封面被內容撐高");
                    }
                }
                checkCoverFrame(card);
                if (width === 1280) {
                    const shortCard = createEventCard({ ...event, id: 8, title: "短標題", description: "", tags: [] }, 1, null, true);
                    target.append(shortCard);
                    checkCoverFrame(shortCard);
                    check(shortCard.getBoundingClientRect().height < cover.getBoundingClientRect().width * 1.25,
                        "短內容仍被原先直式封面高度撐長");
                    shortCard.remove();
                }
                const unchanged = stage => check(JSON.stringify(size()) === JSON.stringify(initial),
                    width + "px " + stage + " 卡片或封面尺寸改變 " + JSON.stringify([initial, size()]));
                pauseRequest = true; mode = "conflict";
                const before = requests;
                buttons[0].click(); buttons[0].click();
                check(requests === before + 1, "處理中重複送出 API");
                check(buttons[0].disabled, "處理中沒有停用按鈕");
                unchanged("處理中");
                releaseRequest(); pauseRequest = false;
                await settled();
                check(conflictDialog.open, "沒有開啟衝突提示視窗");
                check(conflictContent.querySelectorAll("li").length === conflictCount, "未顯示完整衝突清單");
                check(feedback.querySelectorAll("li").length === 0, "完整清單仍留在卡片內");
                check(conflictContent.querySelector("a").getAttribute("href") === "/my-registrations.html", "缺少報名紀錄連結");
                unchanged("衝突");
                conflictDialog.close();
                feedback.querySelector("button").click();
                check(conflictDialog.open, "無法重新查看衝突詳情");
                conflictDialog.close();
                conflictCount = 8;
                for (let i = 0; i < 3; i++) {
                    buttons[0].click(); await settled();
                    check(conflictContent.querySelectorAll("li").length === conflictCount, "重試累積或遺漏衝突項目");
                    check(feedback.querySelectorAll("button").length === 1, "重試累積詳情按鈕");
                    unchanged("重試");
                    conflictDialog.close();
                }
                const third = createEventCard({ ...event, id: 3 }, 1, null, true);
                target.append(third);
                third.querySelector("button").click(); await settled();
                check(conflictContent.textContent.includes("eventId=3"), "未顯示另一張卡片的衝突");
                conflictDialog.close();
                feedback.querySelector("button").click();
                check(conflictContent.textContent.includes("eventId=1"), "重新查看時顯示了別張卡片的衝突");
                conflictDialog.close();
                third.remove();
                mode = "failure"; cancel.click(); await settled();
                check(feedback.hasAttribute("data-registration-conflict"), "取消失敗不應清除衝突");
                mode = "success"; cancel.click(); await settled();
                check(!feedback.hasAttribute("data-registration-conflict"), "取消成功仍保留舊衝突");
                check(feedback.querySelectorAll("li").length === 0, "取消成功仍保留舊清單");
                check(feedback.textContent.includes("重新嘗試"), "缺少重新檢查提示");
                unchanged("取消其他報名");
                buttons[0].click(); await settled();
                check(feedback.textContent === "報名成功。", "解除衝突後無法報名");
                unchanged("報名成功");
                // 以允許的最大長度檢查無空白數字、英文與中文，不靠縮短原始資料解決排版。
                for (const character of ["9", "W", "龍"]) {
                    const longEvent = { ...event, id: 9, title: character.repeat(100),
                        location: character.repeat(255), hostName: character.repeat(50),
                        description: character.repeat(5000) };
                    const longCard = createEventCard(longEvent, 1, null, true);
                    target.append(longCard);
                    checkCoverFrame(longCard);
                    const heading = longCard.querySelector("h2");
                    const locationLine = longCard.querySelector(".event-location");
                    const host = longCard.querySelector(".event-host");
                    const win = frame.contentWindow;
                    for (const [node, maxLines] of [[heading, 2], [locationLine, 2], [host, 1],
                        [longCard.querySelector(".event-preview"), 2]]) {
                        const style = win.getComputedStyle(node);
                        check(node.getBoundingClientRect().height <= parseFloat(style.lineHeight) * maxLines + 1,
                            width + "px 長文字超過摘要行數 " + node.className);
                    }
                    check(heading.textContent === longEvent.title && heading.title === longEvent.title, "標題原文遺失");
                    check(locationLine.textContent === longEvent.location, "地址原文遺失");
                    check(new URL(locationLine.querySelector("a").href).searchParams.get("query") === longEvent.location,
                        "地圖連結使用了截短地址");
                    check(host.textContent === "主持人：" + longEvent.hostName, "主持人原文遺失");
                    check(longCard.scrollWidth <= longCard.clientWidth + 1, "長資料使卡片橫向溢出");
                    const badge = longCard.querySelector(".event-status");
                    const h = heading.getBoundingClientRect(), b = badge.getBoundingClientRect();
                    check(h.right <= b.left + 1, "長標題擠壓狀態標籤");
                    const actions = longCard.querySelector(".actions").getBoundingClientRect();
                    const bounds = longCard.getBoundingClientRect();
                    check(actions.right <= bounds.right && actions.bottom <= bounds.bottom, "按鈕超出卡片");

                    // 以實際詳情頁的 showEvent 渲染，確認完整資料仍可閱讀且不橫向爆框。
                    const detail = doc.createElement("section");
                    detail.className = "event-detail-panel";
                    detail.innerHTML = '<article id="event-detail"><img class="event-cover"><div class="event-summary">' +
                        '<p class="event-labels"></p><h1></h1><dl class="event-facts"></dl></div></article><p class="description"></p>';
                    target.append(detail);
                    const renderDetail = new Function("event", "title", "cover", "labels", "facts", "description", "panel", "time",
                        ${JSON.stringify(renderDetailSource)} + ';showEvent(event);');
                    renderDetail(longEvent, detail.querySelector("h1"), detail.querySelector("img"),
                        detail.querySelector(".event-labels"), detail.querySelector("dl"), detail.querySelector(".description"),
                        detail, value => value);
                    check(detail.querySelector("h1").textContent === longEvent.title, "詳情標題不完整");
                    check(detail.querySelector(".description").textContent === longEvent.description, "詳情介紹不完整");
                    check(detail.querySelectorAll("dd")[0].textContent === longEvent.hostName, "詳情主持人不完整");
                    check(detail.querySelectorAll("dd")[1].textContent.startsWith(longEvent.location), "詳情地址不完整");
                    check(detail.scrollWidth <= detail.clientWidth + 1, width + "px 詳情長資料橫向爆框");
                    check(doc.documentElement.scrollWidth <= doc.documentElement.clientWidth + 1, "頁面出現橫向捲軸");
                    detail.remove();
                    longCard.remove();
                }
                measurements.push({ viewport: width, coverAndCard: initial });
                frame.remove();
            }
            return measurements;
        }
        run().then(value => { document.getElementById("result").textContent = "PASS " + JSON.stringify(value); })
            .catch(error => { document.getElementById("result").textContent = "FAIL " + error.stack; });
        </script>`;
    const fixturePath = path.join(tempDir, "fixture.html");
    fs.writeFileSync(fixturePath, fixture);
    try {
        const output = await new Promise((resolve, reject) => {
            const child = spawn(chrome, ["--headless=new", "--no-first-run", "--no-default-browser-check",
                "--disable-background-networking", "--allow-file-access-from-files",
                "--user-data-dir=" + path.join(tempDir, "profile"), "--dump-dom",
                "--virtual-time-budget=60000", pathToFileURL(fixturePath).href], { windowsHide: true });
            let stdout = "";
            let stderr = "";
            child.stdout.on("data", data => { stdout += data; });
            child.stderr.on("data", data => { stderr += data; });
            const timer = setTimeout(() => { child.kill(); reject(new Error("瀏覽器驗證逾時")); }, 45000);
            child.on("error", error => { clearTimeout(timer); reject(error); });
            child.on("close", code => { clearTimeout(timer); resolve({ code, stdout, stderr }); });
        });
        assert.equal(output.code, 0, output.stderr);
        const result = output.stdout.match(/<pre id="result">([\s\S]*?)<\/pre>/)?.[1];
        assert.match(result || "找不到測試結果", /^PASS /);
        console.log(result);
    } finally {
        // 僅清理由本測試建立的暫存目錄。
        if (path.dirname(tempDir) === os.tmpdir() && path.basename(tempDir).startsWith("dnd-card-layout-")) {
            fs.rmSync(tempDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
        }
    }
});
