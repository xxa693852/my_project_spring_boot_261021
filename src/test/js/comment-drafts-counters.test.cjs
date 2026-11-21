const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const dir=path.join(__dirname,'../../main/resources/static');
const settled=()=>new Promise(resolve=>setTimeout(resolve,0));
function setup(){
 const all=[];
 class Element {
  constructor(tag='div'){this.tagName=tag;this.children=[];this.handlers={};this.value='';this.dataset={};this.style={};this.attributes={};this.disabled=false;all.push(this);}
  append(...nodes){for(const node of nodes){node.parent=this;this.children.push(node);}}
  after(node){this.afterNode=node;}
  remove(){if(this.parent)this.parent.children=this.parent.children.filter(node=>node!==this);}
  replaceChildren(){this.children=[];}
  setAttribute(key,value){this.attributes[key]=value;}
  getAttribute(key){return this.attributes[key]||null;}
  removeAttribute(key){delete this.attributes[key];}
  addEventListener(key,fn){this.handlers[key]=fn;}
  focus(){} scrollIntoView(){} showModal(){this.open=true;}
  get childElementCount(){return this.children.length;}
  querySelectorAll(selector){const matches=[];const walk=node=>{for(const child of node.children){if(selector.split(',').includes(child.tagName))matches.push(child);walk(child);}};walk(this);return matches;}
  querySelector(selector){return this.querySelectorAll(selector)[0];}
 }
 const ids={};for(const id of ['comment-access','comment-login','comment-form','comment-body','comment-message','comment-list','comment-pages','comment-previous','comment-next','comment-page-number','delete-comment-dialog'])ids[id]=new Element(id==='comment-body'?'textarea':id==='comment-form'?'form':'div');
 const section=new Element();section.append(...Object.values(ids));
 const document={querySelector:()=>section,getElementById:id=>ids[id],createElement:tag=>new Element(tag),addEventListener(){},querySelectorAll:selector=>selector==='[data-char-limit]'?all.filter(node=>node.dataset.charLimit):all.filter(node=>node.className==='field-help character-count')};
 let rejection=429;
 const calls=[];
 const result={canPost:true,canModerate:false,open:true,comments:[{id:7,authorName:'玩家',body:'原始留言',createdAt:'2026-12-01T12:00:00',canDelete:true}],totalPages:1};
 const auth={session:()=>true,loginUrl:()=>'/login.html',async request(url,options={}){calls.push({url,options});return options.method?{ok:false,status:rejection,json:async()=>({message:'內容不符合發布規範，請調整後再試。'})}:{ok:true,json:async()=>result};},errorMessage:async response=>(await response.json()).message};
 const context={window:{},document,location:{search:''},URLSearchParams,setTimeout,DndAuth:auth,renderPageJump(){}};
 vm.runInNewContext(fs.readFileSync(path.join(dir,'site-time.js'),'utf8'),context);
 context.DndTime=context.window.DndTime;
 vm.runInNewContext(fs.readFileSync(path.join(dir,'field-counters.js'),'utf8'),context);
 context.DndCounters=context.window.DndCounters;
 return {context,ids,all,Element,calls,load(){vm.runInNewContext(fs.readFileSync(path.join(dir,'event-comments.js'),'utf8'),context);context.window.loadEventComments(1);}};
}

test('counters update on input, pasted text, programmatic loading and tags while excluding every password form',()=>{
 const app=setup(),counters=app.context.DndCounters;
 const input=new app.Element('textarea');input.id='description';input.dataset.charLimit='5000';
 counters.refresh();assert.equal(input.afterNode.textContent,'0／5000');
 input.value='中文😀';input.handlers.input();assert.equal(input.afterNode.textContent,'4／5000');
 input.value='既有活動';counters.refresh();assert.equal(input.afterNode.textContent,'4／5000');
 const tags=new app.Element('textarea');counters.attach(tags,16,true);tags.value='新手\n'+'字'.repeat(17);tags.handlers.input();assert.match(tags.afterNode.textContent,/標籤 2／5/);assert.match(tags.afterNode.textContent,/17／16/);
 for(const key of ['password','confirmPassword','oldPassword']){const secret=new app.Element('input');secret.type='text';secret.name=key;secret.value='secret';counters.attach(secret,30);assert.equal(secret.afterNode,undefined);}
 const secret=new app.Element('input');secret.type='password';counters.attach(secret,30);assert.equal(secret.afterNode,undefined);
});

test('rejected publication and edit preserve the exact draft and restore controls',async()=>{
 const app=setup();app.load();await settled();
 app.ids['comment-body'].value='尚未送出的原稿';
 app.ids['comment-form'].handlers.submit({preventDefault(){}});await settled();
 assert.equal(app.ids['comment-body'].value,'尚未送出的原稿');
 assert.equal(app.ids['comment-body'].disabled,false);
 assert.equal(app.ids['comment-message'].textContent,'內容不符合發布規範，請調整後再試。');
 const edit=app.all.find(node=>node.textContent==='編輯留言');edit.handlers.click();
 const editor=app.ids['comment-list'].children[0].querySelector('form');
 const draft=editor.querySelector('textarea');draft.value='編輯被拒絕仍須保留';
 editor.handlers.submit({preventDefault(){}});await settled();
 assert.equal(editor.querySelector('textarea'),draft);assert.equal(draft.value,'編輯被拒絕仍須保留');assert.equal(draft.disabled,false);
 assert.ok(app.calls.some(call=>call.options.method==='POST'));assert.ok(app.calls.some(call=>call.options.method==='PUT'));
});
