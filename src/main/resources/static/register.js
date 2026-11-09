const form = document.getElementById("register-form");
const message = document.getElementById("message");
const submit = document.getElementById("submit");
const fields = ["name", "account", "email", "password", "confirmPassword"];
const target = DndAuth.destination(new URLSearchParams(location.search).get("returnTo"));
const loginUrl = "/login.html?returnTo=" + encodeURIComponent(target);
document.getElementById("login-link").href = loginUrl;
document.getElementById("continue-login").href = loginUrl;
let submitting = false;
function clearErrors() {
    for (const field of fields) {
        document.getElementById(field + "-error").textContent = "";
        form.elements[field].removeAttribute("aria-invalid");
    }
}
form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submitting) return;
    // 調整：立即提示長度與 bytes 錯誤；後端仍會再次驗證。
    const passwordError = DndPasswordRules.message(form.elements.password.value, true);
    if (passwordError) {
        message.textContent = passwordError;
        form.elements.password.focus();
        return;
    }
    submitting = true;
    submit.disabled = true;
    clearErrors();
    message.textContent = "註冊中……";
    try {
        // Send only the DTO's five fields; role and status are assigned by the server.
        const body = {};
        for (const field of fields) body[field] = form.elements[field].value;
        const response = await fetch("/members", {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            let firstInvalid;
            for (const field of fields) {
                if (typeof data.errors?.[field] !== "string") continue;
                document.getElementById(field + "-error").textContent = data.errors[field];
                form.elements[field].setAttribute("aria-invalid", "true");
                firstInvalid ??= form.elements[field];
            }
            message.textContent = data.message || "註冊失敗，請確認輸入資料。";
            firstInvalid?.focus();
            return;
        }
        form.reset();
        form.hidden = true;
        document.getElementById("success").hidden = false;
        document.getElementById("continue-login").focus();
    } catch {
        message.textContent = "無法連線，請稍後再試。";
    } finally {
        submitting = false;
        submit.disabled = false;
    }
});