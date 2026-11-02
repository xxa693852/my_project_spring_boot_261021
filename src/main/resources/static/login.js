const form = document.getElementById("login-form");
const message = document.getElementById("message");
const submit = document.getElementById("submit");
form.addEventListener("submit", async (event) => {
    event.preventDefault();
    submit.disabled = true;
    message.textContent = "登入中……";
    try {
        const response = await fetch("/members/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                account: form.elements.account.value.trim(),
                password: form.elements.password.value
            })
        });
        if (!response.ok) {
            throw new Error(await DndAuth.errorMessage(response, "登入失敗，請確認帳號與密碼。"));
        }
        DndAuth.save(await response.json());
        form.elements.password.value = "";
        const target = new URLSearchParams(location.search).get("returnTo");
        location.assign(DndAuth.destination(target));
    } catch (error) {
        message.textContent = error.message || "無法連線，請稍後再試。";
        submit.disabled = false;
    }
});