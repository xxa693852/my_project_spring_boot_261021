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
const searchForm = document.getElementById("admin-event-search");
let activeFilters = new URLSearchParams();
const pagination = document.getElementById("event-pagination");
const previousPage = document.getElementById("previous-page");
const nextPage = document.getElementById("next-page");
const pageStatus = document.getElementById("page-status");
const coverPreview = document.getElementById("event-cover-preview");
const deliveryLog = document.getElementById("delivery-log");
const deliveryMessage = document.getElementById("delivery-message");
const deliveryList = document.getElementById("delivery-list");
const keys = ["title", "description", "hostName", "location", "startAt", "endAt", "registrationDeadline", "capacity", "registrationFee", "beginnerFriendly", "coverTheme", "tags"];
const dateKeys = ["startAt", "endAt", "registrationDeadline"];
let editingId = null, editable = true, busy = false;
let currentPage = 1;
let totalPages = 0;
let formOpener;
let moderationRefreshing = false;
const cancelDialog = document.getElementById("cancel-event-dialog");
let eventToCancel;

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
    DndAdmin.expired();
    return true;
}
function setBusy(value) {
    busy = value;
    management.querySelectorAll("button").forEach(button => { button.disabled = value; });
    searchForm.elements.keyword.disabled = value;
    searchForm.elements.editableOnly.disabled = value;
    fields.disabled = value || !editable;
    save.disabled = value || !editable;
    retry.disabled = value;
    document.getElementById("refresh-moderation-status").disabled = value || moderationRefreshing;
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
    return node;
}
function canEdit(event) {
    return event.status === "OPEN" && Date.now() < Date.parse(event.startAt + "+08:00");
}
async function checked(path, options) {
    const response = await DndAdmin.request(path, options);
    if (response.status === 401) { requireLogin(); throw new Error("登入已失效，請重新登入。"); }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        const error = new Error(data.message || "操作失敗，請稍後再試。");
        error.errors = data.errors;
        throw error;
    }
    return data;
}
async function loadList(page = currentPage, filters = activeFilters) {
    const params = new URLSearchParams(filters);
    params.set("page", page);
    const result = await checked("/admin/events/page?" + params);
    // Removing an event from the results can empty the last page.
    const lastPage = Math.max(1, result.totalPages);
    if (page > lastPage) {
        return loadList(lastPage, filters);
    }
    if (!Array.isArray(result.events)) throw new Error("活動資料格式不正確。");
    activeFilters = filters;
    const events = result.events;
    list.replaceChildren();
    if (!events.length && filters.size) text(list, "p", "沒有符合條件的活動。");
    else if (!events.length) text(list, "p", "目前沒有活動。");
    const statuses = { OPEN: "開放中", CLOSED: "已關閉", CANCELLED: "已取消" };
    for (const event of events) {
        const card = document.createElement("article");
        card.className = "admin-event-card";
        text(card, "h3", event.title);
        text(card, "p", "活動狀態：" + (statuses[event.status] || event.status));
        text(card, "p", "開始時間：" + DndTime.format(event.startAt));
        text(card, "p", "地點：" + event.location);
        const slots = text(card, "button", "已報名：" + event.registeredCount + " 人；剩餘名額：" + event.remainingSlots + " / " + event.capacity);
        slots.type = "button";
        slots.className = "admin-slot-link";
        slots.setAttribute("aria-label", event.title + "：查看報名名單與名額");
        slots.addEventListener("click", () => openRoster(event.id, slots));
        const edit = document.createElement("button");
        edit.type = "button";
        edit.textContent = "活動資料修改／收退款";
        edit.dataset.eventId = String(event.id);
        edit.addEventListener("click", () => loadForm(event.id, edit));
        const actions = document.createElement("div");
        actions.className = "actions";
        actions.append(edit);
        const manage = text(actions, "button", "管理報名");
        manage.type = "button";
        manage.addEventListener("click", () => openRoster(event.id, manage));
        const hasEnded = Date.now() >= Date.parse(event.endAt + "+08:00");
        if (event.status === "OPEN" && hasEnded) {
            text(card, "p", "活動已結束，已移出公開列表。");
        } else if (event.status === "OPEN") {
            if (event.hiddenFromPublicList) text(card, "p", "已從公開列表隱藏。");
            const visibility = document.createElement("button");
            visibility.type = "button";
            visibility.textContent = event.hiddenFromPublicList ? "恢復公開列表顯示" : "從公開列表隱藏";
            visibility.addEventListener("click", () => changeListVisibility(event));
            actions.append(visibility);
        }
        if (canEdit(event)) {
            const cancel = document.createElement("button");
            cancel.type = "button";
            cancel.textContent = "取消整場活動";
            cancel.className = "admin-danger-outline";
            cancel.addEventListener("click", () => {
                if (busy || cancelDialog.open) return;
                eventToCancel = event;
                cancelDialog.returnValue = "";
                document.getElementById("cancel-event-description").textContent =
                    "確定取消「" + event.title + "」？所有玩家的報名都會一併取消。";
                cancelDialog.showModal();
            });
            actions.append(cancel);
        }
        card.append(actions);
        list.append(card);
    }
    currentPage = result.page;
    totalPages = result.totalPages;
    pagination.hidden = totalPages < 2;
    renderPageJump(pagination, currentPage, Math.max(1, totalPages), changePage);
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
// 後台只登記已完成的退款，並保存藍新交易編號與處理說明以供對帳。
async function loadPaymentLog(eventId) {
    const list = document.getElementById("payment-log-list");
    const notice = document.getElementById("payment-log-message");
    list.replaceChildren();
    const tableRegion = list.closest(".table-scroll");
    tableRegion.hidden = true;
    notice.textContent = "讀取付款紀錄中……";
    try {
        const records = await checked("/admin/events/" + eventId + "/payments");
        let notifications = [];
        let notificationsKnown = true;
        try { notifications = await checked("/admin/events/" + eventId + "/refund-notifications"); }
        catch { notificationsKnown = false; }
        const noticeLabels = { PENDING: "待寄送", SENDING: "處理中", SENT: "服務已接受", FAILED: "缺收件人", UNKNOWN: "結果待確認", NOT_BOUND: "未綁定", NOT_CONFIGURED: "未設定" };
        const labels = { INIT: "待付款", PENDING: "待付款", PAID: "已付款",
            FAILED: "付款失敗", CANCELLED: "已取消", REFUND_PENDING: "待全額退款",
            REVIEW_REQUIRED: "已收款，待人工核對", REFUNDED: "已退款" };
        notice.textContent = records.length ? "" : "此活動沒有付款紀錄。";
        tableRegion.hidden = records.length === 0;
        for (const record of records) {
            const row = text(list, "tr", "");
            const order = text(row, "th", record.merchantOrderNo);
            order.scope = "row";
            if (record.providerTradeNo) text(order, "p", "藍新交易編號 " + record.providerTradeNo);
            text(row, "td", "NT$" + new Intl.NumberFormat("zh-TW").format(record.amount));
            text(row, "td", labels[record.status] || record.status);
            text(row, "td", DndTime.format(record.paidAt, "—"));
            text(row, "td", DndTime.format(record.refundedAt, "—"));
            const item = text(row, "td", record.refundNote || "—");
            const delivery = text(row, "td", "");
            let hasNotification = false;
            for (const notification of notifications) {
                if (notification.paymentId !== record.id) continue;
                hasNotification = true;
                text(delivery, "p", notification.channel + "：" + (noticeLabels[notification.status] || notification.status));
            }
            if (!hasNotification) delivery.textContent = notificationsKnown ? "—（無通知紀錄）" : "無法讀取，請重新整理";
            if (record.status !== "REFUND_PENDING" && record.status !== "REVIEW_REQUIRED") continue;
            const note = document.createElement("input");
            note.setAttribute("aria-label", "退款說明 " + record.merchantOrderNo);
            note.placeholder = "內部說明：金流處理日期與對帳單號（不寄給玩家）";
            note.maxLength = 255;
            item.replaceChildren(note);
            const button = text(item, "button", "登記已全額退款");
            button.type = "button";
            button.addEventListener("click", async () => {
                if (busy) return;
                if (!note.value.trim()) {
                    notice.textContent = "請先填寫退款說明。";
                    note.focus();
                    return;
                }
                if (!window.confirm("確認已在藍新後台完成全額退款？登記成功後會安排 Email 與已綁定 LINE 通知。")) return;
                setBusy(true);
                try {
                    await checked("/admin/events/" + eventId + "/payments/" + record.id + "/refund", {
                        method: "POST", headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ note: note.value.trim() })
                    });
                    await loadPaymentLog(eventId);
                } catch (error) {
                    notice.textContent = error.message;
                } finally { setBusy(false); }
            });
        }
    } catch (error) {
        notice.textContent = error.message;
    }
}

function openForm(event, opener) {
    let commentsLink = document.getElementById("admin-comments-link");
    if (!commentsLink) {
        commentsLink = document.createElement("a");
        commentsLink.id = "admin-comments-link";
        commentsLink.textContent = "管理活動留言";
        document.getElementById("form-info").after(commentsLink);
    }
    commentsLink.hidden = !event;
    if (event) commentsLink.href = "/event-detail.html?id=" + event.id + "&admin=1";
    document.getElementById("payment-log").hidden = !event;
    clearErrors();
    form.reset();
    editingId = event?.id ?? null;
    deliveryLog.hidden = event?.status !== "CANCELLED";
    for (const picker of datePickers.values()) picker.setMode(editingId === null);
    updateDateMinimums();
    editable = !event || canEdit(event);
    formTitle.textContent = event ? "活動資料修改／收退款" : "新增活動";
    document.getElementById("form-info").textContent = event
        ? "已報名：" + event.registeredCount + " 人。名額上限不得低於此人數。" +
          (editable ? "" : " 此活動資料僅供查看；仍可處理收退款。") : "";
    if (event) for (const key of keys) {
        if (key === "beginnerFriendly") form.elements[key].checked = event[key];
        else if (key === "tags") form.elements[key].value = (event.tags || []).join("\n");
        else if (key === "coverTheme") form.elements[key].value = eventCovers.has(event.coverTheme) ? event.coverTheme : "tavern";
        else form.elements[key].value = key.endsWith("At") || key === "registrationDeadline"
            ? event[key].slice(0, 19) : event[key];
    }
    DndCounters.refresh(form);
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
        await loadPaymentLog(id);
        if (event.status === "CANCELLED") await loadDeliveryLog(id);
    }
    catch (error) { message.textContent = error.message; }
    finally { setBusy(false); }
}
newEvent.addEventListener("click", () => {
    if (!busy) { openForm(null, newEvent); setBusy(false); }
});
document.getElementById("close-form").addEventListener("click", () => { if (!busy) dialog.close(); });
document.getElementById("event-close-top").addEventListener("click", () => { if (!busy) dialog.close(); });
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
    if (editingId === null) for (const picker of datePickers.values()) picker.sync();
    const body = {};
    for (const key of keys) {
        const input = form.elements[key];
        body[key] = key === "tags" ? tags
            : key === "beginnerFriendly" ? input.checked
            : (key === "capacity" || key === "registrationFee") ? (input.value === "" ? null : Number(input.value))
            : key.endsWith("At") || key === "registrationDeadline"
                ? (input.value.length === 16 ? input.value + ":00" : input.value) : input.value;
    }
    const errors = DndEventRules.validate(body, editingId === null);
    let firstInvalid;
    for (const key of Object.keys(errors)) {
        const input = showFieldError(key, errors[key]);
        if (!firstInvalid) firstInvalid = input;
    }
    if (firstInvalid) {
        formMessage.textContent = "資料驗證失敗";
        firstInvalid.focus();
        return;
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
        loadModerationStatus();
        if (saved) {
            const updated = id === null ? newEvent : list.querySelector('[data-event-id="' + id + '"]');
            (updated || newEvent).focus({ preventScroll: true });
        }
    }
});
async function cancelEvent(event) {
    if (busy) return;
    setBusy(true);
    try {
        const data = await checked("/admin/events/" + event.id, { method: "DELETE" });
        message.textContent = data.message || "活動已取消。";
        await loadList();
    } catch (error) { message.textContent = error.message; }
    finally { setBusy(false); }
}
cancelDialog.addEventListener("close", () => {
    const event = eventToCancel;
    eventToCancel = undefined;
    if (event && cancelDialog.returnValue === "cancel") cancelEvent(event);
});

async function changeListVisibility(event) {
    if (busy) return;
    setBusy(true);
    try {
        const hidden = !event.hiddenFromPublicList;
        const data = await checked("/admin/events/" + event.id + "/list-visibility?hidden=" + hidden,
            { method: "PUT" });
        await loadList();
        message.textContent = data.message;
    } catch (error) { message.textContent = error.message; }
    finally {
        setBusy(false);
        list.querySelector('[data-event-id="' + event.id + '"]')?.focus({ preventScroll: true });
    }
}

async function loadModerationStatus() {
    // 防止連點重送；即使狀態文字相同，也顯示本次讀取結果與時間。
    if (moderationRefreshing) return;
    moderationRefreshing = true;
    const notice = document.getElementById("moderation-status");
    const refresh = document.getElementById("refresh-moderation-status");
    refresh.disabled = true;
    refresh.textContent = "更新中……";
    notice.setAttribute("aria-busy", "true");
    notice.textContent = "正在重新讀取審核狀態……";
    try {
        const status = await checked("/admin/moderation/status");
        const updatedAt = new Intl.DateTimeFormat("zh-TW", {
            timeZone: "Asia/Taipei", hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23"
        }).format(new Date());
        notice.textContent = status.message + "（已更新：" + updatedAt + "，台北時間）";
        refresh.textContent = "✓ 已更新，再次整理";
    } catch {
        notice.textContent = "本次更新失敗，暫時無法讀取審核狀態，請稍後再試。";
        refresh.textContent = "更新失敗，重新整理";
    } finally {
        moderationRefreshing = false;
        notice.removeAttribute("aria-busy");
        refresh.disabled = busy;
    }
}
document.getElementById("refresh-moderation-status").addEventListener("click", loadModerationStatus);


async function initialize() {
    if (busy) return;
    setBusy(true);
    retry.hidden = true;
    try {
        const member = await DndAdmin.currentMember();
        if (member.role !== "ADMIN") {
            management.hidden = true;
            message.textContent = "無權限：僅限管理員使用活動管理功能。";
            return;
        }
        management.hidden = false;
        await loadList();
        await loadModerationStatus();
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
async function applySearch() {
    if (busy) return;
    const filters = new URLSearchParams();
    const keyword = searchForm.elements.keyword.value.trim();
    if (keyword) filters.set("keyword", keyword);
    if (searchForm.elements.editableOnly.checked) filters.set("editableOnly", "true");
    setBusy(true);
    message.textContent = "";
    try {
        await loadList(1, filters);
    } catch (error) {
        searchForm.elements.keyword.value = activeFilters.get("keyword") || "";
        searchForm.elements.editableOnly.checked = activeFilters.has("editableOnly");
        message.textContent = error.message;
    } finally {
        setBusy(false);
    }
}
searchForm.addEventListener("submit", event => {
    event.preventDefault();
    applySearch();
});
searchForm.elements.editableOnly.addEventListener("change", applySearch);
document.getElementById("clear-admin-event-search").addEventListener("click", () => {
    searchForm.reset();
    applySearch();
});
previousPage.addEventListener("click", () => changePage(currentPage - 1));
nextPage.addEventListener("click", () => changePage(currentPage + 1));
retry.addEventListener("click", initialize);
initialize();

// 名單與個別取消共用既有後台 Session / CSRF request。
const rosterDialog = document.getElementById("roster-dialog");
const rosterMessage = document.getElementById("roster-message");
const rosterCancelForm = document.getElementById("roster-cancel-form");
let rosterEventId = null, rosterTargetId = null, rosterBusy = false, rosterOpener, rosterSelectedRow;
function clearRosterSelection() {
    if (rosterSelectedRow) rosterSelectedRow.classList.remove("roster-selected");
    rosterSelectedRow = null;
}
const rosterStatusLabels = { REGISTERED: "已報名", CANCELLED: "已取消", PENDING_PAYMENT: "待付款" };
const rosterPaymentLabels = { INIT: "待付款", PENDING: "待付款", PAID: "已付款", CANCELLED: "已取消",
    FAILED: "付款失敗", REFUND_PENDING: "待全額退款", REFUNDED: "已退款", REVIEW_REQUIRED: "已收款，待人工核對" };
function setRosterBusy(value) {
    rosterBusy = value;
    rosterDialog.querySelectorAll("button, textarea").forEach(input => { input.disabled = value; });
}
async function renderRoster() {
    const data = await checked("/admin/events/" + rosterEventId + "/registrations");
    document.getElementById("roster-title").textContent = "管理報名";
    document.getElementById("roster-event-name").textContent = data.title;
    document.getElementById("roster-summary").textContent = "已報名：" + data.registeredCount + " 人；待付款保留：" +
        data.heldCount + " 人；剩餘名額：" + data.remainingSlots + " / " + data.capacity;
    const rows = document.getElementById("roster-list");
    rows.replaceChildren();
    if (!data.entries.length) {
        const empty = text(rows, "tr", "");
        text(empty, "td", "目前沒有報名紀錄。").colSpan = 5;
    }
    for (const entry of data.entries) {
        const row = document.createElement("tr");
        text(row, "td", entry.name + "／" + entry.account);
        text(row, "td", DndTime.format(entry.registeredAt, "—"));
        let status = rosterStatusLabels[entry.status] || entry.status;
        if (entry.status === "PENDING_PAYMENT") {
            status += Date.now() >= Date.parse(entry.paymentExpiresAt + "+08:00") ? "（保留已逾期）" : "（保留中）";
        }
        text(row, "td", status);
        text(row, "td", entry.paymentStatus ? (rosterPaymentLabels[entry.paymentStatus] || entry.paymentStatus) : "無付款訂單（免費報名）");
        const action = text(row, "td", DndTime.format(entry.cancelledAt, "—"));
        if (entry.canCancel) {
            const cancel = text(action, "button", "取消報名並釋出名額");
            cancel.type = "button";
            cancel.className = "admin-danger-outline";
            cancel.addEventListener("click", () => {
                if (rosterBusy) return;
                clearRosterSelection();
                rosterSelectedRow = row;
                row.classList.add("roster-selected");
                rosterTargetId = entry.id;
                rosterCancelForm.hidden = false;
                rosterCancelForm.reset();
                DndCounters.refresh(rosterCancelForm);
                rosterMessage.textContent = "";
                document.getElementById("roster-target").textContent = "確定取消「" + entry.name + "／" + entry.account + "」的報名？已付款者將列為待全額退款。";
                document.getElementById("roster-reason").focus();
            });
        }
        rows.append(row);
    }
    const history = document.getElementById("roster-history");
    history.replaceChildren();
    if (!data.cancellations.length) {
        const empty = text(history, "tr", "");
        text(empty, "td", "尚無管理員個別取消紀錄。").colSpan = 7;
    }
    const mailStatuses = new Map((data.notifications || []).map(item => [item.cancellationId, item]));
    const mailLabels = { PENDING: "Email 待寄送", SENDING: "Email 處理中（若長時間未更新請人工確認）", SENT: "郵件服務已接受", FAILED: "無收件信箱，未寄出", UNKNOWN: "寄送結果待人工確認" };
    const lineLabels = { PENDING: "待推播", SENDING: "處理中（長時間未更新請人工確認）", SENT: "LINE 服務已接受",
        NOT_BOUND: "未綁定 LINE", NOT_CONFIGURED: "LINE 服務未設定", RECIPIENT_ERROR: "無法確認 LINE 收件人",
        REJECTED: "LINE 拒絕推播", UNKNOWN: "推播結果待人工確認" };
    for (const record of data.cancellations) {
        const row = document.createElement("tr");
        text(row, "td", DndTime.format(record.cancelledAt, "—"));
        text(row, "td", "#" + record.memberId);
        text(row, "td", "#" + record.operatorId);
        text(row, "td", DndTime.format(record.registeredAt, "—"));
        text(row, "td", record.reason);
        const notification = mailStatuses.get(record.id);
        text(row, "td", mailLabels[notification?.status] || "無通知紀錄");
        text(row, "td", lineLabels[notification?.lineStatus] || "舊紀錄（未啟用 LINE 通知）");
        history.append(row);
    }
}
async function openRoster(eventId, opener) {
    if (busy || rosterDialog.open) return;
    clearRosterSelection();
    rosterEventId = eventId;
    rosterOpener = opener;
    rosterCancelForm.hidden = true;
    rosterTargetId = null;
    document.getElementById("roster-list").replaceChildren();
    document.getElementById("roster-history").replaceChildren();
    document.getElementById("roster-summary").textContent = "";
    document.getElementById("roster-event-name").textContent = "";
    document.getElementById("roster-title").textContent = "管理報名";
    rosterMessage.textContent = "讀取名單中……";
    rosterDialog.showModal();
    setRosterBusy(true);
    try { await renderRoster(); rosterMessage.textContent = ""; }
    catch (error) { rosterMessage.textContent = error.message; }
    finally { setRosterBusy(false); }
}
const rosterFinalDialog = document.getElementById("roster-final-dialog");
let resolveRosterConfirmation;
function askRosterConfirmation(reason) {
    document.getElementById("roster-final-description").textContent =
        "活動：「" + document.getElementById("roster-event-name").textContent + "」\n" +
        document.getElementById("roster-target").textContent;
    document.getElementById("roster-final-reason").textContent = reason;
    rosterFinalDialog.returnValue = "";
    return new Promise(resolve => {
        resolveRosterConfirmation = resolve;
        rosterFinalDialog.showModal();
        document.getElementById("roster-final-keep").focus();
    });
}
document.getElementById("roster-final-keep").addEventListener("click", () => rosterFinalDialog.close("keep"));
document.getElementById("roster-final-confirm").addEventListener("click", () => rosterFinalDialog.close("confirm"));
rosterFinalDialog.addEventListener("close", () => {
    if (resolveRosterConfirmation) {
        const resolve = resolveRosterConfirmation;
        resolveRosterConfirmation = null;
        resolve(rosterFinalDialog.returnValue === "confirm");
    }
});
rosterCancelForm.addEventListener("submit", async event => {
    event.preventDefault();
    if (rosterBusy || rosterTargetId === null) return;
    const reason = document.getElementById("roster-reason").value.trim();
    if (!reason) { rosterMessage.textContent = "請填寫取消原因。"; return; }
    setRosterBusy(true);
    let completed = false;
    try {
        if (!await askRosterConfirmation(reason)) return;
        const result = await checked("/admin/events/" + rosterEventId + "/registrations/" + rosterTargetId + "/cancel", {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ reason })
        });
        completed = true;
        clearRosterSelection();
        rosterTargetId = null;
        rosterCancelForm.hidden = true;
        rosterMessage.textContent = result.message;
        await renderRoster();
        await loadList();
    } catch (error) {
        rosterMessage.textContent = (completed ? "取消已完成，但畫面更新失敗，請重新整理名單。" : "取消未確認成功，請先重新整理名單確認狀態。") + " " + error.message;
    } finally {
        setRosterBusy(false);
        if (!rosterCancelForm.hidden) document.getElementById("roster-reason").focus();
    }
});
document.getElementById("roster-keep").addEventListener("click", () => {
    clearRosterSelection();
    rosterTargetId = null;
    rosterCancelForm.hidden = true;
    document.getElementById("roster-refresh").focus();
});
document.getElementById("roster-close").addEventListener("click", () => rosterDialog.close());
document.getElementById("roster-close-top").addEventListener("click", () => { if (!rosterBusy) rosterDialog.close(); });
rosterDialog.addEventListener("cancel", event => { if (rosterBusy) event.preventDefault(); });
rosterDialog.addEventListener("close", () => {
    clearRosterSelection();
    if (rosterOpener?.isConnected) rosterOpener.focus();
    else document.getElementById("new-event").focus();
});
document.getElementById("roster-refresh").addEventListener("click", async () => {
    if (rosterBusy) return;
    setRosterBusy(true);
    clearRosterSelection();
    rosterTargetId = null;
    rosterCancelForm.hidden = true;
    try { await renderRoster(); await loadList(); rosterMessage.textContent = "名單已更新。"; }
    catch (error) { rosterMessage.textContent = error.message; }
    finally { setRosterBusy(false); }
});

document.getElementById("reload-payment-log").addEventListener("click", async () => {
    if (busy || editingId === null) return;
    setBusy(true);
    try { await loadPaymentLog(editingId); } finally { setBusy(false); }
});
