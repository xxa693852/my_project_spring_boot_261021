const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

function setup() {
    class Element {
        constructor(tag = "div") { this.tagName = tag; this.children = []; this.attributes = {}; this.value = ""; this.hidden = false; }
        get childElementCount() { return this.children.length; }
        append(...nodes) { this.children.push(...nodes); }
        replaceChildren(...nodes) { this.children = nodes; }
        setAttribute(name, value) { this.attributes[name] = value; }
        reset() {}
        add(node, index) { this.children.splice(index, 0, node); }
    }
    const elements = new Map();
    const document = { createElement: tag => new Element(tag), getElementById(id) {
        if (!elements.has(id)) elements.set(id, new Element());
        return elements.get(id);
    }};
    const input = "<img src=x onerror=alert(1)>";
    const data = { member: { name: input, account: "player", email: "player@example.com", role: "MEMBER", status: "ACTIVE", suspended: true },
        registrations: [{ eventTitle: input, eventId: 11, status: "REGISTERED", startAt: "2026-12-25 12:00:00" },
            { eventId: 12, status: "CANCELLED" }],
        actions: [{ action: "SUSPEND", actedAt: "2026-11-22 12:00:00", actorId: 1,
            suspendedUntil: "2026-12-14 11:11:11", reason: input, notificationStatus: "SENT", notificationChannel: "EMAIL" },
            { action: "SUSPEND", actorId: 2, reason: "永久" }, { action: "LIFT", actorId: 1, reason: "解除" }] };
    const details = new Element();
    const suspension = new Element();
    const context = vm.createContext({ document, details, suspension, dialog: { open: true }, detailRequest: 0,
        operation: new Element(), untilGroup: new Element(), deadlinePicker: { time: {}, update() {}, setMode() {} },
        taipeiDate: { format() { return "2026-11-22"; } }, Date,
        Option: function (label, value) { this.label = label; this.value = value; },
        DndTime: { format(value, fallback) { return value || fallback; } },
        DndAdmin: { request: async () => ({ ok: true, status: 200, json: async () => data }) }
    });
    const source = fs.readFileSync("src/main/resources/static/admin-members.js", "utf8");
    vm.runInContext(source.slice(source.indexOf("function addText("), source.indexOf("async function openDetails(")), context);
    const tables = () => details.children.filter(node => node.className === "table-scroll").map(wrapper => wrapper.children[0]);
    return { context, data, details, suspension, tables, input };
}

test("會員報名及停權紀錄分欄呈現，輸入保持純文字並保留各種停權與通知狀態", async () => {
    const env = setup();
    await vm.runInContext("renderDetails(7)", env.context);
    const [registrations, actions] = env.tables();
    assert.equal(registrations.children[1].children[0].children.length, 3);
    assert.equal(registrations.children[1].children[0].children[0].attributes.scope, "col");
    const rows = registrations.children[2].children;
    assert.deepEqual(rows[0].children.map(cell => cell.textContent), [env.input, "已報名", "2026-12-25 12:00:00"]);
    assert.equal(rows[0].children[0].children.length, 0);
    assert.deepEqual(rows[1].children.map(cell => cell.textContent), ["場次 #12", "已取消", "—"]);
    const history = actions.children[2].children;
    assert.deepEqual(history[0].children.map(cell => cell.textContent),
        ["2026-11-22 12:00:00", "#1", "限期停權", "2026-12-14 11:11:11", env.input, "已送出（EMAIL）"]);
    assert.equal(history[0].children[4].children.length, 0);
    assert.equal(history[1].children[2].textContent, "永久停權");
    assert.equal(history[1].children[3].textContent, "無期限");
    assert.equal(history[2].children[2].textContent, "解除停權");
    assert.equal(history[2].children[3].textContent, "—");
    assert.equal(history[2].children[5].textContent, "—");
    assert.equal(env.suspension.hidden, false);
});

test("空紀錄保留表頭與跨欄提示，停用或管理員帳號仍隱藏停權表單", async () => {
    for (const member of [{ status: "INACTIVE", role: "MEMBER" }, { status: "ACTIVE", role: "ADMIN" }]) {
        const env = setup();
        Object.assign(env.data.member, member);
        env.data.registrations = [];
        env.data.actions = [];
        await vm.runInContext("renderDetails(7)", env.context);
        const [registrations, actions] = env.tables();
        const first = registrations.children[2].children[0].children[0];
        const second = actions.children[2].children[0].children[0];
        assert.equal(first.colSpan, 3);
        assert.equal(first.textContent, "沒有報名紀錄。");
        assert.equal(second.colSpan, 6);
        assert.equal(second.textContent, "沒有停權紀錄。");
        assert.equal(env.suspension.hidden, true);
    }
});
