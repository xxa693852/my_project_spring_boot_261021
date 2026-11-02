const message = document.getElementById("message");
const management = document.getElementById("management");
const list = document.getElementById("event-list");
const form = document.getElementById("event-form");
const fields = document.getElementById("event-fields");
const save = document.getElementById("save-event");
const formMessage = document.getElementById("form-message");
const retry = document.getElementById("retry");
const keys = ["title", "description", "hostName", "location", "startAt", "endAt", "registrationDeadline", "capacity", "beginnerFriendly"];
let editingId = null, editable = true, busy = false;
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
}
function clearErrors() {
    formMessage.textContent = "";
    for (const key of keys) {
        document.getElementById(key + "-error").textContent = "";
        form.elements[key].removeAttribute("aria-invalid");
    }
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
async function loadList() {
    const events = await checked("/admin/events");
    if (!Array.isArray(events)) throw new Error("活動資料格式不正確。");
    list.replaceChildren();
    if (!events.length) text(list, "p", "目前沒有活動。");
    const statuses = { OPEN: "開放中", CLOSED: "已關閉", CANCELLED: "已取消" };
    for (const event of events) {
        const card = document.createElement("article");
        text(card, "h3", event.title);
        text(card, "p", "活動狀態：" + (statuses[event.status] || event.status));
        text(card, "p", "開始時間：" + event.startAt.replace("T", " "));
        text(card, "p", "地點：" + event.location);
        text(card, "p", "有效報名：" + event.registeredCount + " 人；剩餘名額：" + event.remainingSlots + " / " + event.capacity);
        const edit = document.createElement("button");
        edit.type = "button";
        edit.textContent = canEdit(event) ? "修改活動" : "查看活動資料";
        edit.addEventListener("click", () => loadForm(event.id));
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
}
function openForm(event) {
    clearErrors();
    form.reset();
    editingId = event?.id ?? null;
    editable = !event || canEdit(event);
    document.getElementById("form-title").textContent = event ? "活動資料／修改" : "新增活動";
    document.getElementById("form-info").textContent = event
        ? "有效報名：" + event.registeredCount + " 人。名額上限不得低於此人數。" +
          (editable ? "" : " 此活動已取消、關閉或開始，僅供查看。") : "";
    if (event) for (const key of keys) {
        if (key === "beginnerFriendly") form.elements[key].checked = event[key];
        else form.elements[key].value = key.endsWith("At") || key === "registrationDeadline"
            ? event[key].slice(0, 19) : event[key];
    }
    fields.disabled = !editable;
    save.hidden = !editable;
    form.hidden = false;
    document.getElementById("form-title").focus({ preventScroll: true });
    form.scrollIntoView({
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth",
        block: "start"
    });
}
async function loadForm(id) {
    if (busy) return;
    setBusy(true);
    try { openForm(await checked("/admin/events/" + id)); }
    catch (error) { message.textContent = error.message; }
    finally { setBusy(false); }
}
document.getElementById("new-event").addEventListener("click", () => {
    if (!busy) { openForm(null); setBusy(false); }
});
document.getElementById("close-form").addEventListener("click", () => { if (!busy) form.hidden = true; });
form.addEventListener("submit", async event => {
    event.preventDefault();
    if (busy || !editable) return;
    clearErrors();
    const body = {};
    for (const key of keys) {
        const input = form.elements[key];
        body[key] = key === "beginnerFriendly" ? input.checked
            : key === "capacity" ? Number(input.value)
            : key.endsWith("At") || key === "registrationDeadline"
                ? (input.value.length === 16 ? input.value + ":00" : input.value) : input.value;
    }
    setBusy(true);
    formMessage.textContent = "儲存中……";
    try {
        const id = editingId;
        const data = await checked(id ? "/admin/events/" + id : "/admin/events", {
            method: id ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
        });
        form.hidden = true;
        message.textContent = data.message || "活動已儲存。";
        await loadList();
    } catch (error) {
        formMessage.textContent = error.message;
        if (form.hidden) message.textContent = "活動已儲存，但列表更新失敗：" + error.message;
        let first;
        for (const key of keys) if (typeof error.errors?.[key] === "string") {
            document.getElementById(key + "-error").textContent = error.errors[key];
            form.elements[key].setAttribute("aria-invalid", "true");
            first ??= form.elements[key];
        }
        setBusy(false);
        first?.focus();
    } finally { setBusy(false); }
});
async function cancelEvent(event) {
    if (busy || !confirm("確定取消「" + event.title + "」？取消整場活動會一併取消所有有效報名")) return;
    setBusy(true);
    try {
        const data = await checked("/admin/events/" + event.id, { method: "DELETE" });
        message.textContent = data.message || "活動已取消。";
        if (editingId === event.id) form.hidden = true;
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
retry.addEventListener("click", initialize);
initialize();