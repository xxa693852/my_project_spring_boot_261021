async function updateLineNotificationPrompt() {
    const prompt = document.getElementById("line-notification-prompt");
    if (!prompt || !DndAuth.session()) return;
    try {
        const response = await DndAuth.request("/members/me/line-notifications");
        if (!response.ok) return;
        const status = await response.json();
        prompt.hidden = !status.notificationsEnabled || !status.lineAvailable || status.linked;
    } catch {
        // 通知設定讀取失敗時，報名流程仍可照常操作。
    }
}

if (document.querySelector("[data-load-notification-status]")) updateLineNotificationPrompt();
