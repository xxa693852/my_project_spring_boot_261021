(() => {
    const section = document.querySelector(".event-comments");
    const access = document.getElementById("comment-access");
    const login = document.getElementById("comment-login");
    const form = document.getElementById("comment-form");
    const body = document.getElementById("comment-body");
    const message = document.getElementById("comment-message");
    const list = document.getElementById("comment-list");
    const pages = document.getElementById("comment-pages");
    const previous = document.getElementById("comment-previous");
    const next = document.getElementById("comment-next");
    const pageNumber = document.getElementById("comment-page-number");
    const deleteDialog = document.getElementById("delete-comment-dialog");
    let commentToDelete;
    let memberPath;
    let publicPath;
    let adminPath;
    let page = 1;
    let busy = false;
    const managingComments = new URLSearchParams(location.search).get("admin") === "1";

    function button(label, action) {
        const control = document.createElement("button");
        control.type = "button";
        control.textContent = label;
        if (label === "取消編輯" || label === "返回") control.className = "button-secondary";
        control.addEventListener("click", action);
        return control;
    }

    function paragraph(text, className) {
        const element = document.createElement("p");
        element.className = className;
        element.textContent = text;
        return element;
    }

    function showComments(result) {
        const signedIn = !!DndAuth.session();
        form.hidden = !result.canPost;
        login.href = DndAuth.loginUrl();
        login.hidden = result.canModerate || !result.open || signedIn;
        access.hidden = !result.canModerate && result.open && (!signedIn || result.canPost);
        access.textContent = result.canModerate ? "管理模式：可隱藏或恢復留言，發言請使用會員登入。"
            : result.open ? "目前帳號無法留言。" : "活動已取消或結束，留言區僅供查看。";
        list.replaceChildren();
        if (!result.comments.length) list.append(paragraph("目前沒有留言。", "comment-meta"));

        for (const comment of result.comments) {
            const entry = document.createElement("article");
            entry.className = "event-comment";
            const localTime = DndTime.format(comment.createdAt);
            entry.append(paragraph(comment.authorName + " · " + localTime + "（台北時間）", "comment-meta"),
                paragraph(comment.body, "comment-body"));
            if (comment.hiddenReason) {
                entry.append(paragraph("已隱藏｜" + comment.hiddenReason, "comment-meta"));
            }

            const actions = document.createElement("div");
            actions.className = "actions";
            if (comment.canDelete) {
                actions.append(button("編輯留言", () => showEditForm(entry, comment)));
                actions.append(button("刪除留言", () => {
                    if (busy || deleteDialog.open) return;
                    commentToDelete = comment.id;
                    deleteDialog.returnValue = "";
                    deleteDialog.showModal();
                }));
            }
            if (result.canModerate) {
                const path = adminPath + "/" + comment.id + "/visibility";
                if (comment.hiddenReason) {
                    actions.append(button("恢復留言", () => {
                        change(path, { method: "DELETE" }, "留言已恢復公開。");
                    }));
                } else {
                    actions.append(button("隱藏留言", () => showHideForm(entry, path)));
                }
            }
            if (actions.childElementCount) entry.append(actions);
            list.append(entry);
        }

        const total = Math.max(1, result.totalPages);
        pages.hidden = total === 1;
        pageNumber.textContent = "第 " + page + " / " + total + " 頁";
        renderPageJump(pages, page, total, turnPage);
        previous.disabled = page === 1;
        next.disabled = !result.hasNext;
    }

    function showEditForm(entry, comment) {
        if (busy) return;
        const existing = entry.querySelector("form");
        if (existing) { existing.querySelector("textarea").focus(); return; }
        const editor = document.createElement("form");
        editor.className = "comment-edit-form";
        const label = document.createElement("label");
        label.textContent = "編輯留言（最多 500 字元）";
        const draft = document.createElement("textarea");
        draft.id = "edit-comment-" + comment.id;
        draft.maxLength = 500;
        draft.required = true;
        draft.value = comment.body;
        label.append(draft);
        const submit = button("儲存修改", () => {});
        submit.type = "submit";
        editor.append(label, submit, button("取消編輯", () => editor.remove()));
        editor.addEventListener("submit", event => {
            event.preventDefault();
            change(memberPath + "/" + comment.id, json("PUT", {body: draft.value}), "留言已更新。");
        });
        entry.append(editor);
        DndCounters.attach(draft, 500);
        draft.focus();
    }

    function showHideForm(entry, path) {
        const existing = entry.querySelector("form");
        if (existing) {
            existing.querySelector("input").focus();
            return;
        }
        const hideForm = document.createElement("form");
        const label = document.createElement("label");
        const reason = document.createElement("input");
        label.textContent = "給留言者的說明（最多 200 字）";
        reason.required = true;
        reason.maxLength = 200;
        label.append(reason);
        const submit = document.createElement("button");
        submit.type = "submit";
        submit.textContent = "確認隱藏";
        const cancel = button("返回", () => {
            hideForm.remove();
            entry.querySelector(".actions button:last-child").focus();
        });
        const notice = paragraph("確認後會私下通知留言者這段說明。", "comment-meta");
        hideForm.append(label, notice, submit, cancel);
        hideForm.addEventListener("submit", event => {
            event.preventDefault();
            change(path, json("PUT", { reason: reason.value }), "留言已隱藏。");
        });
        entry.append(hideForm);
        DndCounters.attach(reason, 200);
        reason.focus();
    }

    async function fetchPage(target) {
        const query = "?page=" + target;
        let response;
        // 後台身分獨立檢查，會員帳號的 role 不會自動啟用管理模式。
        if (managingComments) {
            response = await DndAdmin.request(adminPath + query, { cache: "no-store" });
            if (!response.ok) throw new Error("管理員登入已失效，請重新登入後再載入留言。");
        }
        if (response?.ok) return response.json();
        if (DndAuth.session()) {
            response = await DndAuth.request(memberPath + query, { cache: "no-store" });
        }
        if (!response || response.status === 401) {
            response = await fetch(publicPath + query, { cache: "no-store" });
        }
        if (!response.ok) throw new Error(await DndAuth.errorMessage(response, "無法載入留言。"));
        return response.json();
    }

    async function load(target) {
        let result = await fetchPage(target);
        if (target > Math.max(1, result.totalPages)) {
            target = Math.max(1, result.totalPages);
            result = await fetchPage(target);
        }
        page = target;
        showComments(result);
    }

    function json(method, data) {
        return { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) };
    }

    function turnPage(target) {
        change(target, undefined, undefined, true);
    }

    async function change(path, options, success, scrollToComments = false) {
        if (busy) return;
        busy = true;
        const controls = Array.from(section.querySelectorAll("button,input,textarea")).map(control => [control, control.disabled]);
        for (const [control] of controls) control.disabled = true;
        section.setAttribute("aria-busy", "true");
        try {
            if (typeof path === "number") {
                await load(path);
                if (scrollToComments) list.scrollIntoView({ block: "start" });
                message.textContent = "";
                return;
            }
            const response = path.startsWith("/admin/")
                ? await DndAdmin.request(path, options) : await DndAuth.request(path, options);
            if (!response.ok) {
                throw new Error(await DndAuth.errorMessage(response, "無法更新留言。"));
            }
            if (options.method === "POST") { body.value = ""; window.DndCounters?.refresh(form); }
            try {
                await load(options.method === "POST" ? 1 : page);
                message.textContent = success;
            } catch (error) {
                message.textContent = success + " 請重新整理以更新留言列表。";
            }
        } catch (error) {
            message.textContent = error.message;
        } finally {
            for (const [control, disabled] of controls) control.disabled = disabled;
            busy = false;
            section.removeAttribute("aria-busy");
        }
    }

    form.addEventListener("submit", event => {
        event.preventDefault();
        change(memberPath, json("POST", { body: body.value }), "留言已送出。");
    });
    previous.addEventListener("click", () => turnPage(page - 1));
    next.addEventListener("click", () => turnPage(page + 1));
    deleteDialog.addEventListener("close", () => {
        if (deleteDialog.returnValue === "cancel") {
            change(memberPath + "/" + commentToDelete, { method: "DELETE" }, "留言已刪除。");
        }
        commentToDelete = undefined;
    });

    window.loadEventComments = id => {
        if (publicPath) return;
        publicPath = "/events/" + id + "/comments";
        memberPath = "/members/me" + publicPath;
        adminPath = "/admin" + publicPath;
        section.hidden = false;
        message.textContent = "留言載入中……";
        change(1);
    };
})();
