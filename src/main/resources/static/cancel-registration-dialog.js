let cancellationDialog;

function confirmRegistrationCancellation(title) {
    if (!cancellationDialog) {
        cancellationDialog = document.createElement("dialog");
        cancellationDialog.className = "cancel-registration-dialog";
        cancellationDialog.setAttribute("aria-labelledby", "cancel-title");
        cancellationDialog.setAttribute("aria-describedby", "cancel-description");
        cancellationDialog.innerHTML = `
            <form method="dialog">
                <h2 id="cancel-title">取消報名</h2>
                <p id="cancel-description"></p>
                <div class="actions">
                    <button type="submit" value="keep" autofocus>保留報名</button>
                    <button type="submit" value="cancel">確認取消報名</button>
                </div>
            </form>`;
        document.body.append(cancellationDialog);
    }
    if (cancellationDialog.open) return Promise.resolve(false);
    cancellationDialog.querySelector("p").textContent =
        "確定要取消「" + (title || "這場活動") + "」的報名嗎？";
    cancellationDialog.returnValue = "";
    cancellationDialog.showModal();
    return new Promise(resolve => {
        cancellationDialog.addEventListener("close", () => {
            resolve(cancellationDialog.returnValue === "cancel");
        }, { once: true });
    });
}
