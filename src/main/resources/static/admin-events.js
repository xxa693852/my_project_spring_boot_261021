const message = document.getElementById("message");
const management = document.getElementById("management");
const list = document.getElementById("event-list");
const dialog = document.getElementById("event-dialog");
const formTitle = document.getElementById("form-title");
const form = document.getElementById("event-form");
const fields = document.getElementById("event-fields");
const save = document.getElementById("save-event");
const formMessage = document.getElementById("form-message");
const retry = document.getElementById("retry");
const newEvent = document.getElementById("new-event");
const pagination = document.getElementById("event-pagination");
const previousPage = document.getElementById("previous-page");
const nextPage = document.getElementById("next-page");
const pageStatus = document.getElementById("page-status");
const coverPreview = document.getElementById("event-cover-preview");
const deliveryLog = document.getElementById("delivery-log");
const deliveryMessage = document.getElementById("delivery-message");
const deliveryList = document.getElementById("delivery-list");
const keys = ["title", "description", "hostName", "location", "startAt", "endAt", "registrationDeadline", "capacity", "beginnerFriendly", "coverTheme", "tags"];
const dateKeys = ["startAt", "endAt", "registrationDeadline"];
const pastDateMessage = "請選擇今天或之後的日期（台北時間）。";
let editingId = null, editable = true, busy = false;
let currentPage = 1;
let totalPages = 0;
let formOpener;

const datePickers = new Map();
for (const key of dateKeys) datePickers.set(key, createDatePicker(form.elements[key], form.querySelector('label[for="' + key + '"]')));

function updateDateMinimums() {
    const today = taipeiDate.format(new Date());
    for (const key of dateKeys) {
        if (editingId === null) {
            datePickers.get(key).update(today);
        }
    }
}

function requireLogin() {
    if (DndAuth.session()) return false;
    management.hidden = true;
    location.replace(DndAuth.loginUrl());
    return true;
}
function setBusy(value) {
    busy = value;
    management.querySelectorAll("button").forEach(button => { button.disabled = value; });
    fields.disabled = value || !editable;
    save.disabled = value || !editable;
    retry.disabled = value;
    previousPage.disabled = value || currentPage <= 1;
    nextPage.disabled = value || currentPage >= totalPages;
}
function clearErrors() {
    formMessage.textContent = "";
    for (const key of keys) {
        document.getElementById(key + "-error").textContent = "";
        form.elements[key].removeAttribute("aria-invalid");
        datePickers.get(key)?.time.removeAttribute("aria-invalid");
    }
}
function updateCoverPreview() {
    coverPreview.src = eventCoverUrl(form.elements.coverTheme.value);
}
form.elements.coverTheme.addEventListener("change", updateCoverPreview);

function showFieldError(key, text) {
    document.getElementById(key + "-error").textContent = text;
    const input = editingId === null && datePickers.has(key)
        ? datePickers.get(key).time : form.elements[key];
    input.setAttribute("aria-invalid", "true");
    return input;
}
function text(parent, tag, value) {
    const node = document.createElement(tag);
    node.textContent = value;
    parent.append(node);
}
function canEdit(event) {
    return event.status === "OPEN" && Date.now() < Date.parse(event.startAt + "+08:00");
}
async function checked(path, options) {
    const response = await DndAuth.request(path, options);
    if (response.status === 401) { requireLogin(); throw new Error("登入已失效，請重新登入。"); }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        const error = new Error(data.message || "操作失敗，請稍後再試。");
        error.errors = data.errors;
        throw error;
    }
    return data;
}
async function loadList(page = currentPage) {
    const result = await checked("/admin/events/page?page=" + page);
    if (!Array.isArray(result.events)) throw new Error("活動資料格式不正確。");
    const events = result.events;
    list.replaceChildren();
    if (!events.length) text(list, "p", "目前沒有活動。");
    const statuses = { OPEN: "開放中", CLOSED: "已關閉", CANCELLED: "已取消" };
    for (const event of events) {
        const card = document.createElement("article");
        text(card, "h3", event.title);
        text(card, "p", "活動狀態：" + (statuses[event.status] || event.status));
        text(card, "p", "開始時間：" + event.startAt.replace("T", " "));
        text(card, "p", "地點：" + event.location);
        text(card, "p", "已報名：" + event.registeredCount + " 人；剩餘名額：" + event.remainingSlots + " / " + event.capacity);
        const edit = document.createElement("button");
        edit.type = "button";
        edit.textContent = canEdit(event) ? "修改活動" : "查看活動資料";
        edit.dataset.eventId = String(event.id);
        edit.addEventListener("click", () => loadForm(event.id, edit));
        card.append(edit);
        if (canEdit(event)) {
            const cancel = document.createElement("button");
            cancel.type = "button";
            cancel.textContent = "取消整場活動";
            cancel.addEventListener("click", () => cancelEvent(event));
            card.append(cancel);
        }
        list.append(card);
    }
    currentPage = result.page;
    totalPages = result.totalPages;
    pagination.hidden = totalPages === 0;
    pageStatus.textContent = "第 " + currentPage + " / " + totalPages + " 頁，共 " + result.totalEvents + " 筆";
}
async function loadDeliveryLog(id) {
    deliveryList.replaceChildren();
    deliveryMessage.textContent = "讀取通知紀錄中……";
    try {
        const records = await checked("/admin/events/" + id + "/notifications");
        const statuses = {
            PENDING: "待發送", SENDING: "處理中", SENT: "服務已接受",
            FAILED: "發送失敗", SKIPPED: "未發送", UNKNOWN: "結果待確認"
        };
        const reasons = {
            LINE_NOT_CONFIGURED: "LINE 未設定，已改用 Email",
            LINE_RECIPIENT_UNAVAILABLE: "LINE 收件人無法確認，已改用 Email",
            LINE_REJECTED: "LINE 拒絕推播，已改用 Email",
            LINE_DELIVERY_UNCONFIRMED: "LINE 推播結果不明",
            EMAIL_UNAVAILABLE: "沒有可用的 Email",
            EMAIL_SEND_ERROR: "Email 寄送服務回報錯誤",
            MEMBER_UNAVAILABLE: "會員目前不可用",
            DELIVERY_NEEDS_REVIEW: "請檢查寄送紀錄"
        };
        for (const record of records) {
            const channel = record.channel === "EMAIL" ? "Email" : record.channel;
            const details = ["會員 #" + record.memberId, statuses[record.status] || record.status];
            if (channel) details.push(channel);
            if (reasons[record.resultCode]) details.push(reasons[record.resultCode]);
            text(deliveryList, "li", details.join(" · "));
        }
        deliveryMessage.textContent = records.length ? "" : "這場活動沒有通知紀錄。";
    } catch (error) {
        deliveryMessage.textContent = error.message;
    }
}
document.getElementById("reload-delivery").addEventListener("click", async () => {
    if (busy || editingId === null) return;
    setBusy(true);
    try { await loadDeliveryLog(editingId); }
    finally { setBusy(false); }
});
function openForm(event, opener) {
    clearErrors();
    form.reset();
    editingId = event?.id ?? null;
    deliveryLog.hidden = event?.status !== "CANCELLED";
    for (const picker of datePickers.values()) picker.setMode(editingId === null);
    updateDateMinimums();
    editable = !event || canEdit(event);
    formTitle.textContent = event ? "活動資料／修改" : "新增活動";
    document.getElementById("form-info").textContent = event
        ? "已報名：" + event.registeredCount + " 人。名額上限不得低於此人數。" +
          (editable ? "" : " 此活動已取消、關閉或開始，僅供查看。") : "";
    if (event) for (const key of keys) {
        if (key === "beginnerFriendly") form.elements[key].checked = event[key];
        else if (key === "tags") form.elements[key].value = (event.tags || []).join("\n");
        else if (key === "coverTheme") form.elements[key].value = eventCovers.has(event.coverTheme) ? event.coverTheme : "tavern";
        else form.elements[key].value = key.endsWith("At") || key === "registrationDeadline"
            ? event[key].slice(0, 19) : event[key];
    }
    updateCoverPreview();
    fields.disabled = !editable;
    save.hidden = !editable;
    formOpener = opener;
    dialog.showModal();
    dialog.scrollTop = 0;
    formTitle.focus({ preventScroll: true });
}
async function loadForm(id, opener) {
    if (busy) return;
    setBusy(true);
    try {
        const event = await checked("/admin/events/" + id);
        openForm(event, opener);
        if (event.status === "CANCELLED") await loadDeliveryLog(id);
    }
    catch (error) { message.textContent = error.message; }
    finally { setBusy(false); }
}
newEvent.addEventListener("click", () => {
    if (!busy) { openForm(null, newEvent); setBusy(false); }
});
document.getElementById("close-form").addEventListener("click", () => { if (!busy) dialog.close(); });
dialog.addEventListener("cancel", event => {
    if (busy) event.preventDefault();
});
dialog.addEventListener("close", () => {
    if (!busy) formOpener?.focus({ preventScroll: true });
});
form.addEventListener("submit", async event => {
    event.preventDefault();
    if (busy || !editable) return;
    clearErrors();
    const tags = form.elements.tags.value.split("\n").map(tag => tag.trim()).filter(Boolean);
    if (tags.length > 5) {
        showFieldError("tags", "最多可填寫 5 個自訂標籤").focus();
        return;
    }
    if (tags.some(tag => tag.length > 16)) {
        showFieldError("tags", "每個自訂標籤最多 16 字").focus();
        return;
    }
    if (editingId === null) {
        const today = taipeiDate.format(new Date());
        for (const [key, picker] of datePickers) {
            if (picker.sync() < today) {
                picker.update(today);
                document.getElementById(key + "-error").textContent = pastDateMessage;
                picker.time.setAttribute("aria-invalid", "true");
                picker.time.focus();
                return;
            }
        }
    }
    const body = {};
    for (const key of keys) {
        const input = form.elements[key];
        body[key] = key === "tags" ? tags
            : key === "beginnerFriendly" ? input.checked
            : key === "capacity" ? Number(input.value)
            : key.endsWith("At") || key === "registrationDeadline"
                ? (input.value.length === 16 ? input.value + ":00" : input.value) : input.value;
    }
    setBusy(true);
    formMessage.textContent = "儲存中……";
    const id = editingId;
    let saved = false;
    try {
        const data = await checked(id ? "/admin/events/" + id : "/admin/events", {
            method: id ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
        });
        saved = true;
        dialog.close();
        message.textContent = data.message || "活動已儲存。";
        await loadList(id === null ? 1 : currentPage);
    } catch (error) {
        if (saved) {
            message.textContent = "活動已儲存，但列表更新失敗：" + error.message;
            retry.hidden = false;
            return;
        }
        formMessage.textContent = error.message;
        let first;
        for (const key of Object.keys(error.errors || {})) {
            const field = key.startsWith("tags[") ? "tags" : key;
            if (keys.includes(field) && typeof error.errors[key] === "string") {
                const input = showFieldError(field, error.errors[key]);
                first ??= input;
            }
        }
        setBusy(false);
        first?.focus();
    } finally {
        setBusy(false);
        if (saved) {
            const updated = id === null ? newEvent : list.querySelector('[data-event-id="' + id + '"]');
            (updated || newEvent).focus({ preventScroll: true });
        }
    }
});
async function cancelEvent(event) {
    if (busy || !confirm("確定取消「" + event.title + "」？取消整場活動會一併取消所有已報名玩家的報名")) return;
    setBusy(true);
    try {
        const data = await checked("/admin/events/" + event.id, { method: "DELETE" });
        message.textContent = data.message || "活動已取消。";
        await loadList();
    } catch (error) { message.textContent = error.message; }
    finally { setBusy(false); }
}
async function initialize() {
    if (busy || requireLogin()) return;
    setBusy(true);
    retry.hidden = true;
    try {
        const member = await DndAuth.currentMember();
        if (member.role !== "ADMIN") {
            management.hidden = true;
            message.textContent = "無權限：僅限管理員使用活動管理功能。";
            return;
        }
        management.hidden = false;
        await loadList();
        message.textContent = "";
    } catch (error) {
        if (error.status === 401) requireLogin();
        message.textContent = error.message;
        retry.hidden = false;
    } finally { setBusy(false); }
}
async function changePage(page) {
    if (busy || page < 1 || page > totalPages) return;
    setBusy(true);
    message.textContent = "";
    try {
        await loadList(page);
    } catch (error) {
        message.textContent = error.message;
    } finally {
        setBusy(false);
        pageStatus.tabIndex = -1;
        pageStatus.focus();
    }
}
previousPage.addEventListener("click", () => changePage(currentPage - 1));
nextPage.addEventListener("click", () => changePage(currentPage + 1));
retry.addEventListener("click", initialize);
initialize();
