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
    focus() {}
    reset() { for(const element of elements.values()) element.value=''; }
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
  for (const [,src] of html.matchAll(/<script src="([^"?]+)(?:\?[^\"]*)?"/g)) {
    if (src.endsWith('feature-loader.js')) continue;
    vm.runInContext(fs.readFileSync(path.join(__dirname,'../../web',src),'utf8'),context,{filename:src});
  }
  for (const [,src] of html.matchAll(/href="([^"?]+)(?:\?[^\"]*)?" data-feature/g)) {
    const feature=fs.readFileSync(path.join(__dirname,'../../web',src),'utf8');
    for(const [,script] of feature.matchAll(/<script>([\s\S]*?)<\/script>/g)) vm.runInContext(script,context,{filename:src});
  }
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
    await app.run("renderRealModule('Sản phẩm & bảng giá')");
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
  const pending=app.run("renderRealModule('Sản phẩm & bảng giá')");
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

test('Sprint 1-2 menus retain authorized features and exclude later workflows',()=>{
  const app=setup();
  for (const role of ['ADMIN','SALES_MANAGER','SALES_REP','WH_MANAGER','WAREHOUSE','ACCOUNTANT','CUSTOMER']) {
    const permissions=role==='ADMIN'?['admin.users','products.read','suppliers.read']:role==='WAREHOUSE'||role==='WH_MANAGER'?['products.read','suppliers.read']:['products.read'];
    app.run(`activeRole=${JSON.stringify(role)};currentUser={roles:[activeRole],permissions:${JSON.stringify(permissions)}}`);
    const menu=Array.from(app.run('sprint2Menu()'));
    assert.ok(menu.includes('Hồ sơ cá nhân'));
    assert.ok(menu.includes('Đổi mật khẩu'));
    assert.ok(menu.includes('Sản phẩm & bảng giá'));
    assert.equal(menu.includes('Nhập người dùng Excel'),role==='ADMIN');
    assert.equal(menu.includes('Nhật ký hệ thống'),role==='ADMIN');
    for (const removed of ['Đơn hàng','Tồn kho','Nhập kho','Xuất kho','Kiểm kê','Thanh toán','Vai trò & quyền','Cấu hình']) assert.ok(!menu.includes(removed));
  }
});

test('all extracted feature entry points load from index without missing scripts',()=>{
  const app=setup();
  for(const name of ['s2Catalog','s2Profile','s2Audit','s2Import','renderUsersModule','renderRealHome','openForgotPassword']) {
    assert.equal(app.run(`typeof ${name}`),'function',name);
  }
});

test('every role can open and submit password change without admin permissions',async()=>{
  for(const role of ['ADMIN','SALES_MANAGER','SALES_REP','WH_MANAGER','WAREHOUSE','ACCOUNTANT','CUSTOMER']) {
    const app=setup();let calls=0;
    app.run(`activeRole='${role}';currentUser={roles:[activeRole],permissions:[],requiresPasswordChange:true}`);
    assert.equal(app.run("openSprint2View('Đổi mật khẩu')"),true);
    app.elements.get('changeCurrentPassword').value=' old-password ';
    app.elements.get('changeNewPassword').value='NewPassword123';
    app.elements.get('changeConfirmPassword').value='NewPassword123';
    app.context.fetch=async(url,options)=>{
      calls++;
      assert.equal(url,'api/auth/change-password');
      assert.equal(options.method,'POST');
      assert.deepEqual(JSON.parse(options.body),{currentPassword:' old-password ',newPassword:'NewPassword123'});
      assert.equal(app.elements.get('changePasswordSubmit').disabled,true);
      return {ok:true,json:async()=>({message:'Đã đổi mật khẩu'})};
    };
    await app.elements.get('changePasswordForm').onsubmit({preventDefault(){},currentTarget:app.elements.get('changePasswordForm')});
    assert.equal(calls,1);
    assert.equal(app.run('currentUser.requiresPasswordChange'),false);
    assert.equal(app.elements.get('changeCurrentPassword').value,'');
    assert.equal(app.elements.get('changeNewPassword').value,'');
    assert.equal(app.elements.get('changePasswordSuccess').textContent,'Đổi mật khẩu thành công! Các phiên đăng nhập khác đã được đăng xuất.');
    assert.equal(app.elements.get('toast').textContent,'Đổi mật khẩu thành công!');
  }
});

test('password change validates fields and shows API or session errors without reporting success',async()=>{
  const app=setup();let calls=0;
  app.run('currentUser={requiresPasswordChange:true};renderChangePassword()');
  const submit=()=>app.elements.get('changePasswordForm').onsubmit({preventDefault(){},currentTarget:app.elements.get('changePasswordForm')});
  app.context.fetch=async()=>{calls++;throw new Error('Unexpected request');};
  for(const [current,password,confirm,expected] of [
    ['', 'Password123','Password123',/hiện tại/],
    ['old','short1','short1',/8 ký tự/],
    ['old','12345678','12345678',/chữ cái/],
    ['old','Password123','different',/mật khẩu xác nhận không giống nhau/],
    ['old','Password123','x',/mật khẩu xác nhận không giống nhau/]
  ]) {
    app.elements.get('changeCurrentPassword').value=current;
    app.elements.get('changeNewPassword').value=password;
    app.elements.get('changeConfirmPassword').value=confirm;
    await submit();
    assert.match(app.elements.get('changePasswordError').textContent,expected);
  }
  assert.equal(calls,0);
  app.elements.get('changeConfirmPassword').value='Password123';
  for(const [status,message,expected] of [[400,'Mật khẩu hiện tại không đúng',/Mật khẩu cũ không đúng/],[401,'',/hết hạn/]]) {
    app.context.fetch=async()=>({ok:false,status,json:async()=>({message})});
    await submit();
    assert.match(app.elements.get('changePasswordError').textContent,expected);
    assert.equal(app.elements.get('changePasswordSuccess').textContent,'');
    assert.equal(app.elements.get('changePasswordSubmit').disabled,false);
    assert.equal(app.run('currentUser.requiresPasswordChange'),true);
  }
});

test('password change prevents duplicate submissions and ignores navigation during a request',async()=>{
  const app=setup();let finish,calls=0;
  app.run('renderChangePassword()');
  for(const id of ['changeCurrentPassword','changeNewPassword','changeConfirmPassword'])app.elements.get(id).value='Password123';
  const form=app.elements.get('changePasswordForm');
  app.context.fetch=()=>{calls++;return new Promise(resolve=>{finish=resolve});};
  const event={preventDefault(){},currentTarget:form};
  const pending=form.onsubmit(event);
  await form.onsubmit(event);
  assert.equal(calls,1);
  app.elements.get('moduleContent').innerHTML='Another screen';
  finish({ok:true,json:async()=>({message:'Success'})});
  await pending;
  assert.equal(app.elements.get('moduleContent').innerHTML,'Another screen');
});

test('delete user requires confirmation, blocks self and reloads after success',async()=>{
  const app=setup();let calls=0,reloads=0;
  app.context.window.confirm=()=>false;
  app.context.button={dataset:{userId:'20',userName:'test'},disabled:false,isConnected:true};
  app.context.reload=async()=>{reloads++;};
  app.context.fetch=async(url,options)=>{calls++;assert.equal(url,'api/users/20');assert.equal(options.method,'DELETE');return {ok:true,json:async()=>({message:'Deleted'})};};
  app.run('currentUser={id:1}');
  await app.run('deleteUserAccount(button,reload)');
  assert.equal(calls,0);
  app.context.window.confirm=()=>true;
  app.run('currentUser={id:20}');
  await app.run('deleteUserAccount(button,reload)');
  assert.equal(calls,0);
  app.run('currentUser={id:1}');
  await app.run('deleteUserAccount(button,reload)');
  assert.equal(calls,1);assert.equal(reloads,1);assert.equal(app.context.button.disabled,false);
});
