const message = document.getElementById("message");
const list = document.getElementById("registration-list");
const actionMessage = document.getElementById("action-message");
const retry = document.getElementById("retry");
const searchForm = document.getElementById("registration-search");
const pagination = document.getElementById("registration-pagination");
const previousPage = document.getElementById("registration-previous");
const nextPage = document.getElementById("registration-next");
const pageSize = 8;
let registrations = [];
let currentPage = 1;
let keyword = "";
let cancelableOnly = false;
let busy = false;

function renderRegistrations(page = currentPage) {
    // 先篩選完整清單，再切出目前這一頁的資料。
    const matches = registrations.filter(registration => {
        if (cancelableOnly && !registration.canCancel) return false;
        return !keyword
            || (registration.eventTitle || "").toLowerCase().includes(keyword)
            || (registration.location || "").toLowerCase().includes(keyword);
    });
    const totalPages = Math.max(1, Math.ceil(matches.length / pageSize));
    // 取消最後一頁的最後一筆後，回到仍有結果的頁面。
    currentPage = Math.min(Math.max(1, page), totalPages);
    const start = (currentPage - 1) * pageSize;
    list.replaceChildren();
    matches.slice(start, start + pageSize).forEach(render);
    pagination.hidden = totalPages < 2;
    if (!pagination.hidden) {
        renderPageJump(pagination, currentPage, totalPages, changePage);
        previousPage.disabled = currentPage === 1;
        nextPage.disabled = currentPage === totalPages;
    }
    if (matches.length) {
        message.textContent = "共 " + matches.length + " 筆，第 " + currentPage + " / " + totalPages + " 頁";
    } else {
        message.textContent = keyword || cancelableOnly ? "沒有符合條件的報名。" : "你目前沒有報名紀錄。";
    }
}

function changePage(page) {
    renderRegistrations(page);
    message.tabIndex = -1;
    message.focus();
}

function applySearch() {
    keyword = searchForm.elements.keyword.value.trim().toLowerCase();
    cancelableOnly = searchForm.elements.cancelableOnly.checked;
    renderRegistrations(1);
}

searchForm.addEventListener("submit", event => {
    event.preventDefault();
    applySearch();
});
searchForm.elements.cancelableOnly.addEventListener("change", applySearch);
document.getElementById("clear-registration-search").addEventListener("click", () => {
    searchForm.reset();
    applySearch();
});
previousPage.addEventListener("click", () => changePage(currentPage - 1));
nextPage.addEventListener("click", () => changePage(currentPage + 1));
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
    const registrationStatuses = { REGISTERED: "已報名", CANCELLED: "已取消報名",
        PENDING_PAYMENT: "待付款／等待藍新確認", EXPIRED: "付款保留已逾時" };
    if (registration.status === "PENDING_PAYMENT") {
        text(card, "p", "付款期限：" + registration.paymentExpiresAt.replace("T", " ") + "（台北時間）");
    }
    const paymentStatuses = { PAID: "已付款", REFUND_PENDING: "待全額退款",
        REVIEW_REQUIRED: "已收款，待管理員核對", REFUNDED: "已退款", FAILED: "付款失敗" };
    if (paymentStatuses[registration.paymentStatus]) {
        text(card, "p", "付款狀態：" + paymentStatuses[registration.paymentStatus]);
    }
    const eventStatuses = { OPEN: "開放中", CLOSED: "已關閉", CANCELLED: "活動已取消" };
    text(card, "p", "報名狀態：" + (registrationStatuses[registration.status] || "未知"));
    text(card, "p", "活動狀態：" + (eventStatuses[registration.eventStatus] || "資料無法取得"));
    if (registration.eventStatus === "CANCELLED") {
        text(card, "p", "這場活動已由管理員取消，不再接受報名。");
    }
    const actions = document.createElement("div");
    actions.className = "actions";
    if (["OPEN", "CLOSED", "CANCELLED"].includes(registration.eventStatus)) {
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
        cancel.disabled = busy;
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
    searchForm.hidden = pagination.hidden = true;
    list.replaceChildren();
    message.textContent = "報名紀錄載入中……";
    try {
        const response = await DndAuth.request("/registration");
        if (response.status === 401) { requireLogin(); return; }
        if (!response.ok) {
            throw new Error(await DndAuth.errorMessage(response, "無法讀取報名紀錄。"));
        }
        const result = await response.json();
        if (!Array.isArray(result)) throw new Error("報名資料格式不正確。");
        registrations = result;
        searchForm.hidden = false;
        renderRegistrations();
    } catch (error) {
        message.textContent = error.message || "無法讀取報名紀錄，請稍後再試。";
        retry.hidden = false;
    }
}
async function cancelRegistration(registration) {
    if (busy || !await confirmRegistrationCancellation(registration.eventTitle)) return;
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
        list.querySelectorAll("button").forEach(button => { button.disabled = false; });
    }
}
retry.addEventListener("click", loadRegistrations);
loadRegistrations();