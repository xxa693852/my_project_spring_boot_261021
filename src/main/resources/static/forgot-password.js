const form = document.getElementById("forgot-form");
const submit = document.getElementById("submit");
const message = document.getElementById("message");

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submit.disabled || !form.reportValidity()) {
        return;
    }
    submit.disabled = true;
    message.textContent = "正在處理申請……";
    try {
        // 只傳 Email，不需要登入 Token；存在與不存在都顯示相同提示。
        const response = await fetch("/members/forgot-password", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email: form.elements.email.value.trim() })
        });
        const data = await response.json().catch(() => null);
        if (!response.ok) {
            let errorMessage = "目前無法處理申請，請稍後再試。";
            if (data && data.errors && typeof data.errors.email === "string") {
                errorMessage = data.errors.email;
            } else if (data && typeof data.message === "string") {
                errorMessage = data.message;
            }
            throw new Error(errorMessage);
        }
        message.textContent = "若此 Email 已註冊且帳號可使用，系統將寄送密碼重設信。";
    } catch (error) {
        message.textContent = error.message || "無法連線，請稍後再試。";
    } finally {
        submit.disabled = false;
    }
});
