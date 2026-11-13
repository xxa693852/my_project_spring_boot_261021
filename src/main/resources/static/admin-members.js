const statusLine = document.getElementById("member-status");
const management = document.getElementById("member-management");
const results = document.getElementById("member-results");
const previous = document.getElementById("member-previous");
const next = document.getElementById("member-next");
const pageLabel = document.getElementById("member-page");
const dialog = document.getElementById("member-dialog");
const details = document.getElementById("member-details");
const detailStatus = document.getElementById("member-detail-status");
const suspension = document.getElementById("member-suspension");
const operation = document.getElementById("member-operation");
const untilGroup = document.getElementById("member-until-group");
const until = document.getElementById("member-until");
const deadlinePicker = createDatePicker(until, document.querySelector('label[for="member-until"]'));
const reason = document.getElementById("member-reason");
const submit = suspension.querySelector('button[type="submit"]');

let page = 1;
let query = "";
let selectedId;
let busy = false;
let saving = false;
let detailRequest = 0;

function addText(parent, tag, text) {
    const element = document.createElement(tag);
    element.textContent = text;
    parent.append(element);
    return element;
}

function dateText(value) {
    return value ? value.slice(0, 16).replace("T", " ") : "—";
}

function memberStatus(member) {
    if (member.status !== "ACTIVE") return "已停用";
    if (!member.suspended) return "正常";
    return member.suspendedUntil ? "停權至 " + dateText(member.suspendedUntil) : "永久停權";
}

async function request(path, options) {
    const response = await DndAuth.request("/admin/members" + path, { cache: "no-store", ...options });
    if (response.status === 401) location.assign(DndAuth.loginUrl());
    if (response.status === 403) throw new Error("只有管理員可以管理會員。");
    if (!response.ok) throw new Error(await DndAuth.errorMessage(response, "操作失敗，請稍後再試。"));
    return response.json();
}

async function loadPage(targetPage) {
    const data = await request("?" + new URLSearchParams({ query, page: targetPage }));
    page = data.page;
    results.replaceChildren();
    for (const member of data.items) {
        const card = document.createElement("article");
        addText(card, "h2", member.name);
        addText(card, "p", member.account + " · " + member.email);
        addText(card, "p", memberStatus(member) + " · " + (member.role === "ADMIN" ? "管理員" : "一般會員"));
        const button = addText(card, "button", "查看詳情");
        button.setAttribute("aria-label", "查看會員 " + member.account + " 的詳情");
        button.addEventListener("click", () => openDetails(member.id));
        results.append(card);
    }
    previous.disabled = page <= 1;
    next.disabled = page >= data.totalPages;
    pageLabel.textContent = data.totalPages ? "第 " + page + " / " + data.totalPages + " 頁" : "";
    statusLine.textContent = data.totalElements ? "共 " + data.totalElements + " 位會員" : "沒有符合條件的會員。";
    management.hidden = false;
}

async function refresh(targetPage) {
    if (busy) return;
    busy = true;
    try {
        await loadPage(targetPage);
    } catch (error) {
        statusLine.textContent = error.message;
    } finally {
        busy = false;
    }
}

function syncOperation() {
    untilGroup.hidden = operation.value !== "temporary";
    deadlinePicker.time.disabled = untilGroup.hidden;
    if (!untilGroup.hidden) deadlinePicker.update(taipeiDate.format(new Date()));
}

async function renderDetails(id) {
    const requestId = ++detailRequest;
    const data = await request("/" + id);
    if (requestId !== detailRequest || !dialog.open) return;
    const member = data.member;
    document.getElementById("member-title").textContent = member.name + " 的會員詳情";
    details.replaceChildren();
    addText(details, "p", "帳號：" + member.account + " · Email：" + member.email);
    addText(details, "p", "狀態：" + memberStatus(member));
    addText(details, "p", "加入時間：" + dateText(member.createdAt));
    addText(details, "h3", "報名紀錄");
    const history = addText(details, "ul", "");
    for (const entry of data.registrations) {
        const status = entry.status === "REGISTERED" ? "已報名" : "已取消";
        addText(history, "li", (entry.eventTitle || "場次 #" + entry.eventId) + " · " + status
            + " · " + dateText(entry.startAt));
    }
    if (!history.childElementCount) addText(history, "li", "沒有報名紀錄。");

    addText(details, "h3", "停權紀錄");
    const actions = addText(details, "ul", "");
    for (const entry of data.actions) {
        let description = "解除停權";
        if (entry.action === "SUSPEND") {
            description = entry.suspendedUntil ? "停權至 " + dateText(entry.suspendedUntil) : "永久停權";
        }
        const notice = { PENDING: "待寄送", SENDING: "處理中", SENT: "已送出", FAILED: "寄送失敗", UNKNOWN: "待確認", SKIPPED: "已略過" };
        const delivery = entry.notificationStatus ? " · 通知：" + notice[entry.notificationStatus]
            + (entry.notificationChannel ? "（" + entry.notificationChannel + "）" : "") : "";
        addText(actions, "li", dateText(entry.actedAt) + " · 管理員 #" + entry.actorId
            + " · " + description + " · " + entry.reason + delivery);
    }
    if (!actions.childElementCount) addText(actions, "li", "沒有停權紀錄。");

    suspension.reset();
    deadlinePicker.setMode(true);
    operation.replaceChildren(new Option("限期停權", "temporary"), new Option("永久停權", "permanent"));
    if (member.suspended) operation.add(new Option("提前解除停權", "lift"), 0);
    operation.selectedIndex = 0;
    syncOperation();
    suspension.hidden = member.role !== "MEMBER" || member.status !== "ACTIVE";
}

async function openDetails(id) {
    selectedId = id;
    document.getElementById("member-title").textContent = "會員詳情";
    details.replaceChildren();
    suspension.hidden = true;
    detailStatus.textContent = "讀取中……";
    dialog.showModal();
    try {
        await renderDetails(id);
        detailStatus.textContent = "";
    } catch (error) {
        if (selectedId === id && dialog.open) detailStatus.textContent = error.message;
    }
}

document.getElementById("member-search").addEventListener("submit", event => {
    event.preventDefault();
    if (busy) return;
    query = document.getElementById("member-query").value.trim();
    refresh(1);
});
previous.addEventListener("click", () => refresh(page - 1));
next.addEventListener("click", () => refresh(page + 1));
const close = document.getElementById("member-close");
close.addEventListener("click", () => dialog.close());
dialog.addEventListener("cancel", event => {
    if (saving) event.preventDefault();
});
operation.addEventListener("change", syncOperation);
suspension.addEventListener("submit", async event => {
    event.preventDefault();
    if (saving) return;
    saving = true;
    const action = operation.value;
    submit.disabled = close.disabled = true;
    let saved = false;
    try {
        await request("/" + selectedId + "/suspension", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                action: action === "lift" ? "LIFT" : "SUSPEND",
                until: action === "temporary" ? until.value : null,
                reason: reason.value.trim()
            })
        });
        saved = true;
        suspension.hidden = true;
        await renderDetails(selectedId);
        await refresh(page);
        detailStatus.textContent = "已更新會員停權設定。";
    } catch (error) {
        detailStatus.textContent = saved ? "已儲存，請重新開啟會員詳情確認。" : error.message;
    } finally {
        saving = false;
        submit.disabled = close.disabled = false;
    }
});

if (DndAuth.session()) refresh(1);
else location.replace(DndAuth.loginUrl());
