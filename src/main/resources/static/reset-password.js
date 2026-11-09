const token = new URLSearchParams(location.search).get("token");
const form = document.getElementById("reset-form");
const message = document.getElementById("message");
const submit = document.getElementById("submit");
let completed = false;

// 開啟頁面不會使用 Token；提交後才由後端判斷是否有效。
if (!token || !token.trim()) {
    form.hidden = true;
    message.textContent = "連結缺少重設憑證，請重新申請忘記密碼。";
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (completed || submit.disabled || !token || !token.trim()) {
        return;
    }
    const password = form.elements.password.value;
    const confirmPassword = form.elements.confirmPassword.value;
    // 調整：和註冊、會員設定使用相同的新密碼規則。
    const passwordError = DndPasswordRules.message(password, true);
    if (passwordError) {
        message.textContent = passwordError;
        form.elements.password.focus();
        return;
    }
    if (password !== confirmPassword) {
        message.textContent = "兩次輸入的密碼不一致。";
        form.elements.confirmPassword.focus();
        return;
    }
    submit.disabled = true;
    message.textContent = "正在重設密碼……";
    try {
        const response = await fetch("/members/reset-password", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ token: token.trim(), password, confirmPassword })
        });
        const data = await response.json().catch(() => null);
        if (!response.ok) {
            let errorMessage = data && data.message ? data.message : "重設失敗，請重新申請重設連結或稍後再試。";
            if (data && data.errors) {
                const details = Object.values(data.errors).filter(value => typeof value === "string");
                if (details.length > 0) {
                    errorMessage = details.join("；");
                }
            }
            throw new Error(errorMessage);
        }
        completed = true;
        form.reset();
        form.hidden = true;
        message.textContent = "密碼重設成功，請使用新密碼登入。";
        document.getElementById("success-link").hidden = false;
        // 成功後將已失效的 Token 從網址移除。
        history.replaceState(null, "", location.pathname);
    } catch (error) {
        message.textContent = error.message || "無法連線，請稍後再試。";
    } finally {
        if (!completed) {
            submit.disabled = false;
        }
    }
});
