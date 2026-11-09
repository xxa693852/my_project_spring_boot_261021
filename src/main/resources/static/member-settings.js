const message = document.getElementById("message");
const settings = document.getElementById("settings");
const retry = document.getElementById("retry");
const profileForm = document.getElementById("profile-form");
const passwordForm = document.getElementById("password-form");
let memberId, busy = false;
function requireLogin() {
    if (DndAuth.session()) return false;
    settings.hidden = true;
    location.replace(DndAuth.loginUrl());
    return true;
}
function setBusy(value) {
    busy = value;
    settings.querySelectorAll("fieldset").forEach(fieldset => { fieldset.disabled = value; });
    retry.disabled = value;
}
function displayMember(member) {
    memberId = member.id;
    for (const key of ["account", "name", "email"]) profileForm.elements[key].value = member[key];
}
async function loadMember() {
    if (busy || requireLogin()) return;
    setBusy(true);
    retry.hidden = true;
    try {
        displayMember(await DndAuth.currentMember(true));
        settings.hidden = false;
        message.textContent = "";
    } catch (error) {
        if (error.status === 401) requireLogin();
        message.textContent = error.message || "無法載入會員資料，請稍後再試。";
        retry.hidden = false;
    } finally { setBusy(false); }
}
function bindForm(form, names, path, resultId) {
    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (busy || !memberId || requireLogin()) return;
        const result = document.getElementById(resultId);
        for (const key of names) {
            document.getElementById(key + "-error").textContent = "";
            form.elements[key].removeAttribute("aria-invalid");
        }
        const body = {};
        for (const key of names) body[key] = form.elements[key].value;
        // 調整：新密碼檢查完整規則；舊密碼僅檢查 bytes。
        if (path === "password") {
            const passwordError = DndPasswordRules.message(body.password, true);
            const oldPasswordError = DndPasswordRules.message(body.oldPassword, false);
            if (passwordError || oldPasswordError) {
                result.textContent = passwordError || oldPasswordError;
                return;
            }
        }
        setBusy(true);
        result.textContent = "儲存中……";
        let saved = false;
        try {
            const response = await DndAuth.request("/members/" + memberId + "/" + path, {
                method: "PUT", headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            if (response.status === 401) { requireLogin(); return; }
            const data = await response.json().catch(() => ({}));
            if (!response.ok) {
                let first;
                for (const key of names) if (typeof data.errors?.[key] === "string") {
                    document.getElementById(key + "-error").textContent = data.errors[key];
                    form.elements[key].setAttribute("aria-invalid", "true");
                    first ??= form.elements[key];
                }
                result.textContent = data.message || "儲存失敗，請確認輸入資料。";
                setBusy(false);
                first?.focus();
                return;
            }
            saved = true;
            if (path === "password") {
                form.reset();
                result.textContent = "密碼修改成功。既有登入仍有效，下次請使用新密碼登入。";
            } else {
                displayMember(await DndAuth.currentMember(true));
                result.textContent = data.message || "個人資料已更新。";
            }
        } catch (error) {
            if (error.status === 401) requireLogin();
            result.textContent = saved
                ? "資料已儲存，但重新載入失敗，請重新整理確認。"
                : "無法連線，請稍後再試。";
        } finally { setBusy(false); }
    });
}
bindForm(profileForm, ["name", "email"], "profile", "profile-message");
bindForm(passwordForm, ["oldPassword", "password", "confirmPassword"], "password", "password-message");
retry.addEventListener("click", loadMember);
loadMember();