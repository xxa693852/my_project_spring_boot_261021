(() => {
    const endpoint = "/members/me/line-notifications";
    const preference = document.getElementById("line-preference");
    const result = document.getElementById("line-message");
    const link = document.getElementById("line-link");
    const unlink = document.getElementById("line-unlink");
    const callbackUrl = new URL(location.href);
    const hasCallback = callbackUrl.searchParams.has("state")
        && (callbackUrl.searchParams.has("code") || callbackUrl.searchParams.has("error"));
    if (hasCallback) {
        history.replaceState(null, "", "/member-settings.html");
    }

    async function request(path, options) {
        const response = await DndAuth.request(endpoint + path, options);
        if (!response.ok) {
            throw new Error(await DndAuth.errorMessage(response, "無法處理通知設定，請稍後再試。"));
        }
        return response.json();
    }

    function render(status) {
        link.hidden = !status.notificationsEnabled || !status.lineAvailable || status.linked;
        unlink.hidden = !status.linked;
        if (!status.notificationsEnabled) {
            preference.textContent = "活動取消通知目前尚未開放。";
        } else if (status.linked && status.lineAvailable) {
            preference.textContent = "優先使用 LINE。LINE 無法推播時，會視寄送結果改用 Email。";
        } else {
            preference.textContent = "目前使用會員資料中的 Email 接收通知。";
        }
    }

    link.addEventListener("click", async () => {
        link.disabled = true;
        result.textContent = "";
        try {
            const data = await request("/start", { method: "POST" });
            location.assign(data.authorizationUrl);
        } catch (error) {
            result.textContent = error.message;
            link.disabled = false;
        }
    });

    unlink.addEventListener("click", async () => {
        unlink.disabled = true;
        result.textContent = "";
        try {
            render(await request("", { method: "DELETE" }));
            result.textContent = "已解除 LINE 綁定。";
        } catch (error) {
            result.textContent = error.message;
        } finally {
            unlink.disabled = false;
        }
    });

    async function initialize() {
        if (!DndAuth.session()) return;
        try {
            render(await request(""));
            if (!hasCallback) return;
            if (callbackUrl.searchParams.has("error")) {
                result.textContent = "LINE 授權未完成，可重新綁定。";
                return;
            }
            render(await request("/complete", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    code: callbackUrl.searchParams.get("code"),
                    state: callbackUrl.searchParams.get("state")
                })
            }));
            result.textContent = "已綁定 LINE 通知。";
        } catch (error) {
            result.textContent = error.message;
            if (link.hidden && unlink.hidden) preference.textContent = "";
        }
    }

    initialize();
})();
