const message = document.getElementById("message");
const eventList = document.getElementById("event-list");

function addText(parent, tag, value, className) {
    const element = document.createElement(tag);
    element.textContent = value;
    if (className) element.className = className;
    parent.append(element);
}

function createEventCard(event) {
    const card = document.createElement("article");
    card.className = "event-card";

    const cover = document.createElement("img");
    cover.className = "event-cover";
    cover.src = eventCoverUrl(event.coverTheme);
    cover.alt = "";
    cover.loading = "lazy";

    const body = document.createElement("div");
    body.className = "event-card-body";
    addText(body, "h2", event.title);
    addText(body, "p", event.startAt ? event.startAt.replace("T", " ") : "時間未設定", "event-meta");
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
    addText(body, "p", "剩餘名額 " + event.remainingSlots + " / " + event.capacity, "event-count");

    const labels = document.createElement("p");
    labels.className = "event-labels";
    renderEventTags(labels, event, event.canRegister ? "目前可報名" : "目前不可報名");
    body.append(labels);

    const actions = document.createElement("div");
    actions.className = "actions";
    const link = document.createElement("a");
    link.className = "button-link";
    link.textContent = "查看詳情";
    link.setAttribute("aria-label", "查看詳情：" + event.title);
    link.href = "/event-detail.html?" + new URLSearchParams({ id: event.id });
    actions.append(link);
    body.append(actions);
    card.append(cover, body);
    return card;
}

async function loadEvents() {
    try {
        const response = await fetch("/events");
        if (!response.ok) throw new Error("讀取活動失敗，HTTP " + response.status);
        const events = await response.json();
        if (!Array.isArray(events)) throw new Error("活動列表格式錯誤");

        eventList.replaceChildren();
        for (const event of events) {
            eventList.append(createEventCard(event));
        }
        message.textContent = events.length === 0 ? "目前沒有開放的活動。" : "";
    } catch (error) {
        message.textContent = "無法載入活動，請稍後再試。";
        console.error(error);
    }
}

loadEvents();
