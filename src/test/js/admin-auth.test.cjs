const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

// 用最小 DOM 替身驗證登入流程與網路副作用，不連線到網站或資料庫。
function setup(pathname = '/admin-events.html', initialResponses = []) {
    const handlers = {};
    const calls = [];
    const navigation = [];
    const responses = [...initialResponses];
    const timers = [];
    const messages = [];
    let channel;
    const nav = { children: [], replaceChildren() { this.children = []; }, append(...elements) { for (const element of elements) { element.parent = this; this.children.push(element); } } };
    const draft = { value: '尚未儲存的活動與停權說明', focus() {} };
    let dialog;
    const form = {
        elements: { account: { value: '' }, password: { value: '', focus() {} } },
        button: { disabled: false }, message: { textContent: '' },
        addEventListener(type, handler) { this[type] = handler; },
        querySelector(selector) { return selector === 'button' ? this.button : this.message; }
    };
    const document = {
        activeElement: draft, hidden: false,
        body: { append(element) { dialog = element; } },
        addEventListener(type, handler) { handlers[type] = handler; },
        getElementById(id) { return pathname === '/events.html' && id === 'member-nav' ? nav : null; }, querySelector() { return null; },
        createElement(tag) {
            if (tag !== 'dialog') return { setAttribute() {}, addEventListener() {}, remove() { if (this.parent) this.parent.children = this.parent.children.filter(item => item !== this); } };
            return { open: false, setAttribute() {}, addEventListener() {},
                querySelector() { return form; }, showModal() { this.open = true; }, close() { this.open = false; } };
        }
    };
    const context = {
        document, Headers, URLSearchParams, Date,
        location: { pathname, search: '', reload() { navigation.push('reload'); },
            assign(value) { navigation.push(value); } },
        setTimeout(callback, delay) { timers.push({ callback, delay }); return timers.length; }, clearTimeout() {},
        BroadcastChannel: class { constructor() { channel = this; } postMessage(value) { messages.push(value); } },
        async fetch(url, options = {}) {
            calls.push({ url, options });
            assert.ok(responses.length, 'Unexpected network request: ' + url);
            const [status, data] = responses.shift();
            return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
        },
        // 後台若嘗試使用會員的瀏覽器儲存區，立即讓測試失敗。
        get localStorage() { throw new Error('Admin must not use localStorage'); },
        get sessionStorage() { throw new Error('Admin must not use sessionStorage'); }
    };
    context.window = { addEventListener(type, handler) { handlers['window:' + type] = handler; } };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/admin-auth.js'), 'utf8'), context);
    return { auth: context.window.DndAdmin, calls, navigation, responses, draft, form, handlers, nav, timers, channel, messages, context,
        get dialog() { return dialog; } };
}

const identity = { id: 7, account: 'admin', csrfToken: 'csrf', idleExpiresAt: Date.now() + 1800000 };

test('expired write retains the page and draft, and reauthentication never resubmits it', async () => {
    const app = setup();
    app.responses.push([200, identity], [401, {}]);
    const response = await app.auth.request('/admin/events', { method: 'POST', body: '{"title":"draft"}' });
    assert.equal(response.status, 401);
    assert.equal(app.dialog.open, true);
    assert.equal(app.draft.value, '尚未儲存的活動與停權說明');
    assert.deepEqual(app.navigation, []);
    app.form.elements.password.value = 'correct-password';
    app.responses.push([200, identity]);
    await app.form.submit({ preventDefault() {} });
    assert.equal(app.dialog.open, false);
    assert.equal(app.form.elements.password.value, '');
    assert.equal(app.draft.value, '尚未儲存的活動與停權說明');
    assert.equal(app.calls.filter(call => call.url === '/admin/events').length, 1);
    assert.equal(app.calls[1].options.headers.get('Authorization'), null);
    assert.equal(app.calls[1].options.headers.get('X-Admin-CSRF'), 'csrf');
});

test('background reads do not report activity; real user input does', async () => {
    const app = setup();
    app.responses.push([200, identity], [200, identity]);
    await app.auth.currentMember();
    await app.auth.currentMember();
    assert.deepEqual(app.calls.map(call => call.url), ['/admin/auth/me', '/admin/auth/me']);
    await app.handlers.keydown({ isTrusted: false });
    assert.equal(app.calls.length, 2);
    app.responses.push([200, identity]);
    await app.handlers.keydown({ isTrusted: true });
    assert.equal(app.calls[2].url, '/admin/auth/activity');
    assert.equal(app.calls[2].options.method, 'POST');
});

test('switching admin accounts reloads instead of reusing the previous account draft', async () => {
    const app = setup();
    app.responses.push([200, identity]);
    await app.auth.currentMember();
    app.responses.push([200, { ...identity, id: 8 }]);
    await assert.rejects(app.auth.login('another', 'password'));
    assert.deepEqual(app.navigation, ['reload']);
});

test('login return destinations and request paths cannot send admin credentials to another site', async () => {
    const app = setup();
    assert.equal(app.auth.destination('https://example.com'), '/admin-events.html');
    assert.equal(app.auth.destination('/admin-members.html'), '/admin-members.html');
    await assert.rejects(app.auth.request('https://example.com/admin/events'));
    assert.equal(app.calls.length, 0);
});


// 前台登入狀態查詢由載入時自動啟動，等待其非同步完成後檢查畫面。
const settled = () => new Promise(resolve => setTimeout(resolve, 0));

test('front navigation stays hidden without an admin session, even for an ADMIN member login', async () => {
    const app = setup('/events.html', [[401, {}]]);
    const storage = { getItem() { return JSON.stringify({accessToken: 'member-access', refreshToken: 'member-refresh', loginId: 'member'}); } };
    Object.defineProperty(app.context, 'localStorage', { value: storage });
    Object.defineProperty(app.context, 'sessionStorage', { value: storage });
    app.context.navigator = {};
    app.responses.push([200, { role: 'ADMIN' }]);
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/auth.js'), 'utf8'), app.context);
    await settled();
    assert.ok(!app.nav.children.some(link => link.href === '/admin-events.html'));
    assert.equal(app.dialog, undefined);
    assert.ok(!app.calls.some(call => call.url === '/admin/auth/activity'));
});

test('verified admin sees a return link without member login; rebuilding member nav retains one link', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    assert.equal(app.nav.children.filter(link => link.textContent === '返回管理後台').length, 1);
    app.nav.replaceChildren();
    app.auth.renderReturnLink(app.nav);
    app.auth.renderReturnLink(app.nav);
    assert.equal(app.nav.children.length, 1);
    assert.equal(app.nav.children[0].href, '/admin-events.html');
    assert.deepEqual(app.calls.map(call => call.url), ['/admin/auth/me']);
});

test('real frontend activity extends admin time; synthetic activity and background checks do not', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    await app.handlers.input({ isTrusted: false });
    app.responses.push([200, identity]);
    await app.handlers['window:focus']();
    assert.ok(app.calls.every(call => call.url === '/admin/auth/me'));
    app.responses.push([200, { ...identity, idleExpiresAt: identity.idleExpiresAt + 30000 }]);
    await app.handlers.input({ isTrusted: true });
    const activity = app.calls.at(-1);
    assert.equal(activity.url, '/admin/auth/activity');
    assert.equal(activity.options.headers.get('X-Admin-CSRF'), 'csrf');
    assert.equal(activity.options.headers.get('Authorization'), null);
    assert.equal(activity.options.keepalive, true);
});

test('frontend expiry hides return link without login dialog, navigation, or changing member storage', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    app.responses.push([401, {}]);
    await app.timers.at(-1).callback();
    assert.equal(app.nav.children.length, 0);
    assert.equal(app.dialog, undefined);
    assert.deepEqual(app.navigation, []);
    await app.handlers.pointerdown({ isTrusted: true });
    assert.ok(app.calls.every(call => call.url === '/admin/auth/me'));
});

test('logout in another tab removes the frontend return link; later admin login restores it', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    app.responses.push([401, {}]);
    await app.channel.onmessage();
    assert.equal(app.nav.children.length, 0);
    app.responses.push([200, identity]);
    await app.channel.onmessage();
    assert.equal(app.nav.children.length, 1);
    assert.equal(app.dialog, undefined);
});


test('an activity response arriving after frontend expiry cannot restore the return link', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    let finishActivity;
    app.context.fetch = () => new Promise(resolve => { finishActivity = resolve; });
    const touching = app.handlers.input({ isTrusted: true });
    app.auth.expired();
    finishActivity(new Response(JSON.stringify(identity), { status: 200 }));
    await touching;
    assert.equal(app.nav.children.length, 0);
    assert.equal(app.dialog, undefined);
});


function loadMemberAuth(app, signedIn) {
    let saved = signedIn ? JSON.stringify({accessToken: 'member-access', refreshToken: 'member-refresh', loginId: 'member'}) : null;
    const storage = { getItem() { return saved; }, removeItem() { saved = null; }, setItem(key, value) { saved = value; } };
    Object.defineProperty(app.context, 'localStorage', { value: storage });
    Object.defineProperty(app.context, 'sessionStorage', { value: storage });
    app.context.navigator = {};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/auth.js'), 'utf8'), app.context);
}

test('frontend logout revokes admin even without member JWT and hides the return link', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    loadMemberAuth(app, false);
    app.responses.push([200, {}]);
    await app.context.window.DndAuth.logout();
    const logout = app.calls.find(call => call.url === '/members/logout-session');
    assert.equal(logout.options.method, 'POST');
    assert.equal(logout.options.headers['X-Session-Logout'], '1');
    assert.ok(!app.nav.children.some(link => link.href === '/admin-events.html'));
    assert.ok(!app.calls.some(call => call.url === '/registration'));
});

test('expired member JWT cannot skip admin session revocation on logout', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    app.responses.push([200, {role: 'MEMBER'}]);
    loadMemberAuth(app, true);
    await settled();
    app.responses.push([200, {}], [401, {}]);
    await app.context.window.DndAuth.logout();
    const paths = app.calls.map(call => call.url);
    assert.ok(paths.indexOf('/members/logout-session') < paths.indexOf('/registration'));
    assert.equal(app.context.window.DndAuth.session(), null);
    assert.ok(!app.nav.children.some(link => link.href === '/admin-events.html'));
});

test('old admin identity response cannot restore access indicator after logout', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    let finishRead;
    app.context.fetch = () => new Promise(resolve => { finishRead = resolve; });
    const checking = app.handlers['window:focus']();
    app.auth.signedOut();
    finishRead(new Response(JSON.stringify(identity), { status: 200 }));
    await checking;
    assert.ok(!app.nav.children.some(link => link.href === '/admin-events.html'));
    assert.equal(app.dialog, undefined);
});


test('admin login provides own member access without member JWT or a second login', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    app.responses.push([200, {id: 7, role: 'ADMIN'}]);
    loadMemberAuth(app, false);
    await app.context.window.DndAuth.ready;
    await settled();
    assert.equal(app.context.window.DndAuth.session().adminSession, true);
    assert.equal(app.context.window.DndAuth.session().accessToken, undefined);
    assert.ok(!app.nav.children.some(link => link.textContent === '會員登入'));
    app.responses.push([200, {}], [200, []]);
    await app.context.window.DndAuth.request('/members/7/profile', {method: 'PUT', body: '{}'});
    await app.context.window.DndAuth.request('/registration');
    for (const call of app.calls.filter(call => call.url === '/members/7/profile' || call.url === '/registration')) {
        assert.equal(call.options.headers.get('X-Admin-Front'), '1');
        assert.equal(call.options.headers.get('X-Admin-CSRF'), 'csrf');
        assert.equal(call.options.headers.get('Authorization'), null);
    }
    assert.ok(!app.calls.some(call => call.url === '/members/login'));
    assert.ok(app.auth.frontSession());
});

test('member request waits for initial admin verification instead of redirecting to member login', async () => {
    const app = setup('/events.html', [[200, identity], [200, {id: 7}], [200, []]]);
    loadMemberAuth(app, false);
    await app.context.window.DndAuth.ready;
    const response = await app.context.window.DndAuth.request('/registration');
    assert.equal(response.status, 200);
    assert.deepEqual(app.navigation, []);
    assert.ok(app.auth.frontSession());
});

test('expired admin session cannot leave a usable member bridge behind', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    app.responses.push([200, {id: 7}]);
    loadMemberAuth(app, false);
    await settled();
    app.responses.push([401, {}]);
    await app.handlers['window:focus']();
    assert.equal(app.context.window.DndAuth.session(), null);
    await assert.rejects(app.context.window.DndAuth.request('/registration'));
    assert.ok(!app.nav.children.some(link => link.href === '/admin-events.html'));
});


test('admin can register for a free event with query parameters and cancel without losing session', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    app.responses.push([200, {id: 7}]);
    loadMemberAuth(app, false);
    await app.context.window.DndAuth.ready;
    await settled();
    app.responses.push([200, {status: 'REGISTERED'}], [200, {}]);
    const response = await app.context.window.DndAuth.request('/registration?eventId=12', {method: 'POST'});
    assert.equal(response.status, 200);
    await app.context.window.DndAuth.request('/registration/events/12', {method: 'DELETE'});
    for (const call of app.calls.filter(call => call.options.method === 'POST' || call.options.method === 'DELETE')) {
        assert.equal(call.options.headers.get('X-Admin-Front'), '1');
        assert.equal(call.options.headers.get('X-Admin-CSRF'), 'csrf');
        assert.equal(call.options.headers.get('Authorization'), null);
        assert.equal(call.options.credentials, 'same-origin');
    }
    assert.ok(app.auth.frontSession());
    assert.deepEqual(app.navigation, []);
});

test('member bridge rejects external URLs and similar route prefixes before sending credentials', async () => {
    const app = setup('/events.html', [[200, identity]]);
    await settled();
    const count = app.calls.length;
    for (const path of ['https://example.com/registration?eventId=12', '//example.com/registration', '/registration-other?eventId=12', '/members-other']) {
        await assert.rejects(app.auth.memberRequest(path), /會員請求路徑不正確/);
    }
    assert.equal(app.calls.length, count);
});
