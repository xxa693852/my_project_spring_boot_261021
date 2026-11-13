(() => {
    const KEY = "dnd.session";
    let queue = Promise.resolve();
    let profilePromise;
    function session() {
        try {
            const saved = sessionStorage.getItem(KEY) || localStorage.getItem(KEY);
            const tokens = JSON.parse(saved);
            if (tokens?.accessToken && tokens?.refreshToken) return tokens;
        } catch {
            // Browsers may restrict storage.
        }
        return null;
    }
    function save(value, remember = false) {
        if (!value?.accessToken || !value?.refreshToken) {
            throw new Error("登入回應缺少權杖，請重新登入。");
        }
        const tokens = JSON.stringify({
            accessToken: value.accessToken,
            refreshToken: value.refreshToken,
            loginId: globalThis.crypto.randomUUID?.() || value.refreshToken
        });
        if (remember) {
            localStorage.setItem(KEY, tokens);
            sessionStorage.removeItem(KEY);
        } else {
            sessionStorage.setItem(KEY, tokens);
            localStorage.removeItem(KEY);
        }
        profilePromise = undefined;
    }
    function clear() {
        sessionStorage.removeItem(KEY);
        localStorage.removeItem(KEY);
        profilePromise = undefined;
    }
    // Only permit these local pages as login destinations.
    function destination(value) {
        return /^\/(events\.html(?:\?(?:page=[1-9]\d*(?:&event=[1-9]\d*)?|event=[1-9]\d*))?|admin-events\.html|member-settings\.html|my-registrations\.html|event-detail\.html\?id=[1-9]\d*)$/.test(value || "")
            ? value : "/events.html";
    }
    function loginUrl() {
        return "/login.html?returnTo=" + encodeURIComponent(
            destination(location.pathname + location.search));
    }
    async function perform(path, options = {}) {
        const current = session();
        if (!current) {
            const error = new Error("請先登入。");
            error.status = 401;
            throw error;
        }
        const storage = sessionStorage.getItem(KEY) ? sessionStorage : localStorage;
        const headers = new Headers(options.headers);
        headers.set("Authorization", "Bearer " + current.accessToken);
        headers.set("X-Refresh-Token", current.refreshToken);
        const response = await fetch(path, { ...options, headers });
        const accessToken = response.headers.get("X-New-Access-Token");
        const refreshToken = response.headers.get("X-New-Refresh-Token");
        // Only update the session that was used for this request.
        const latest = session();
        if (latest?.refreshToken === current.refreshToken) {
            if (response.status === 401) {
                storage.removeItem(KEY);
                profilePromise = undefined;
            } else if (accessToken && refreshToken) {
                storage.setItem(KEY, JSON.stringify({ ...current, accessToken, refreshToken }));
            }
        }
        return response;
    }
    // Queue requests locally and lock across tabs when the browser supports it.
    function withSessionLock(action) {
        const result = queue.then(() => {
            if (navigator.locks) return navigator.locks.request(KEY, action);
            return action();
        });
        queue = result.catch(() => {});
        return result;
    }
    function request(path, options) {
        const loginId = session()?.loginId;
        return withSessionLock(() => {
            if (session()?.loginId !== loginId) {
                const error = new Error("登入狀態已變更，請重新整理頁面。");
                error.status = 401;
                throw error;
            }
            return perform(path, options);
        });
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
    function logout() {
        return withSessionLock(async () => {
            // Refresh first if necessary, so the logout body contains the latest token.
            const verified = await perform("/registration");
            if (verified.status === 401) { clear(); return; }
            if (!verified.ok) throw new Error("無法確認登入狀態，請稍後再試。");
            const response = await perform("/members/logout", {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ refreshToken: session().refreshToken })
            });
            if (!response.ok && response.status !== 401) {
                throw new Error(await errorMessage(response, "登出失敗，請稍後再試。"));
            }
            clear();
        });
    }
    function renderNav(container) {
        container.replaceChildren();
        const events = document.createElement("a");
        events.href = "/events.html";
        events.textContent = "活動列表";
        if (location.pathname === events.pathname) events.setAttribute("aria-current", "page");
        container.append(events);
        const myRegistrations = document.createElement("a");
        myRegistrations.href = "/my-registrations.html";
        myRegistrations.textContent = "我的報名";
        if (location.pathname === myRegistrations.pathname) myRegistrations.setAttribute("aria-current", "page");
        container.append(myRegistrations);
        const settings = document.createElement("a");
        settings.href = "/member-settings.html";
        settings.textContent = "會員設定";
        if (location.pathname === settings.pathname) settings.setAttribute("aria-current", "page");
        container.append(settings);
        if (!session()) {
            const link = document.createElement("a");
            link.href = loginUrl();
            link.textContent = "會員登入";
            container.append(link);
            return;
        }
        const status = document.createElement("span");
        status.className = "nav-session-status";
        status.textContent = "已登入";
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
            if (location.pathname === link.pathname) link.setAttribute("aria-current", "page");
            container.append(link);
        }).catch(error => {
            if (error.status === 401 && !session()) renderNav(container);
        });
    }
    window.DndAuth = { session, save, clear, destination, loginUrl, request, errorMessage, renderNav, currentMember };
    const header = document.querySelector(".site-header");
    if (header) {
        new ResizeObserver(() => {
            document.documentElement.style.setProperty("--site-header-height", header.offsetHeight + "px");
        }).observe(header);
    }
    const nav = document.getElementById("member-nav");
    if (nav) {
        renderNav(nav);
        window.addEventListener("storage", event => {
            if (event.storageArea !== localStorage || event.key !== KEY || sessionStorage.getItem(KEY)) return;
            try {
                if (JSON.parse(event.oldValue)?.loginId !== JSON.parse(event.newValue)?.loginId
                    || event.oldValue === null || event.newValue === null) location.reload();
            } catch {
                location.reload();
            }
        });
    }
})();