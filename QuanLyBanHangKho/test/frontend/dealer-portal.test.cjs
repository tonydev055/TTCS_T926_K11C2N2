
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
function setup(){
 const elements=new Map();
 for(const id of ['dealerPortal','dpSubmit','dpSub','dpDisc','dpPay','dpAfter','dpCredit','dpCart','dpCartCount','dpPoint','dpDate','dpNote']){
   elements.set(id,{disabled:false,textContent:'',innerHTML:'',value:id==='dpPoint'?'1':id==='dpDate'?'2099-01-01':'',classList:{toggle(){}},querySelectorAll:()=>[]});
 }
 const pending=[],timers=new Map();let timerId=0;
 const context=vm.createContext({window:{},document:{getElementById:id=>elements.get(id)||null},escapeHtml:s=>String(s),jsonRequestOptions:(method,body)=>({method,body}),showToast(){},confirm:()=>true,setTimeout(fn){timers.set(++timerId,fn);return timerId},clearTimeout(id){timers.delete(id)},apiRequest:(url,options)=>new Promise((resolve,reject)=>pending.push({url,options,resolve,reject}))});
 const html=fs.readFileSync(path.join(__dirname,'../../web/dat-hang/index.html'),'utf8');
 const hooks="return { render, runQuote, scheduleQuote, renderCart, submit, init(){generation=1;me={canOrder:true,credit:{creditLimit:1000,outstanding:0,available:1000}};cart=[{productId:1,unitId:1,quantity:1,name:'SKU',sku:'SKU',units:[{id:1,name:'cai'}]}];}, quantity(value){cart[0].quantity=value;}, remove(){cart=[];renderCart(1);}, current(){return quote;} };";
 const source=html.match(/<script>([\s\S]*?)<\/script>/)[1].replace('return { render };',hooks);
 vm.runInContext(source,context);const api=context.window.dealerPortalFeature;api.init();
 return {api,pending,elements,timers};
}
const quote={lines:[{unitPrice:80,payable:80}],subtotal:80,discount:0,payable:80,stock:{ok:true},credit:{creditLimit:1000,outstanding:0,available:1000,remainingAfterOrder:920}};
test('late quote cannot enable submit after quantity changes',async()=>{
 const app=setup();const old=app.api.runQuote(1);app.api.quantity(2);app.api.scheduleQuote(1);
 app.pending[0].resolve(quote);await old;
 assert.equal(app.api.current(),null);assert.equal(app.elements.get('dpSubmit').disabled,true);
});
test('removing last line discards an in-flight quote',async()=>{
 const app=setup();const old=app.api.runQuote(1);app.api.remove();app.pending[0].resolve(quote);await old;
 assert.equal(app.api.current(),null);assert.equal(app.elements.get('dpSubmit').disabled,true);
});
test('quote failure after cart changes keeps submit disabled',async()=>{
 const app=setup();let first=app.api.runQuote(1);app.pending[0].resolve(quote);await first;
 app.api.quantity(2);app.api.scheduleQuote(1);const next=app.api.runQuote(1);
 app.pending[1].reject(new Error('API unavailable'));await next;
 assert.equal(app.api.current(),null);assert.equal(app.elements.get('dpSubmit').disabled,true);
});
test('duplicate submit does not create a second order',async()=>{
 const app=setup();const first=app.api.runQuote(1);app.pending[0].resolve(quote);await first;
 const sending=app.api.submit(1);const second=app.api.submit(1);
 assert.equal(app.pending.filter(x=>x.url==='portal/orders').length,1);
 app.pending.find(x=>x.url==='portal/orders').reject(new Error('API unavailable'));await sending;await second;
});
