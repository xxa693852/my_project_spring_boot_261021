const message = document.getElementById("message");
const eventList = document.getElementById("event-list");

function addText(parent, tag, value, className) {
    const element = document.createElement(tag);
    element.textContent = value;
    if (className) element.className = className;
    parent.append(element);
    return element;
}

function eventTime(value) {
    return value ? value.slice(0, 16).replace("T", " ") : "未設定";
}

function eventSchedule(event) {
    const start = eventTime(event.startAt);
    const end = eventTime(event.endAt);
    if (!event.startAt || !event.endAt) return start + " 至 " + end;
    const endLabel = start.slice(0, 10) === end.slice(0, 10) ? end.slice(11) : end;
    const minutes = Math.round((Date.parse(event.endAt + "+08:00") -
        Date.parse(event.startAt + "+08:00")) / 60000);
    if (!(minutes > 0)) return start + " 至 " + endLabel;
    const hours = Math.floor(minutes / 60);
    const remainder = minutes % 60;
    const duration = [hours ? hours + " 小時" : "", remainder ? remainder + " 分鐘" : ""]
        .filter(Boolean).join(" ");
    return start + " 至 " + endLabel + "（約 " + duration + "）";
}

function registrationStatus(event) {
    if (event.status === "CANCELLED") return "已取消";
    if (Date.parse(event.registrationDeadline + "+08:00") <= Date.now()) return "報名已截止";
    if (event.canRegister) return "可報名";
    if (event.remainingSlots <= 0) return "已額滿";
    return "目前不可報名";
}

function createEventCard(event, page, registration, registrationStatusKnown) {
    const card = document.createElement("article");
    card.className = "event-card";
    card.id = "event-" + event.id;
    card.tabIndex = -1;

    const cover = document.createElement("img");
    cover.className = "event-cover";
    cover.src = eventCoverUrl(event.coverTheme);
    cover.alt = "";
    cover.loading = "lazy";

    const body = document.createElement("div");
    body.className = "event-card-body";
    const heading = document.createElement("div");
    heading.className = "event-card-heading";
    addText(heading, "h2", event.title);
    const status = registrationStatus(event);
    const badge = addText(heading, "span", status, status === "可報名" ? "event-status event-status-open" : "event-status");
    body.append(heading);
    addText(body, "p", "活動時間　" + eventSchedule(event), "event-meta event-schedule");
    addText(body, "p", "報名截止　" + eventTime(event.registrationDeadline), "event-meta");
    const locationLink = createEventMapLink(event.location, event.location);
    if (locationLink) {
        locationLink.setAttribute("aria-label", event.location + "，在 Google 地圖查看（另開分頁）");
        const locationLine = document.createElement("p");
        locationLine.className = "event-meta";
        locationLine.append(locationLink);
        body.append(locationLine);
    } else {
        addText(body, "p", "地點未提供", "event-meta");
    }
    addText(body, "p", "主持人：" + event.hostName, "event-meta");
    const slots = addText(body, "p", "剩餘名額 " + event.remainingSlots + " / " + event.capacity, "event-count");

    if (event.description?.trim()) {
        addText(body, "p", event.description.trim(), "event-preview");
    }
    const labels = document.createElement("p");
    labels.className = "event-labels";
    renderEventTags(labels, event);
    body.append(labels);

    const actions = document.createElement("div");
    actions.className = "actions";
    const link = document.createElement("a");
    link.className = "event-details-link";
    link.textContent = "查看詳情";
    link.setAttribute("aria-label", "查看詳情：" + event.title);
    link.href = "/event-detail.html?" + new URLSearchParams({ id: event.id, ...(page > 1 ? { page } : {}) });
    const register = document.createElement("button");
    const cancel = document.createElement("button");
    register.type = cancel.type = "button";
    register.textContent = "報名活動";
    cancel.textContent = "取消報名";
    const registered = document.createElement("strong");
    registered.className = "event-count";
    registered.textContent = "✓ 已報名";
    actions.append(register, registered, link, cancel);
    body.append(actions);
    const feedback = addText(body, "div", "");
    feedback.setAttribute("role", "status");
    let busy = false;
    let isRegistered = registration?.status === "REGISTERED";
    const returnUrl = pageUrl(page) + (page > 1 ? "&" : "?") + "event=" + event.id;
    const loginUrl = "/login.html?" + new URLSearchParams({ returnTo: returnUrl });

    function updateActions() {
        const status = registrationStatus(event);
        badge.textContent = status;
        badge.classList.toggle("event-status-open", status === "可報名");
        slots.textContent = "剩餘名額 " + event.remainingSlots + " / " + event.capacity;
        register.hidden = !registrationStatusKnown || isRegistered || status !== "可報名";
        registered.hidden = !isRegistered;
        cancel.hidden = !isRegistered || event.status !== "OPEN"
            || !(Date.now() < Date.parse(event.registrationDeadline + "+08:00"));
    }

    async function changeRegistration(method) {
        if (busy) return;
        if (!DndAuth.session()) {
            location.assign(loginUrl);
            return;
        }
        const removing = method === "DELETE";
        if (removing && !await confirmRegistrationCancellation(event.title)) return;
        busy = true;
        register.disabled = cancel.disabled = true;
        feedback.textContent = "處理中……";
        try {
            const path = removing ? "/registration/events/" + event.id
                : "/registration?eventId=" + event.id;
            const response = await DndAuth.request(path, { method });
            if (!response.ok) {
                if (!removing && await showRegistrationConflict(response, feedback)) return;
                const error = new Error(await DndAuth.errorMessage(response, "操作失敗，請稍後再試。"));
                error.status = response.status;
                throw error;
            }

            isRegistered = !removing;
            const remainingSlots = Math.max(0, Math.min(event.capacity,
                event.remainingSlots + (removing ? 1 : -1)));
            event = { ...event, remainingSlots, canRegister: remainingSlots > 0 };
            updateActions();
            feedback.textContent = removing ? "已取消報名。" : "報名成功。";
            if (removing && !register.hidden) {
                register.disabled = false;
                register.focus();
            } else {
                link.focus();
            }
            try {
                const latest = await fetch("/events/" + event.id, { cache: "no-store" });
                if (latest.ok) event = await latest.json();
                else if (latest.status === 404) event = { ...event, status: "CANCELLED", canRegister: false };
            } catch {
                // 保留已成功的操作結果，使用者可重新整理取得最新名額。
            }
            updateActions();
        } catch (error) {
            if (error.status === 401) {
                location.assign(loginUrl);
                return;
            }
            feedback.textContent = error.message || "操作失敗，請稍後再試。";
        } finally {
            busy = false;
            register.disabled = cancel.disabled = false;
        }
    }

    register.addEventListener("click", () => changeRegistration("POST"));
    cancel.addEventListener("click", () => changeRegistration("DELETE"));
    updateActions();
    card.append(cover, body);
    return card;
}

function pageUrl(page) {
    return page > 1 ? "/events.html?page=" + page : "/events.html";
}

async function loadEvents() {
    const requestedPage = Number(new URLSearchParams(location.search).get("page"));
    const page = Number.isSafeInteger(requestedPage) && requestedPage > 0 && requestedPage <= 2147483647
        ? requestedPage : 1;
    try {
        const response = await fetch("/events/page?page=" + page);
        if (!response.ok) throw new Error("讀取活動失敗，HTTP " + response.status);
        const result = await response.json();
        if (!Array.isArray(result.events) || !Number.isInteger(result.totalPages)) {
            throw new Error("活動列表格式錯誤");
        }
        const lastPage = Math.max(1, result.totalPages);
        if (page > lastPage) {
            location.replace(pageUrl(lastPage));
            return;
        }

        let registrations = new Map();
        let registrationStatusKnown = true;
        if (DndAuth.session()) {
            try {
                const response = await DndAuth.request("/registration", { cache: "no-store" });
                if (!response.ok) throw new Error("讀取報名狀態失敗");
                const records = await response.json();
                registrations = new Map(records.map(record => [record.eventId, record]));
            } catch {
                registrationStatusKnown = !DndAuth.session();
                if (registrationStatusKnown) DndAuth.renderNav(document.getElementById("member-nav"));
            }
        }
        for (const event of result.events) {
            eventList.append(createEventCard(event, page, registrations.get(event.id), registrationStatusKnown));
        }
        message.textContent = result.events.length === 0 ? "目前沒有公開活動。" : "";
        if (!registrationStatusKnown) {
            message.textContent = "無法讀取你的報名狀態，請重新整理，或從活動詳情操作。";
        }
        const selectedEvent = new URLSearchParams(location.search).get("event");
        if (selectedEvent) {
            const card = document.getElementById("event-" + selectedEvent);
            if (card) {
                card.focus({ preventScroll: true });
                card.scrollIntoView({ block: "start" });
            } else {
                message.textContent = "原先選擇的活動已不在這一頁，請從列表重新選擇。";
            }
        }
        if (result.totalPages > 1) {
            const previous = document.getElementById("previous-page");
            const next = document.getElementById("next-page");
            previous.hidden = page === 1;
            previous.href = pageUrl(page - 1);
            next.hidden = page === lastPage;
            next.href = pageUrl(page + 1);
            document.getElementById("page-summary").textContent = "第 " + page + " / " + lastPage + " 頁";
            previous.parentElement.hidden = false;
        }
    } catch (error) {
        message.textContent = "無法載入活動，請稍後再試。";
        console.error(error);
    }
}

loadEvents();
