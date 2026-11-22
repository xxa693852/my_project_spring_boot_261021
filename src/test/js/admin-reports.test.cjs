const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

function setup() {
    const elements = {};
    for (const id of ["report-form", "report-from", "report-through", "download-report", "report-message"]) {
        elements[id] = { value: "", listeners: {}, addEventListener(name, handler) { this.listeners[name] = handler; }, reportValidity() { return true; } };
    }
    const requests = [];
    const links = [];
    let release;
    let response = { ok: true, headers: { get() { return 'attachment; filename="dnd-report_20261101.xlsx"'; } }, blob: async () => ({}) };
    class FixedDate extends Date { constructor(...args) { super(...(args.length ? args : ["2026-10-31T16:30:00Z"])); } }
    class TestURL extends URL { static createObjectURL() { return "blob:test"; } static revokeObjectURL() {} }
    const context = vm.createContext({ Date: FixedDate, Intl, URLSearchParams, URL: TestURL, setTimeout() {},
        document: { getElementById: id => elements[id], body: { append() {} }, createElement() {
            const link = { click() { links.push(this); }, remove() {} }; return link;
        } },
        DndAdmin: { request: async path => { requests.push(path); await new Promise(resolve => { release = resolve; }); return response; },
            errorMessage: async () => "管理員登入已逾時" }
    });
    vm.runInContext(fs.readFileSync("src/main/resources/static/admin-reports.js", "utf8"), context);
    return { elements, requests, links, finish() { release(); }, fail() { response = { ok: false }; },
        submit() { return elements["report-form"].listeners.submit({ preventDefault() {} }); } };
}

test("報表預設台北本月，下載使用後台 API 與伺服器檔名，處理中不重複請求", async () => {
    const env = setup();
    assert.equal(env.elements["report-from"].value, "2026-11-01");
    assert.equal(env.elements["report-through"].value, "2026-11-30");
    const pending = env.submit();
    await env.submit();
    assert.equal(env.requests.length, 1);
    assert.equal(env.elements["download-report"].disabled, true);
    assert.equal(env.requests[0], "/admin/reports/export?from=2026-11-01&through=2026-11-30");
    env.finish(); await pending;
    assert.equal(env.links[0].download, "dnd-report_20261101.xlsx");
    assert.equal(env.elements["download-report"].disabled, false);
});

test("日期倒置不請求，下載失敗保留日期並恢復按鈕", async () => {
    const env = setup();
    env.elements["report-from"].value = "2026-12-01";
    await env.submit();
    assert.equal(env.requests.length, 0);
    env.elements["report-from"].value = "2026-11-01";
    env.fail(); const pending = env.submit(); env.finish(); await pending;
    assert.equal(env.elements["report-message"].textContent, "管理員登入已逾時");
    assert.equal(env.links.length, 0);
    assert.equal(env.elements["report-from"].value, "2026-11-01");
    assert.equal(env.elements["download-report"].disabled, false);
});
