const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
function setup(){
 const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/admin-events.js'),'utf8');
 const start=source.indexOf('async function loadModerationStatus()');
 const end=source.indexOf('document.getElementById("refresh-moderation-status").addEventListener',start);
 const notice={textContent:'原狀態',attributes:{},setAttribute(k,v){this.attributes[k]=v;},removeAttribute(k){delete this.attributes[k];}};
 const button={textContent:'重新整理審核狀態',disabled:false};
 let resolve,reject,calls=0;
 const pending=new Promise((yes,no)=>{resolve=yes;reject=no;});
 const context={Intl,Date,moderationRefreshing:false,busy:false,document:{getElementById:id=>id==='moderation-status'?notice:button},checked:()=>{calls++;return pending;}};
 vm.createContext(context);vm.runInContext(source.slice(start,end),context);
 return {context,notice,button,resolve,reject,calls:()=>calls};
}
test('refresh immediately shows progress, suppresses duplicate requests and visibly confirms an unchanged status',async()=>{
 const app=setup();const pending=app.context.loadModerationStatus();
 assert.equal(app.button.disabled,true);assert.equal(app.button.textContent,'更新中……');assert.equal(app.notice.attributes['aria-busy'],'true');
 await app.context.loadModerationStatus();assert.equal(app.calls(),1);
 app.resolve({message:'詞庫與 AI 審核正常啟用'});await pending;
 assert.match(app.notice.textContent,/詞庫與 AI 審核正常啟用（已更新：\d{2}:\d{2}:\d{2}，台北時間）/);
 assert.equal(app.button.textContent,'✓ 已更新，再次整理');assert.equal(app.button.disabled,false);assert.equal(app.notice.attributes['aria-busy'],undefined);
});
test('failed refresh clearly reports failure and permits retry',async()=>{
 const app=setup();const pending=app.context.loadModerationStatus();app.reject(new Error('network error'));await pending;
 assert.match(app.notice.textContent,/本次更新失敗/);assert.equal(app.button.textContent,'更新失敗，重新整理');assert.equal(app.button.disabled,false);assert.equal(app.context.moderationRefreshing,false);
});
test('refresh completion preserves the surrounding admin operation lock',async()=>{
 const app=setup();const pending=app.context.loadModerationStatus();app.context.busy=true;app.resolve({message:'狀態正常'});await pending;
 assert.equal(app.button.disabled,true);
});
