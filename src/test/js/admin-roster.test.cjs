const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

function setup() {
    class Element {
        constructor() { this.children = []; this.listeners = {}; this.hidden = false; this.textContent = ""; this.value = ""; this.isConnected = true; this.classList = { add() {}, remove() {} }; }
        append(child) { this.children.push(child); }
        replaceChildren() { this.children = []; }
        setAttribute() {}
        addEventListener(name, handler) { this.listeners[name] = handler; }
        querySelectorAll() { return this.children.flatMap(child => [child, ...child.querySelectorAll()]); }
        reset() {}
        focus() { this.focused = true; }
        showModal() { this.open = true; }
        close(value) { this.open = false; this.returnValue = value || ""; this.listeners.close?.(); }
    }
    const elements = new Map();
    const document = {
        getElementById(id) { if (!elements.has(id)) elements.set(id, new Element()); return elements.get(id); },
        createElement() { return new Element(); }
    };
    const malicious = "<img src=x onerror=alert(1)>";
    const data = { title: malicious, registeredCount: 1, heldCount: 1, remainingSlots: 1, capacity: 3,
        entries: [{ id: 20, name: malicious, account: "player", registeredAt: "2026-01-01", status: "REGISTERED", paymentStatus: "PAID", canCancel: true }],
        cancellations: [{ memberId: 7, operatorId: 1, reason: malicious }] };
    const calls = [];
    const context = vm.createContext({ document, Date, DndCounters: { refresh() {} }, busy: false, DndTime: { format: value => value || "—" },
        checked: async (path, options) => { calls.push({ path, options }); return options ? { message: "取消完成" } : data; },
        loadList: async () => {}, text(parent, tag, value) { const node = new Element(); node.textContent = value; parent.append(node); return node; }
    });
    const source = fs.readFileSync("src/main/resources/static/admin-events.js", "utf8");
    vm.runInContext(source.slice(source.indexOf("// 名單與個別取消共用")), context);
    return { context, document, elements, calls, malicious, data };
}

test("名單以純文字呈現輸入，並顯示名額及管理員取消歷程", async () => {
    const env = setup();
    await vm.runInContext("openRoster(11, document.createElement('button'))", env.context);
    assert.match(env.document.getElementById("roster-summary").textContent, /待付款保留：1/);
    const row = env.document.getElementById("roster-list").children[0];
    assert.equal(row.children[0].textContent, env.malicious + "／player");
    assert.equal(row.children[0].children.length, 0);
    assert.match(env.document.getElementById("roster-history").children[0].children[4].textContent, /onerror/);
});

test("個別取消需選擇對象及原因，送出正確 API 後重新讀取名單", async () => {
    const env = setup();
    await vm.runInContext("openRoster(11, document.createElement('button'))", env.context);
    const row = env.document.getElementById("roster-list").children[0];
    row.children[4].children[0].listeners.click();
    const form = env.document.getElementById("roster-cancel-form");
    assert.equal(form.hidden, false);
    await form.listeners.submit({ preventDefault() {} });
    assert.equal(env.calls.length, 1);
    env.document.getElementById("roster-reason").value = "  事故  ";
    const pending = form.listeners.submit({ preventDefault() {} });
    assert.equal(env.calls.length, 1, "第二次確認前不能送出取消 API");
    assert.equal(env.document.getElementById("roster-final-dialog").open, true);
    assert.equal(env.document.getElementById("roster-final-keep").focused, true);
    env.document.getElementById("roster-final-confirm").listeners.click();
    await pending;
    assert.equal(env.calls[1].path, "/admin/events/11/registrations/20/cancel");
    assert.deepEqual(JSON.parse(env.calls[1].options.body), { reason: "事故" });
    assert.equal(form.hidden, true);
    assert.equal(env.calls[2].path, "/admin/events/11/registrations");
});

test("不可取消的紀錄沒有取消按鈕", async () => {
    const env = setup();
    env.data.entries[0].canCancel = false;
    await vm.runInContext("openRoster(11, document.createElement('button'))", env.context);
    assert.equal(env.document.getElementById("roster-list").children[0].children[4].children.length, 0);
});

test("第二次確認選擇保留或 Escape 關閉不送出 API 且保留原因", async () => {
    for (const action of ["keep", "escape"]) {
        const env = setup();
        await vm.runInContext("openRoster(11, document.createElement('button'))", env.context);
        env.document.getElementById("roster-list").children[0].children[4].children[0].listeners.click();
        env.document.getElementById("roster-reason").value = "事故";
        const pending = env.document.getElementById("roster-cancel-form").listeners.submit({ preventDefault() {} });
        if (action === "keep") env.document.getElementById("roster-final-keep").listeners.click();
        else env.document.getElementById("roster-final-dialog").close();
        await pending;
        assert.equal(env.calls.length, 1);
        assert.equal(env.document.getElementById("roster-reason").value, "事故");
        assert.equal(env.document.getElementById("roster-cancel-form").hidden, false);
    }
});

test("歷程分別顯示 Email 與 LINE 狀態，舊紀錄不誤標為已推播", async () => {
    const env = setup();
    env.data.cancellations[0].id = 3;
    env.data.notifications = [{ cancellationId: 3, status: "SENT", lineStatus: "NOT_BOUND" }];
    await vm.runInContext("openRoster(11, document.createElement('button'))", env.context);
    const cells = env.document.getElementById("roster-history").children[0].children;
    assert.equal(cells.length, 7);
    assert.equal(cells[5].textContent, "郵件服務已接受");
    assert.equal(cells[6].textContent, "未綁定 LINE");
    env.data.notifications[0].lineStatus = "SENT";
    await vm.runInContext("renderRoster()", env.context);
    assert.equal(env.document.getElementById("roster-history").children[0].children[6].textContent, "LINE 服務已接受");
    env.data.notifications[0].lineStatus = null;
    await vm.runInContext("renderRoster()", env.context);
    assert.equal(env.document.getElementById("roster-history").children[0].children[6].textContent, "舊紀錄（未啟用 LINE 通知）");
});
