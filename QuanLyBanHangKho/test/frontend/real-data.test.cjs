const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

// Exercise the actual renderer without writing fixture records to the database.
function setup() {
  const html = fs.readFileSync(path.join(__dirname,'../../web/index.html'),'utf8');
  const elements = new Map();
  class Element {
    constructor() { this.value=''; this.textContent=''; this.isConnected=true; this.listeners={}; this.classList={add(){},remove(){},toggle(){}}; }
    addEventListener(event,handler) { this.listeners[event]=handler; }
    querySelectorAll() { return []; }
    set innerHTML(value) {
      this.markup=value;
      for (const [,id] of value.matchAll(/id="([^"]+)"/g)) {
        if (elements.has(id)) elements.get(id).isConnected=false;
        elements.set(id,new Element());
      }
    }
    get innerHTML() { return this.markup ?? this.textContent.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  }
  for(const [,id] of html.matchAll(/id="([^"]+)"/g)) elements.set(id,new Element());
  const context=vm.createContext({
    document:{getElementById:id=>elements.get(id),querySelectorAll:()=>[],addEventListener(){},createElement:()=>new Element()},
    sessionStorage:{getItem:()=>null},window:{location:{search:''}},URLSearchParams,Intl,setTimeout,clearTimeout,
    fetch:async()=>({ok:true,json:async()=>[]})
  });
  vm.runInContext(fs.readFileSync(path.join(__dirname,'../../web/assets/app.js'),'utf8'),context);
  return {context,elements,run:source=>vm.runInContext(source,context)};
}

test('empty API data renders no invented rows or totals',async()=>{
  const app=setup();
  await app.run("renderRealModule('Sản phẩm & bảng giá')");
  assert.match(app.elements.get('recordTable').innerHTML,/Chưa có dữ liệu được lưu/);
  assert.equal(app.elements.get('recordCount').textContent,'0 kết quả / 0 bản ghi');
});

test('API errors remain errors instead of becoming empty successful results',async()=>{
  for(const [status,message,expected] of [[500,'ERROR: relation "products" does not exist',/chưa được khởi tạo/],[403,'',/chưa được cấp quyền/],[401,'',/hết hạn/]]) {
    const app=setup();
    app.context.fetch=async()=>({ok:false,status,json:async()=>({message})});
    await app.run("renderRealModule('Đơn hàng')");
    assert.match(app.elements.get('recordTable').innerHTML,expected);
    assert.equal(app.elements.get('recordCount').textContent,'Chưa tải được dữ liệu');
  }
});

test('real response rows support paging, search, zero values, and HTML escaping',async()=>{
  const app=setup();
  app.context.fetch=async()=>({ok:true,json:async()=>Array.from({length:21},(_,id)=>({id,name:id===20?'<img src=x>':'Item '+id,quantity:0,active:false}))});
  await app.run("renderRealModule('Sản phẩm & bảng giá')");
  assert.equal(app.elements.get('recordFooter').textContent,'Trang 1 / 2');
  app.elements.get('recordsNext').onclick();
  assert.match(app.elements.get('recordTable').innerHTML,/&lt;img src=x&gt;/);
  assert.match(app.elements.get('recordTable').innerHTML,/<td>0<\/td><td>Không<\/td>/);
  const search=app.elements.get('recordSearch');search.value='missing';search.listeners.input();
  assert.match(app.elements.get('recordTable').innerHTML,/Không tìm thấy/);
});

test('late responses cannot overwrite a new screen',async()=>{
  const app=setup();let finish;
  app.context.fetch=()=>new Promise(resolve=>{finish=resolve});
  const pending=app.run("renderRealModule('Đơn hàng')");
  app.run('viewRevision++');
  app.elements.get('recordTable').innerHTML='New screen';
  finish({ok:true,json:async()=>[{id:1}]});
  await pending;
  assert.equal(app.elements.get('recordTable').innerHTML,'New screen');
});

test('unsupported modules explicitly show unavailable data',async()=>{
  const app=setup();
  await app.run("renderRealModule('Kiểm kê')");
  assert.match(app.elements.get('moduleContent').innerHTML,/chưa có nguồn dữ liệu/);
});
