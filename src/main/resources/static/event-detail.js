const message = document.getElementById("message");
const detail = document.getElementById("event-detail");
const section = document.getElementById("registration");
const statusText = document.getElementById("registration-status");
const actionMessage = document.getElementById("action-message");
const register = document.getElementById("register-button");
const cancel = document.getElementById("cancel-button");
const login = document.getElementById("login-link");
const eventId = new URLSearchParams(location.search).get("id");
let busy = false;
function addText(tag, text, className) {
    const element = document.createElement(tag);
    element.textContent = text;
    if (className) element.className = className;
    detail.append(element);
}
function time(value) { return value ? value.replace("T", " ") : "未設定"; }
async function loadEvent() {
    register.hidden = cancel.hidden = login.hidden = true;
    section.hidden = true;
    if (!/^[1-9]\d*$/.test(eventId || "")) {
        message.textContent = "活動編號缺少或格式不正確。";
        return;
    }
    try {
        const response = await fetch("/events/" + eventId);
        if (!response.ok) detail.hidden = true;
        if (!response.ok) throw new Error(response.status === 404
            ? "找不到這個活動，活動可能已取消。" : "無法載入活動，請稍後再試。");
        const event = await response.json();
        detail.replaceChildren();
        addText("h1", event.title);
        addText("p", "主持人：" + event.hostName);
        addText("p", "地點：" + event.location);
        addText("p", "開始時間：" + time(event.startAt));
        addText("p", "結束時間：" + time(event.endAt));
        addText("p", "報名截止：" + time(event.registrationDeadline));
        addText("p", "名額上限：" + event.capacity + " 人");
        addText("p", "已報名：" + event.registeredCount + " 人");
        addText("p", "剩餘名額：" + event.remainingSlots + " / " + event.capacity);
        addText("p", event.beginnerFriendly ? "新手友善" : "適合有經驗的玩家");
        addText("p", event.canRegister ? "目前可報名" : "目前不可報名");
        addText("h2", "活動介紹");
        addText("p", event.description, "description");
        detail.hidden = false;
        message.textContent = "";
        section.hidden = false;
        if (!DndAuth.session()) {
            statusText.textContent = "登入後可查看自己的報名狀態。";
            login.href = DndAuth.loginUrl();
            login.hidden = false;
            return;
        }
        statusText.textContent = "報名狀態載入中……";
        const own = await DndAuth.request("/registration/events/" + eventId);
        if (own.status === 401) {
            DndAuth.renderNav(document.getElementById("member-nav"));
            statusText.textContent = "登入已失效，請重新登入。";
            login.href = DndAuth.loginUrl();
            login.hidden = false;
            return;
        }
        if (!own.ok && own.status !== 404) {
            throw new Error(await DndAuth.errorMessage(own, "無法取得報名狀態。"));
        }
        const registration = own.status === 404 ? null : await own.json();
        if (registration?.status === "REGISTERED") {
            statusText.textContent = "你已報名這場活動。";
            // Server timestamps are Taipei local time.
            const deadline = Date.parse(event.registrationDeadline + "+08:00");
            cancel.hidden = !(event.status === "OPEN" && Date.now() < deadline);
            if (cancel.hidden) statusText.textContent += " 已截止，無法自行取消。";
        } else {
            statusText.textContent = registration?.status === "CANCELLED"
                ? "你已取消報名。" : "你尚未報名這場活動。";
            register.hidden = !event.canRegister;
        }
    } catch (error) {
        message.textContent = error.message || "無法載入活動，請稍後再試。";
    }
}
async function changeRegistration(method) {
    if (busy) return;
    if (method === "DELETE" && !confirm("確定要取消這場活動的報名嗎？")) return;
    busy = true;
    register.disabled = cancel.disabled = true;
    actionMessage.textContent = "處理中……";
    try {
        const path = method === "POST" ? "/registration?eventId=" + eventId
            : "/registration/events/" + eventId;
        const response = await DndAuth.request(path, { method });
        if (!response.ok) {
            throw new Error(await DndAuth.errorMessage(response,
                response.status === 401 ? "登入已失效，請重新登入。" : "操作失敗，請稍後再試。"));
        }
        actionMessage.textContent = method === "POST" ? "報名成功。" : "已取消報名。";
    } catch (error) {
        actionMessage.textContent = error.message;
    } finally {
        await loadEvent();
        register.disabled = cancel.disabled = false;
        busy = false;
    }
}
register.addEventListener("click", () => changeRegistration("POST"));
cancel.addEventListener("click", () => changeRegistration("DELETE"));
loadEvent();