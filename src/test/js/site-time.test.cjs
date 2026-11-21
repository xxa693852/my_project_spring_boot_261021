const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const context={window:{},Intl,Date};
vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/site-time.js'),'utf8'),context);
const format=context.window.DndTime.format;
test('Taipei local dates consistently show seconds and discard fractional seconds',()=>{
 assert.equal(format('2026-11-21T14:05'),'2026-11-21 14:05:00');
 assert.equal(format('2026-11-21T14:05:09.123456'),'2026-11-21 14:05:09');
});
test('explicit offsets convert to Taipei independently of browser timezone',()=>{
 assert.equal(format('2026-11-20T16:00:05Z'),'2026-11-21 00:00:05');
 assert.equal(format('2026-11-21T09:00:05+09:00'),'2026-11-21 08:00:05');
});
test('missing and malformed dates display the caller fallback',()=>{
 for(const value of [null,undefined,'','not-a-date']) assert.equal(format(value,'—'),'—');
});
