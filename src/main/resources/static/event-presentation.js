const eventCovers = new Map([
    ["tavern", "/tavern-cover.jpg"],
    ["forest", "/forest-cover.jpg"],
    ["dungeon", "/dungeon-cover.jpg"],
    ["castle", "/castle-cover.jpg"]
]);

function eventCoverUrl(theme) {
    return eventCovers.get(theme) || eventCovers.get("tavern");
}

function createEventMapLink(location, text) {
    if (!location?.trim()) return null;
    const url = new URL("https://www.google.com/maps/search/");
    url.search = new URLSearchParams({ api: "1", query: location.trim() });
    const link = document.createElement("a");
    link.href = url.href;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = text;
    return link;
}

function renderEventTags(container, event, leadingLabel) {
    const audience = event.beginnerFriendly ? "新手友善" : "適合有經驗的玩家";
    const tags = new Set();
    if (leadingLabel) tags.add(leadingLabel);
    tags.add(audience);
    if (Array.isArray(event.tags)) {
        for (const tag of event.tags) tags.add(tag);
    }
    container.replaceChildren();
    for (const tag of tags) {
        const label = document.createElement("span");
        label.className = "event-label";
        label.textContent = tag;
        container.append(label);
    }
}

async function showRegistrationConflict(response, container) {
    if (response.status !== 400) return false;
    const data = await response.clone().json().catch(() => null);
    if (!Array.isArray(data?.conflicts) || !data.conflicts.length) return false;

    const list = document.createElement("ul");
    for (const conflict of data.conflicts) {
        const item = document.createElement("li");
        const start = conflict.startAt.slice(0, 16).replace("T", " ");
        const end = conflict.endAt.slice(0, 16).replace("T", " ");
        item.textContent = conflict.title + "（" + start + " 至 " + end + "，台北時間）";
        list.append(item);
    }
    const link = document.createElement("a");
    link.href = "/my-registrations.html";
    link.textContent = "查看我的報名";
    container.replaceChildren(data.message, list, link);
    return true;
}
