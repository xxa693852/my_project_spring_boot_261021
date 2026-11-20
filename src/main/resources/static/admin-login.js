const form = document.getElementById("admin-login-form");
const message = document.getElementById("message");
const submit = document.getElementById("submit");
form.addEventListener("submit", async event => {
    event.preventDefault();
    submit.disabled = true;
    message.textContent = "登入中……";
    try {
        await DndAdmin.login(form.elements.account.value, form.elements.password.value);
        form.elements.password.value = "";
        location.assign(DndAdmin.destination(new URLSearchParams(location.search).get("returnTo")));
    } catch (error) {
        message.textContent = error.message || "無法連線，請稍後再試。";
        submit.disabled = false;
    }
});
