// 後台只使用 HttpOnly Session Cookie；不讀取或保存會員的長效 JWT。
(() => {
    const inAdmin = /^\/admin-(events|members)\.html$/.test(location.pathname);
    const managingComments = location.pathname === "/event-detail.html" && new URLSearchParams(location.search).get("admin") === "1";
    let profile;
    let revision = 0;
    let active = false;
    let frontNav;
    let returnLink;
    let returnLogout;
    let returnNotice;
    let checking;
    let initialized = false;
    const channel = typeof BroadcastChannel === "function" ? new BroadcastChannel("dnd.admin-state") : null;
    let timer;
    let lastTouch = 0;
    let touching = false;
    let expiredDialog;
    let previousFocus;
    const destination = value => /^\/admin-(events|members)\.html$/.test(value || "") ? value : "/admin-events.html";
    const loginUrl = () => "/admin-login.html?returnTo=" + encodeURIComponent(destination(location.pathname));
    async function errorMessage(response, fallback) {
        const data = await response.json().catch(() => ({}));
        return data.message || data.detail || fallback;
    }
    function accept(value) {
        // 換帳號時重新載入，避免把上一位管理員的表單提交到另一個身分。
        if ((inAdmin || managingComments) && profile && profile.id !== value.id) {
            location.reload();
            throw new Error("管理員已變更，正在重新載入。");
        }
        profile = value;
        active = true;
        clearTimeout(timer);
        timer = setTimeout(checkExpiry, Math.max(0, value.idleExpiresAt - Date.now()) + 100);
        renderReturnLink(frontNav);
        window.DndAuth?.refreshIdentity();
        return value;
    }
    async function currentMember() {
        const expectedRevision = revision;
        const response = await fetch("/admin/auth/me", { cache: "no-store", credentials: "same-origin" });
        // 登出／換帳號前送出的舊回應，不得把管理員入口重新恢復。
        if (expectedRevision !== revision) {
            const error = new Error("登入狀態已變更。");
            error.stale = true;
            throw error;
        }
        if (!response.ok) {
            if (response.status === 401 || response.status === 403) invalidate();
            const error = new Error("管理員登入已失效，請重新登入。");
            error.status = response.status;
            throw error;
        }
        return accept(await response.json());
    }
    async function login(account, password) {
        const response = await fetch("/admin/auth/login", {
            method: "POST", credentials: "same-origin", cache: "no-store",
            headers: { "Content-Type": "application/json", "X-Admin-Login": "1" },
            body: JSON.stringify({ account: account.trim(), password })
        });
        if (!response.ok) throw new Error(await errorMessage(response, "登入失敗，請確認管理員帳號與密碼。"));
        // 後台登入代表改用此管理員身分，移除瀏覽器內其他帳號的會員憑證。
        try { sessionStorage.removeItem("dnd.session"); } catch {}
        try { localStorage.removeItem("dnd.session"); } catch {}
        const identity = accept(await response.json());
        channel?.postMessage("changed");
        return identity;
    }
    // 逾時以原頁面的登入對話框阻擋操作，保留尚未送出的活動、停權與退款欄位。
    // 不自動重送失敗的寫入請求，重新登入後由使用者再次確認送出。
    function expired() {
        invalidate();
        // 一般前台逾時只移除返回入口，不打斷會員瀏覽或要求管理員登入。
        if (!inAdmin && !managingComments) return;
        if (expiredDialog?.open) return;
        previousFocus = document.activeElement;
        if (!expiredDialog) {
            expiredDialog = document.createElement("dialog");
            expiredDialog.id = "admin-expired-dialog";
            expiredDialog.setAttribute("aria-labelledby", "admin-expired-title");
            // 固定模板不含任何使用者輸入；動態訊息使用 textContent。
            expiredDialog.innerHTML = '<form><h2 id="admin-expired-title">管理員登入已逾時</h2>' +
                '<p>尚未送出的內容仍保留在本頁。請重新登入，再確認並送出。</p>' +
                '<label for="admin-again-account">管理員帳號</label><input id="admin-again-account" name="account" autocomplete="username" required>' +
                '<label for="admin-again-password">密碼</label><input id="admin-again-password" name="password" type="password" autocomplete="current-password" required>' +
                '<button type="submit">重新登入</button><p role="status" aria-live="polite"></p>' +
                '<a href="/events.html">離開管理後台</a></form>';
            document.body.append(expiredDialog);
            expiredDialog.addEventListener("cancel", event => event.preventDefault());
            const form = expiredDialog.querySelector("form");
            form.addEventListener("submit", async event => {
                event.preventDefault();
                const button = form.querySelector("button");
                const message = form.querySelector('[role="status"]');
                button.disabled = true;
                try {
                    await login(form.elements.account.value, form.elements.password.value);
                    form.elements.password.value = "";
                    expiredDialog.close();
                    previousFocus?.focus();
                } catch (error) { message.textContent = error.message; }
                finally { button.disabled = false; }
            });
        }
        const form = expiredDialog.querySelector("form");
        form.elements.account.value = profile?.account || "";
        form.elements.password.value = "";
        form.querySelector('[role="status"]').textContent = "";
        expiredDialog.showModal();
        form.elements.password.focus();
    }
    function renderReturnLink(container) {
        if (inAdmin || !container) return;
        frontNav = container;
        returnLink?.remove();
        returnLogout?.remove();
        returnNotice?.remove();
        returnLink = undefined;
        if (!active || !profile || Date.now() >= profile.idleExpiresAt) return;
        const link = document.createElement("a");
        link.href = "/admin-events.html";
        link.textContent = "返回管理後台";
        container.append(link);
        returnLink = link;
        // 只登入後台、沒有會員 JWT 時，也要能從前台明確登出管理身分。
        if (window.DndAuth?.logout && !window.DndAuth.session()) {
            const button = document.createElement("button");
            const notice = document.createElement("span");
            notice.setAttribute("role", "status");
            button.type = "button";
            button.textContent = "登出管理身分";
            button.addEventListener("click", async () => {
                button.disabled = true;
                try { await window.DndAuth.logout(); location.reload(); }
                catch (error) { notice.textContent = error.message; button.disabled = false; }
            });
            container.append(button, notice);
            returnLogout = button;
            returnNotice = notice;
        }
    }
    function invalidate() {
        revision++;
        active = false;
        clearTimeout(timer);
        // 前台沒有後台草稿，清除舊身分；管理頁保留身分供原表單重新登入核對。
        if (!inAdmin && !managingComments) profile = undefined;
        renderReturnLink(frontNav);
        window.DndAuth?.refreshIdentity();
    }
    function frontSession() {
        if (!active || !profile || Date.now() >= profile.idleExpiresAt) return null;
        return { adminSession: true, loginId: "admin:" + profile.id + ":" + profile.csrfToken };
    }
    async function memberRequest(path, options = {}) {
        // 免費報名會使用 /registration?eventId=...，根路徑後也必須允許查詢參數。
        if (!/^\/(members|registration)(?:\/|\?|$)/.test(path)) throw new Error("會員請求路徑不正確。");
        if (!frontSession()) {
            const error = new Error("管理員登入已逾時，請重新從管理員入口登入。");
            error.status = 401;
            throw error;
        }
        const headers = new Headers(options.headers);
        headers.delete("Authorization");
        headers.delete("X-Refresh-Token");
        headers.set("X-Admin-Front", "1");
        headers.set("X-Admin-CSRF", profile.csrfToken);
        const response = await fetch(path, { ...options, headers, credentials: "same-origin", cache: "no-store" });
        if (response.status === 401) expired();
        return response;
    }
    function ready() { return checking || (initialized ? Promise.resolve() : checkExpiry()); }
    function signedOut() {
        invalidate();
        channel?.postMessage("changed");
    }
    function checkExpiry() {
        if (checking) return checking;
        // 到期先隱藏入口，再向伺服器確認其他分頁是否已延長期限。
        renderReturnLink(frontNav);
        checking = currentMember().catch(error => {
            if (error.stale) return;
            if (error.status === 401 || error.status === 403) expired();
            else {
                // 連線失敗時不宣稱登入仍有效；背景重試只讀取狀態，不續期。
                active = false;
                renderReturnLink(frontNav);
                if (profile) timer = setTimeout(checkExpiry, 15000);
            }
        }).finally(() => { checking = undefined; initialized = true; });
        return checking;
    }
    async function request(path, options = {}) {
        // 僅允許同源後台路徑，避免將 CSRF Token 傳往其他來源。
        if (!path.startsWith("/admin/") || path.startsWith("//")) throw new Error("後台請求路徑不正確。");
        if (!profile) {
            try { await currentMember(); }
            catch (error) { if (error.status === 401) expired(); throw error; }
        }
        const headers = new Headers(options.headers);
        headers.set("X-Admin-CSRF", profile.csrfToken);
        const response = await fetch(path, { ...options, headers, credentials: "same-origin", cache: "no-store" });
        if (response.status === 401 || response.status === 403) expired();
        return response;
    }
    // 只有真實輸入事件才回報活動，最多每 30 秒一次；背景讀取與計時器不會續期。
    async function touch(event) {
        if (!event.isTrusted || !active || !profile || document.hidden || expiredDialog?.open || touching || Date.now() - lastTouch < 30000) return;
        const actingProfile = profile;
        touching = true;
        lastTouch = Date.now();
        try {
            const response = await request("/admin/auth/activity", { method: "POST", keepalive: true });
            if (response.ok && active && profile === actingProfile) accept(await response.json());
        } catch { /* 網路錯誤不延長本地期限；下次操作仍由伺服器判斷。 */ }
        finally { touching = false; }
    }
    function renderNav() {
        const nav = document.getElementById("member-nav");
        if (!nav) return;
        for (const [href, label] of [["/events.html", "返回前台"], ["/admin-events.html", "活動管理"], ["/admin-members.html", "會員管理"]]) {
            const link = document.createElement("a");
            link.href = href;
            link.textContent = label;
            if (location.pathname === href) link.setAttribute("aria-current", "page");
            nav.append(link);
        }
        const button = document.createElement("button");
        button.type = "button";
        button.textContent = "登出後台";
        const message = document.createElement("span");
        message.setAttribute("role", "status");
        button.addEventListener("click", async () => {
            button.disabled = true;
            try {
                const response = await request("/admin/auth/logout", { method: "POST" });
                if (!response.ok && response.status !== 401) throw new Error("登出失敗，請稍後再試。");
                invalidate();
                channel?.postMessage("changed");
                location.assign("/admin-login.html");
            } catch (error) { message.textContent = error.message; button.disabled = false; }
        });
        nav.append(button, message);
    }
    window.DndAdmin = { currentMember, login, request, errorMessage, loginUrl, destination, expired, renderReturnLink, signedOut, frontSession, memberRequest, ready };
    if (location.pathname !== "/admin-login.html") {
        if (inAdmin) renderNav();
        for (const event of ["pointerdown", "keydown", "input", "scroll"]) document.addEventListener(event, touch, { passive: true, capture: true });
        window.addEventListener("pageshow", checkExpiry);
        window.addEventListener("focus", checkExpiry);
        if (channel) channel.onmessage = async () => {
            invalidate();
            if (checking) await checking;
            return checkExpiry();
        };
        document.addEventListener("visibilitychange", () => { if (!document.hidden) checkExpiry(); });
        if (!inAdmin && !managingComments) {
            frontNav = document.getElementById("member-nav");
            checkExpiry();
        }
        const header = document.querySelector(".site-header");
        if (header) new ResizeObserver(() => document.documentElement.style.setProperty("--site-header-height", header.offsetHeight + "px")).observe(header);
    }
})();
