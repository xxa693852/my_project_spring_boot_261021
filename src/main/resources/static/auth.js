(() => {
    const KEY = "dnd.session";
    let queue = Promise.resolve();
    let profilePromise;
    function session() {
        try { return JSON.parse(sessionStorage.getItem(KEY)) || null; }
        catch { return null; }
    }
    function save(value) {
        if (!value?.accessToken || !value?.refreshToken) {
            throw new Error("登入回應缺少憑證，請重新登入。");
        }
        sessionStorage.setItem(KEY, JSON.stringify({
            accessToken: value.accessToken, refreshToken: value.refreshToken
        }));
    }
    function clear() { sessionStorage.removeItem(KEY); profilePromise = undefined; }
    // Only permit these local pages as login destinations.
    function destination(value) {
        return /^\/(events\.html|admin-events\.html|member-settings\.html|my-registrations\.html|event-detail\.html\?id=[1-9]\d*)$/.test(value || "")
            ? value : "/events.html";
    }
    function loginUrl() {
        return "/login.html?returnTo=" + encodeURIComponent(
            destination(location.pathname + location.search));
    }
    async function perform(path, options = {}) {
        const current = session();
        if (!current) throw new Error("請先登入。");
        const headers = new Headers(options.headers);
        headers.set("Authorization", "Bearer " + current.accessToken);
        headers.set("X-Refresh-Token", current.refreshToken);
        const response = await fetch(path, { ...options, headers });
        const accessToken = response.headers.get("X-New-Access-Token");
        const refreshToken = response.headers.get("X-New-Refresh-Token");
        if (accessToken && refreshToken) save({ accessToken, refreshToken });
        if (response.status === 401) clear();
        return response;
    }
    // Serial requests avoid using a refresh token twice during token rotation.
    function request(path, options) {
        const result = queue.then(() => perform(path, options));
        queue = result.catch(() => {});
        return result;
    }
    function currentMember(refresh = false) {
        if (refresh) profilePromise = undefined;
        if (!profilePromise) {
            profilePromise = request("/members/me").then(async response => {
                if (!response.ok) {
                    const error = new Error(await errorMessage(response, "無法讀取會員資料。"));
                    error.status = response.status;
                    throw error;
                }
                return response.json();
            }).catch(error => { profilePromise = undefined; throw error; });
        }
        return profilePromise;
    }
    async function errorMessage(response, fallback) {
        const data = await response.json().catch(() => ({}));
        return data.message || fallback;
    }
    async function logout() {
        // Refresh first if necessary, so the logout body contains the latest token.
        const verified = await request("/registration");
        if (verified.status === 401) return;
        if (!verified.ok) throw new Error("無法確認登入狀態，請稍後再試。");
        const response = await request("/members/logout", {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken: session().refreshToken })
        });
        if (!response.ok && response.status !== 401) {
            throw new Error(await errorMessage(response, "登出失敗，請稍後再試。"));
        }
        clear();
    }
    function renderNav(container) {
        container.replaceChildren();
        const myRegistrations = document.createElement("a");
        myRegistrations.href = "/my-registrations.html";
        myRegistrations.textContent = "我的報名";
        myRegistrations.style.marginRight = "16px";
        container.append(myRegistrations);
        const settings = document.createElement("a");
        settings.href = "/member-settings.html";
        settings.textContent = "會員設定";
        settings.style.marginRight = "16px";
        container.append(settings);
        if (!session()) {
            const link = document.createElement("a");
            link.href = loginUrl();
            link.textContent = "會員登入";
            container.append(link);
            return;
        }
        const status = document.createElement("span");
        status.textContent = "已登入 ";
        const button = document.createElement("button");
        button.type = "button";
        button.textContent = "登出";
        const message = document.createElement("span");
        message.setAttribute("role", "status");
        button.addEventListener("click", async () => {
            button.disabled = true;
            try { await logout(); location.reload(); }
            catch (error) { message.textContent = error.message; button.disabled = false; }
        });
        container.append(status, button, message);
        currentMember().then(member => {
            if (member.role !== "ADMIN" || !session()) return;
            const link = document.createElement("a");
            link.href = "/admin-events.html";
            link.textContent = "活動管理";
            link.style.marginLeft = "16px";
            container.append(link);
        }).catch(() => { /* Management APIs still enforce permissions. */ });
    }
    window.DndAuth = { session, save, clear, destination, loginUrl, request, errorMessage, renderNav, currentMember };
    const nav = document.getElementById("member-nav");
    if (nav) renderNav(nav);
})();