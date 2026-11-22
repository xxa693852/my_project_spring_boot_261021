const reportForm = document.getElementById("report-form");
const from = document.getElementById("report-from");
const through = document.getElementById("report-through");
const download = document.getElementById("download-report");
const reportMessage = document.getElementById("report-message");
const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Taipei", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date());
const today = {};
for (const part of parts) today[part.type] = part.value;
from.value = today.year + "-" + today.month + "-01";
const last = new Date(Date.UTC(Number(today.year), Number(today.month), 0)).getUTCDate();
through.value = today.year + "-" + today.month + "-" + String(last).padStart(2, "0");
let downloading = false;
reportForm.addEventListener("submit", async event => {
    event.preventDefault();
    if (downloading) return;
    if (!reportForm.reportValidity()) return;
    if (from.value > through.value) { reportMessage.textContent = "起日不得晚於迄日。"; return; }
    downloading = true;
    download.disabled = true;
    reportMessage.textContent = "正在產生報表……";
    try {
        const response = await DndAdmin.request("/admin/reports/export?" + new URLSearchParams({ from: from.value, through: through.value }));
        if (!response.ok) throw new Error(await DndAdmin.errorMessage(response, "報表下載失敗，請稍後重試。"));
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        const filename = (response.headers.get("Content-Disposition") || "").match(/filename="?([^";]+)"?/);
        link.download = filename ? filename[1] : "dnd-report.xlsx";
        document.body.append(link); link.click(); link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
        reportMessage.textContent = "報表已產生並開始下載。檔名與每張工作表均附產生時間，下載後不會自動更新。";
    } catch (error) { reportMessage.textContent = error.message || "報表下載失敗。"; }
    finally { downloading = false; download.disabled = false; }
});
