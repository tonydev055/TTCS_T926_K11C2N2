const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync(require('node:path').join(__dirname,'../../web/theo-doi-giao-hang/index.html'),'utf8');
const script=[...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)][0][1];
function setup(api){
  const nodes=new Map();
  function node(key){if(!nodes.has(key)) nodes.set(key,{innerHTML:'',textContent:'',value:'',disabled:false,addEventListener(){},querySelector(sel){return node(sel);},querySelectorAll(){return [];}});return nodes.get(key);}
  const host=node('moduleContent');
  const context={window:{},document:{getElementById:key=>key==='moduleContent'?host:node('#'+key)},apiRequest:api,viewRevision:1,setTimeout,clearTimeout,console};
  vm.runInNewContext(script,context);
  return {context,host,nodes,render:()=>context.window.deliveryTrackingFeature.render()};
}
test('tracking uses portal list and selected detail, rendering actual delivered quantities',async()=>{
  const calls=[];const app=setup(async path=>{calls.push(path);return path==='portal/orders'?[{id:12,code:'DON-THAT',status:'DELIVERED',lineCount:1}]:{id:12,code:'DON-THAT',status:'DELIVERED',lines:[{sku:'SKU-THAT',name:'<img src=x>',unitName:'Cái',quantity:3,deliveredQuantity:2,payable:100}],history:[{toStatus:'DELIVERED',changedAt:'2026-10-15T10:00:00',reason:'Đã giao'}]};});
  await app.render();await new Promise(resolve=>setImmediate(resolve));
  assert.deepEqual(calls,['portal/orders','portal/orders/12']);
  assert.match(app.nodes.get('#dtDetailHost').innerHTML,/SKU-THAT/);
  assert.match(app.nodes.get('#dtDetailHost').innerHTML,/2 \/ 3/);
  assert.match(app.nodes.get('#dtDetailHost').innerHTML,/&lt;img src=x&gt;/);
});
test('empty account does not fetch invented order details',async()=>{
  const calls=[];const app=setup(async path=>{calls.push(path);return [];});await app.render();
  assert.deepEqual(calls,['portal/orders']);assert.match(app.nodes.get('#dtListContainer').innerHTML,/chưa có đơn hàng/);
});
test('API errors remain errors and support retry',async()=>{
  const app=setup(async()=>{throw Error('Không có quyền');});await app.render();
  assert.match(app.host.innerHTML,/Không có quyền/);assert.match(app.host.innerHTML,/Thử lại/);
});
test('late list response cannot replace another view',async()=>{
  let done;const app=setup(()=>new Promise(resolve=>{done=resolve;}));const pending=app.render();app.context.viewRevision++;app.host.innerHTML='Màn hình khác';done([]);await pending;assert.equal(app.host.innerHTML,'Màn hình khác');
});
