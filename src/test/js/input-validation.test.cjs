const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const staticDir = path.join(__dirname, '../../main/resources/static');
const context = {window: {}, TextEncoder, Date};
for (const file of ['event-validation.js', 'password-rules.js', 'member-rules.js']) {
    vm.runInNewContext(fs.readFileSync(path.join(staticDir, file), 'utf8'), context);
}
const eventRules = context.window.DndEventRules;
const passwordRules = context.window.DndPasswordRules;
const now = Date.parse('2026-11-21T12:00:00+08:00');
function event() {
    return {title: '活動', description: '介紹', hostName: '主持人', location: '台北',
        startAt: '2026-12-03T12:00:00', endAt: '2026-12-03T16:00:00',
        registrationDeadline: '2026-12-02T12:00:00', capacity: 1,
        registrationFee: 0, coverTheme: 'tavern', tags: []};
}

test('new passwords accept 15 and 30 characters and reject 14 and 31 across ASCII and Chinese byte limits', () => {
    for (const length of [14, 15, 30, 31]) assert.equal(passwordRules.message('a'.repeat(length), true) === '', length >= 15 && length <= 30);
    assert.equal(passwordRules.message('中'.repeat(24), true), '');
    assert.match(passwordRules.message('中'.repeat(25), true), /72 bytes/);
    assert.equal(passwordRules.message(' ', true), '會員密碼必填');
    assert.equal(passwordRules.confirmation('a'.repeat(15), ''), '確認密碼必填');
    assert.equal(passwordRules.confirmation('a'.repeat(15), 'b'.repeat(15)), '確認密碼不一致');
});

test('login and old password checks keep existing short and long passwords usable', () => {
    for (const password of ['old123', 'a'.repeat(40), 'a'.repeat(72)]) assert.equal(passwordRules.message(password, false), '');
    assert.match(passwordRules.message('a'.repeat(73), false), /72 bytes/);
});

test('create and update share event lengths, tag boundaries and capacity limits', () => {
    for (const isNew of [true, false]) {
        const body = event();
        for (const [field, limit] of [['title', 100], ['description', 5000], ['hostName', 50], ['location', 255]]) {
            body[field] = '字'.repeat(limit);
            assert.equal(eventRules.validate(body, isNew, now)[field], undefined);
            body[field] += '字';
            assert.ok(eventRules.validate(body, isNew, now)[field]);
            body[field] = '有效文字';
        }
        for (const capacity of [0, 1, 50, 51, 1.5, null]) {
            body.capacity = capacity;
            assert.equal(eventRules.validate(body, isNew, now).capacity === undefined, capacity === 1 || capacity === 50);
        }
        body.capacity = 1;
        body.tags = ['a', 'b', 'c', 'd', '字'.repeat(16)];
        assert.equal(eventRules.validate(body, isNew, now).tags, undefined);
        body.tags.push('f');
        assert.equal(eventRules.validate(body, isNew, now).tags, '最多可填寫 5 個自訂標籤');
        body.tags = ['字'.repeat(17)];
        assert.equal(eventRules.validate(body, isNew, now).tags, '每個自訂標籤最多 16 字元');
        body.tags = ['一\n二'];
        assert.equal(eventRules.validate(body, isNew, now).tags, '自訂標籤不能包含換行');
        body.tags = [' '];
        assert.equal(eventRules.validate(body, isNew, now).tags, '自訂標籤不能空白');
    }
});

test('invalid calendar dates, blank fields and time order produce field errors', () => {
    const body = event();
    body.startAt = '2028-02-30T12:00:00';
    assert.ok(eventRules.validate(body, true, now).startAt);
    body.startAt = body.endAt;
    assert.equal(eventRules.validate(body, true, now).endAt, '活動開始時間必須早於結束時間');
    body.startAt = body.registrationDeadline;
    assert.equal(eventRules.validate(body, true, now).registrationDeadline, '報名截止時間必須早於活動開始時間');
    body.title = '  ';
    assert.equal(eventRules.validate(body, true, now).title, '活動名稱不能空白');
    body.title = '一\n二';
    assert.equal(eventRules.validate(body, true, now).title, '活動名稱不能包含換行');
    body.startAt = '0999-12-01T12:00:00';
    assert.equal(eventRules.validate(body, true, now).startAt, '活動日期年份必須介於 1000～9999 年');
});

test('new event deadline must be future while editing may retain an already passed deadline', () => {
    const body = event();
    body.registrationDeadline = '2026-11-20T12:00:00';
    assert.equal(eventRules.validate(body, true, now).registrationDeadline, '活動報名已截止');
    assert.equal(eventRules.validate(body, false, now).registrationDeadline, undefined);
});

test('forms use new rules while login keeps its original password constraints', () => {
    const admin = fs.readFileSync(path.join(staticDir, 'admin-events.html'), 'utf8');
    assert.match(admin, /name="capacity"[^>]*max="50"/);
    assert.match(admin, /name="description"[^>]*maxlength="5000"/);
    assert.ok(admin.indexOf('event-validation.js') < admin.indexOf('admin-events.js'));
    for (const file of ['register.html', 'member-settings.html', 'reset-password.html']) {
        const html = fs.readFileSync(path.join(staticDir, file), 'utf8');
        assert.match(html, /name="password"[^>]*maxlength="30"/);
        assert.ok(!html.includes('15～72'));
    }
    for (const file of ['login.html', 'admin-login.html']) {
        const html = fs.readFileSync(path.join(staticDir, file), 'utf8');
        assert.ok(!/name="password"[^>]*(?:minlength="15"|maxlength="30")/.test(html));
    }
});


test('new account and name rules use consistent boundaries and allowed symbols', () => {
    const rules = context.window.DndMemberRules;
    for (const account of ['jia_123', 'jia-123', 'jia.123', 'a._-', 'a'.repeat(20)]) assert.equal(rules.account(account), '');
    for (const account of ['abc', 'a'.repeat(21), '玩家1234', 'user@name', 'has space', 'test+name', 'test/name']) assert.ok(rules.account(account));
    assert.equal(rules.name('名'.repeat(30)), '');
    assert.equal(rules.name('名'.repeat(31)), '姓名或暱稱最多 30 字元');
    assert.equal(rules.name(' '), '姓名或暱稱必填');
});

test('registration displays account and name rules with a valid HTML pattern', () => {
    const html = fs.readFileSync(path.join(staticDir, 'register.html'), 'utf8');
    assert.ok(html.includes('姓名或暱稱（1～30 字元）'));
    assert.ok(html.includes('帳號（4～20 字元）'));
    assert.ok(html.includes('不可有空白'));
    assert.match(html, /name="account"[^>]*maxlength="20"/);
    assert.match(html, /name="name"[^>]*maxlength="30"/);
    const pattern = html.match(/name="account"[^>]*pattern="([^"]+)"/)[1];
    const expression = new RegExp('^(?:' + pattern + ')$', 'v');
    for (const account of ['jia.123', 'jia-123', 'jia_123']) assert.ok(expression.test(account));
    assert.ok(!expression.test('user@name'));
    assert.ok(html.indexOf('member-rules.js') < html.indexOf('register.js'));
});
