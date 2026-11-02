const message = document.getElementById("message");
const list = document.getElementById("registration-list");
const actionMessage = document.getElementById("action-message");
const retry = document.getElementById("retry");
let busy = false;
function requireLogin() {
    if (DndAuth.session()) return false;
    list.replaceChildren();
    location.replace(DndAuth.loginUrl());
    return true;
}
function text(parent, tag, value) {
    const node = document.createElement(tag);
    node.textContent = value;
    parent.append(node);
}
function render(registration) {
    const card = document.createElement("article");
    text(card, "h2", registration.eventTitle || "活動資料無法取得");
    text(card, "p", "開始時間：" + (registration.startAt?.replace("T", " ") || "未提供"));
    text(card, "p", "地點：" + (registration.location || "未提供"));
    const registrationStatuses = { REGISTERED: "已報名", CANCELLED: "已取消報名" };
    const eventStatuses = { OPEN: "開放中", CLOSED: "已關閉", CANCELLED: "活動已取消" };
    text(card, "p", "報名狀態：" + (registrationStatuses[registration.status] || "未知"));
    text(card, "p", "活動狀態：" + (eventStatuses[registration.eventStatus] || "資料無法取得"));
    if (registration.eventStatus === "CANCELLED") {
        text(card, "p", "這場活動已由管理員取消，不再接受報名。");
    }
    const actions = document.createElement("div");
    actions.className = "actions";
    if (registration.eventStatus === "OPEN") {
        const link = document.createElement("a");
        link.textContent = "查看活動詳情";
        link.href = "/event-detail.html?id=" + encodeURIComponent(registration.eventId);
        actions.append(link);
    } else {
        text(actions, "span", "此活動目前不提供公開詳情。");
    }
    if (registration.canCancel) {
        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.textContent = "取消報名";
        cancel.addEventListener("click", () => cancelRegistration(registration));
        actions.append(cancel);
    } else if (registration.status === "REGISTERED") {
        text(card, "p", "目前無法自行取消報名。");
    }
    card.append(actions);
    list.append(card);
}
async function loadRegistrations() {
    if (requireLogin()) return;
    retry.hidden = true;
    list.replaceChildren();
    message.textContent = "報名紀錄載入中……";
    try {
        const response = await DndAuth.request("/registration");
        if (response.status === 401) { requireLogin(); return; }
        if (!response.ok) {
            throw new Error(await DndAuth.errorMessage(response, "無法讀取報名紀錄。"));
        }
        const registrations = await response.json();
        if (!Array.isArray(registrations)) throw new Error("報名資料格式不正確。");
        registrations.forEach(render);
        message.textContent = registrations.length ? "" : "你目前沒有報名紀錄。";
    } catch (error) {
        message.textContent = error.message || "無法讀取報名紀錄，請稍後再試。";
        retry.hidden = false;
    }
}
async function cancelRegistration(registration) {
    if (busy || !confirm("確定要取消「" + (registration.eventTitle || "此活動") + "」的報名嗎？")) return;
    busy = true;
    list.querySelectorAll("button").forEach(button => { button.disabled = true; });
    actionMessage.textContent = "取消報名中……";
    try {
        const response = await DndAuth.request(
            "/registration/events/" + encodeURIComponent(registration.eventId), { method: "DELETE" });
        if (response.status === 401) { requireLogin(); return; }
        if (!response.ok) {
            throw new Error(await DndAuth.errorMessage(response, "取消報名失敗，請稍後再試。"));
        }
        actionMessage.textContent = "已取消報名。";
    } catch (error) {
        actionMessage.textContent = error.message || "無法連線，請稍後再試。";
    } finally {
        await loadRegistrations();
        busy = false;
    }
}
retry.addEventListener("click", loadRegistrations);
loadRegistrations();