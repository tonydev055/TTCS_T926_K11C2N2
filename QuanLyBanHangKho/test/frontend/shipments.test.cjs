const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync(require('node:path').join(__dirname,'../../web/xuat-kho-giao-thieu/index.html'),'utf8');
const script=[...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)][0][1];
function setup(post){
  const nodes=new Map(), calls=[];let closed=0;
  const confirm={dataset:{id:'11'}};
  const node=id=>{if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',value:'',addEventListener(){},setAttribute(){},querySelectorAll:sel=>sel==='.xk-confirm-btn'?[confirm]:[]});return nodes.get(id);};
  const button={disabled:false,isConnected:true},error=node('error');
  const form={querySelector:sel=>sel==='button[type="submit"]'?button:sel==='#xkSubmitError'?error:sel==='#xkReason'?{value:'Thiếu hàng'}:{value:'4'},querySelectorAll:()=>[]};
  const data={pending:[{id:11,orderCode:'DH-THAT',customer:'Đại lý thật',warehouse:'HCM',payable:500,lines:[{id:22,sku:'SKU',name:'Hàng',unit:'Cái',orderedQty:5,reservedQty:5,unitPrice:100}]}],shipped:[],shortfall:[]};
  const context={window:{},document:{getElementById:node},warehouseUi:{esc:v=>String(v??''),screen:async(title,desc,load,render)=>render(node('host'),await load(),1)},apiRequest:async(path,options)=>{calls.push({path,options});if(options)return post?.();return data;},jsonRequestOptions:(method,body)=>({method,body}),openFormDialog:()=>({form,close(){closed++;}}),showToast(){},Intl,console};
  vm.runInNewContext(script,context);
  return {context,calls,form,error,confirm,get closed(){return closed;},render:()=>context.window.sprint5Features['Xuat kho & Giao thieu'].render()};
}
test('shipment screen loads real API data',async()=>{const app=setup();await app.render();assert.equal(app.calls[0].path,'shipments');});
test('failed confirmation keeps form open with server error',async()=>{const app=setup(async()=>{throw Error('Không đủ tồn');});await app.render();app.confirm.onclick({stopPropagation(){}});await app.form.onsubmit({preventDefault(){}});assert.equal(app.closed,0);assert.equal(app.error.textContent,'Không đủ tồn');});
test('successful confirmation sends line IDs and quantities then reloads API',async()=>{const app=setup(async()=>({message:'OK'}));await app.render();app.confirm.onclick({stopPropagation(){}});await app.form.onsubmit({preventDefault(){}});assert.equal(app.closed,1);assert.equal(app.calls[1].path,'shipments/11');assert.equal(app.calls[1].options.body.lines[0].lineId,22);assert.equal(app.calls[1].options.body.lines[0].actualQuantity,4);assert.equal(app.calls[2].path,'shipments');});
