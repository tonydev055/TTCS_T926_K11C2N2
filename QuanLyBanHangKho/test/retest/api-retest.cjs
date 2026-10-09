const fs=require('node:fs');
const path=require('node:path');
const base=process.env.TEST_BASE_URL||'http://localhost:8080/QuanLyBanHangKho';
const prefix=process.env.QA_PREFIX||'qa'+Date.now();
const results=[],fixtures=[];
function check(name,ok,detail=''){results.push({name,status:ok?'PASS':'FAIL',detail});console.log(`${ok?'PASS':'FAIL'} ${name} ${detail}`);}
function client(){return {cookie:''};}
async function req(c,url,method='GET',body){const headers={};if(c.cookie)headers.Cookie=c.cookie;let payload=body;if(body && !(body instanceof FormData)){headers['Content-Type']='application/json';payload=JSON.stringify(body);}const r=await fetch(base+url,{method,headers,body:payload,redirect:'manual'});const cookie=r.headers.get('set-cookie');if(cookie)c.cookie=cookie.split(';')[0];const bytes=Buffer.from(await r.arrayBuffer());let data;try{data=JSON.parse(bytes.toString());}catch{data=bytes.toString();}return {status:r.status,data,bytes};}
async function expect(name,c,url,method,body,status){const r=await req(c,url,method,body);check(name,Array.isArray(status)?status.includes(r.status):r.status===status,`HTTP ${r.status}; expected ${status}`);return r;}
async function login(c,email,password='Demo@123'){return req(c,'/api/auth/login','POST',{email,password});}
async function createUser(admin,suffix,roles=['CUSTOMER'],extra={}){const data={username:prefix+suffix,email:prefix+suffix+'@example.test',fullName:'Kiểm thử '+suffix,phone:'0901234567',roles,warehouses:[],...extra};const r=await req(admin,'/api/users/','POST',data);if(r.data.id)fixtures.push({type:'users',id:r.data.id,email:data.email});return {...data,...r.data,status:r.status};}
async function main(){
 const anon=client(),admin=client(),manager=client();
 await expect('Health',anon,'/api/health','GET',null,200);
 for(const p of ['/api/auth/me','/api/users/','/api/products/','/api/profile/','/api/audit/'])await expect('Anonymous denied '+p,anon,p,'GET',null,401);
 const a=await login(admin,prefix+'ADMIN@example.test');check('Admin login',a.status===200);if(a.status!==200)throw Error('Admin login unavailable');
 const roleEmails={ADMIN:'admin',SALES_MANAGER:'sales.manager',SALES_REP:'sales',WH_MANAGER:'warehouse.manager',WAREHOUSE:'warehouse',ACCOUNTANT:'accountant',CUSTOMER:'customer'};
 for(const [role,email] of Object.entries(roleEmails)){const c=role==='ADMIN'?admin:role==='SALES_MANAGER'?manager:client();const r=await login(c,prefix+role+'@example.test');check('Login '+role,r.status===200&&r.data.roles?.includes(role));if(r.status!==200)continue;await expect('Read products '+role,c,'/api/products/','GET',null,200);await expect('Read profile '+role,c,'/api/profile/','GET',null,200);await expect('Users permission '+role,c,'/api/users/?size=1','GET',null,role==='ADMIN'?200:403);await expect('Audit permission '+role,c,'/api/audit/','GET',null,role==='ADMIN'?200:403);await expect('Users template permission '+role,c,'/api/templates/users','GET',null,role==='ADMIN'?200:403);await expect('Product template permission '+role,c,'/api/templates/products','GET',null,['ADMIN','SALES_MANAGER'].includes(role)?200:403);await expect('Suppliers permission '+role,c,'/api/suppliers/','GET',null,['ADMIN','WH_MANAGER','WAREHOUSE'].includes(role)?200:403);}
 await expect('Unknown login',anon,'/api/auth/login','POST',{email:prefix+'missing',password:'Wrong123'},401);
 const u=await createUser(admin,'auth');check('Admin creates user',u.status===201);const c1=client(),c2=client();let r=await login(c1,u.email);check('Temporary password flagged',r.status===200&&r.data.requiresPasswordChange===true);
 await expect('Temporary password must be changed before catalog access',c1,'/api/products/','GET',null,[401,403]);
 await login(c2,u.email);
 await expect('Reject weak password',c1,'/api/auth/change-password','POST',{currentPassword:'Demo@123',newPassword:'123'},400);
 await expect('Reject wrong current password',c1,'/api/auth/change-password','POST',{currentPassword:'Wrong123',newPassword:'QaPassword456'},400);
 await expect('Change password',c1,'/api/auth/change-password','POST',{currentPassword:'Demo@123',newPassword:'QaPassword456'},200);
 await expect('Other session revoked',c2,'/api/auth/me','GET',null,401);
 await expect('Current session retained',c1,'/api/auth/me','GET',null,200);
 await expect('Profile valid update',c1,'/api/profile/','PUT',{full_name:'Nguyễn Kiểm Thử',phone:'+84901234567'},200);
 r=await req(c1,'/api/profile/');check('Profile persisted Unicode',r.data.full_name==='Nguyễn Kiểm Thử'&&r.data.phone==='+84901234567');
 await expect('Profile blocks role escalation',c1,'/api/profile/','PUT',{full_name:'Test',phone:'0901234567',roles:['ADMIN']},400);
 await expect('Profile rejects phone',c1,'/api/profile/','PUT',{full_name:'Test',phone:'123'},400);
 await expect('Profile rejects empty name',c1,'/api/profile/','PUT',{full_name:'',phone:'0901234567'},400);
 const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j5WQAAAAASUVORK5CYII=','base64');
 async function upload(name,bytes,filename,status){const f=new FormData();f.append('file',new Blob([bytes]),filename);return expect(name,c1,'/api/profile/avatar','POST',f,status);}
 await upload('Avatar PNG upload',png,'avatar.png',200);r=await req(c1,'/api/profile/avatar');check('Avatar resized 256x256',r.status===200&&r.bytes.readUInt32BE(16)===256&&r.bytes.readUInt32BE(20)===256);r=await req(c1,'/api/profile/thumbnail');check('Thumbnail 64x64',r.status===200&&r.bytes.readUInt32BE(16)===64);
 await upload('Reject fake PNG',Buffer.from('not image'),'fake.png',400);await upload('Reject avatar over 2 MB',Buffer.alloc(2097153),'large.png',400);
 await expect('Lock requires reason',admin,`/api/users/${u.id}/lock`,'POST',{},400);
 await expect('Admin locks user',admin,`/api/users/${u.id}/lock`,'POST',{reason:'QA test'},200);
 await expect('Locked session revoked',c1,'/api/auth/me','GET',null,401);r=await login(client(),u.email,'QaPassword456');check('Locked login denied',r.status===423);
 await expect('Admin unlocks user',admin,`/api/users/${u.id}/unlock`,'POST',{},200);
 for(let i=1;i<=5;i++)await expect('Wrong password '+i,anon,'/api/auth/login','POST',{email:u.email,password:'bad'},401);
 r=await login(client(),u.email,'QaPassword456');check('Locked after five failures',r.status===423);
 await expect('Unlock temporary lock',admin,`/api/users/${u.id}/unlock`,'POST',{},200);r=await login(client(),u.email,'QaPassword456');check('Admin unlock clears temporary lock',r.status===200,`HTTP ${r.status}`);
 await expect('Forgot existing',anon,'/api/auth/forgot-password','POST',{email:u.email},200);r=await req(anon,'/api/dev-mailbox/?email='+encodeURIComponent(u.email));const message=r.data.find?.(x=>x.action_url);check('Reset email created with context',!!message?.action_url?.includes('/QuanLyBanHangKho/index.html?resetToken='));
 if(message){const token=new URL(message.action_url).searchParams.get('resetToken');await expect('Reset valid token',anon,'/api/auth/reset-password','POST',{token,password:'QaReset789'},200);await expect('Reset token one use',anon,'/api/auth/reset-password','POST',{token,password:'QaReset789'},400);}
 await expect('Reset invalid token',anon,'/api/auth/reset-password','POST',{token:'invalid',password:'QaReset789'},400);
 await expect('Forgot unknown generic response',anon,'/api/auth/forgot-password','POST',{email:prefix+'none@example.test'},200);
 r=await login(c1,u.email,'QaReset789');check('Login reset password',r.status===200);
 await expect('Logout',c1,'/api/auth/logout','POST',{},200);await expect('Logout invalidates session',c1,'/api/auth/me','GET',null,401);
 r=await req(admin,'/api/users/?search='+prefix+'&size=1');check('Users search pagination',r.status===200&&r.data.items.length===1&&r.data.total>=1);
 await expect('Admin cannot self lock',admin,`/api/users/${a.data.id}/lock`,'POST',{reason:'test'},400);await expect('Admin cannot self delete',admin,`/api/users/${a.data.id}`,'DELETE',null,400);
 const roleUser=await createUser(admin,'role',['SALES_MANAGER']);const old=client();await login(old,roleUser.email);await req(old,'/api/auth/change-password','POST',{currentPassword:'Demo@123',newPassword:'QaRole123'});await expect('Admin changes role',admin,`/api/users/${roleUser.id}`,'PUT',{fullName:'QA role',phone:'0901234567',territory:'',roles:['CUSTOMER'],warehouses:[]},200);r=await req(old,'/api/templates/products');check('Removed role no longer grants permissions',r.status===401||r.status===403,`HTTP ${r.status}`);
 const badRole=await createUser(admin,'badrole',['NOT_A_ROLE']);check('Reject nonexistent role',badRole.status===400,`HTTP ${badRole.status}`);
 const badMail=await createUser(admin,'badmail',['CUSTOMER'],{email:prefix+'invalid'});check('Reject malformed email at admin create',badMail.status===400,`HTTP ${badMail.status}`);
 const reg={username:prefix+'reg',email:prefix+'reg@example.test',fullName:'Tự đăng ký',phone:'0901234567',password:'QaRegister123'};
 r=await expect('Self registration',anon,'/api/auth/register','POST',reg,201);if(r.data.id)fixtures.push({type:'users',id:r.data.id,email:reg.email});await expect('Duplicate registration',anon,'/api/auth/register','POST',reg,409);await expect('Weak registration password',anon,'/api/auth/register','POST',{...reg,username:prefix+'weak',email:prefix+'weak@example.test',password:'123'},400);
 const rc=client();r=await login(rc,reg.email,reg.password);check('Registration only CUSTOMER',r.status===200&&r.data.roles.length===1&&r.data.roles[0]==='CUSTOMER');
 async function save(type,data,c=manager){const r=await req(c,'/api/'+type+'/','POST',data);check('Create '+type,r.status===200,`HTTP ${r.status}`);if(r.data.id)fixtures.push({type,id:r.data.id});return r.data.id;}
 const category=await save('categories',{code:prefix+'C',name:'QA nhóm'});const child=await save('categories',{code:prefix+'D',name:'QA con',parent_id:category});
 await expect('Category cycle blocked',manager,`/api/categories/${category}`,'PUT',{code:prefix+'C',name:'QA nhóm',parent_id:child},400);await expect('Category with child deletion blocked',manager,`/api/categories/${category}`,'DELETE',null,400);
 const product={sku:prefix+'P',name:'QA sản phẩm',category_id:child,base_unit:'lon',packaging:'24 lon',cost_price:12000,active:true};const p=await save('products',product);
 await expect('Duplicate SKU rejected',manager,'/api/products/','POST',product,400);
 await expect('Admin cost write denied',admin,`/api/products/${p}`,'PUT',product,403);
 r=await req(admin,`/api/products/${p}`);check('Admin cost hidden',r.status===200&&!('cost_price' in r.data[0]));r=await req(manager,`/api/products/${p}`);check('Sales manager cost visible',r.data[0]?.cost_price===12000);
 await expect('Base unit immutable',manager,`/api/products/${p}`,'PUT',{...product,base_unit:'kg'},400);await expect('Negative cost rejected',manager,`/api/products/${p}`,'PUT',{...product,cost_price:-1},400);
 await expect('Update product',manager,`/api/products/${p}`,'PUT',{...product,name:'QA sửa',active:false},200);r=await req(rc,`/api/products/${p}`);check('Customer cannot see inactive product',r.status===404);
 const unit=await save('product-units',{product_id:p,name:'thùng',factor:24},admin);r=await req(admin,`/api/product-units/${unit}`,'PUT',{product_id:p,name:'thùng',factor:30});check('Unit update creates version',r.status===200&&r.data.id!==unit);if(r.data.id)fixtures.push({type:'product-units',id:r.data.id});await expect('Zero conversion rejected',admin,'/api/product-units/','POST',{product_id:p,name:'lốc',factor:0},400);
 const supplier=await save('suppliers',{code:prefix+'S',name:'QA NCC',tax_code:'0123456789',contact_name:'QA',phone:'0901234567',payment_terms:'30 ngày'},admin);
 const group=await save('customer-groups',{code:prefix+'G',name:'QA nhóm KH'});const price={name:prefix+' price',customer_group_id:group,valid_from:'2031-01-01',valid_to:'2031-01-31',lines:[{product_id:p,price:20000,floor_price:15000}]};await save('price-lists',price);await expect('Overlapping price dates denied',manager,'/api/price-lists/','POST',price,400);await expect('Floor above sale price denied',manager,'/api/price-lists/','POST',{...price,valid_from:'2031-02-01',valid_to:'2031-02-28',lines:[{product_id:p,price:10,floor_price:11}]},400);r=await req(rc,'/api/price-lists/');check('Customer without group sees no prices',r.status===200&&r.data.length===0);
 r=await req(admin,`/api/audit/?actor=${a.data.id}&type=suppliers`);check('Audit actor/type filters',r.status===200&&r.data.items.some(x=>x.object_id===String(supplier))&&r.data.items.every(x=>x.object_type==='suppliers'&&x.actor_user_id===a.data.id));
 r=await req(admin,'/api/audit/?type=products');check('Audit hides cost',r.status===200&&r.data.items.every(x=>!x.old_value?.cost_price&&!x.new_value?.cost_price));await expect('Audit date range',admin,'/api/audit/?from=2099-01-01&to=2099-12-31','GET',null,200);await expect('Audit invalid date',admin,'/api/audit/?from=invalid','GET',null,400);
 await expect('Commit without preview denied',admin,'/api/imports/users/commit','POST',{token:'invalid'},400);const f=new FormData();f.append('file',new Blob(['fake']),'fake.xls');await expect('Reject XLS extension',admin,'/api/imports/users/preview','POST',f,400);
}
main().catch(e=>{check('Harness completion',false,e.stack);}).finally(()=>{fs.writeFileSync(path.join(__dirname,'../../reports/api-retest-results.json'),JSON.stringify({date:new Date().toISOString(),base,prefix,results,fixtures},null,2));console.log(JSON.stringify({pass:results.filter(x=>x.status==='PASS').length,fail:results.filter(x=>x.status==='FAIL').length,prefix}));});
