const fs=require('fs'),path=require('path');const resultPath=path.join(__dirname,'../../reports/import-retest-results.json');const prefix=process.env.QA_PREFIX||'qa20261003r1',base='http://localhost:8080/QuanLyBanHangKho',results=[];
function check(name,ok,detail=''){results.push({name,status:ok?'PASS':'FAIL',detail});console.log(`${ok?'PASS':'FAIL'} ${name} ${detail}`);}
async function req(c,url,method='GET',body){const headers={};if(c.cookie)headers.Cookie=c.cookie;if(body&&!(body instanceof FormData)){headers['Content-Type']='application/json';body=JSON.stringify(body);}const r=await fetch(base+url,{method,headers,body});if(r.headers.get('set-cookie'))c.cookie=r.headers.get('set-cookie').split(';')[0];const bytes=Buffer.from(await r.arrayBuffer());let data;try{data=JSON.parse(bytes);}catch{}return {status:r.status,data,bytes};}
async function login(role){const c={};const r=await req(c,'/api/auth/login','POST',{email:prefix+role+'@example.test',password:'Demo@123'});if(r.status!==200)throw Error('Login '+role);return c;}
async function preview(c,kind,file){const f=new FormData();f.append('file',new Blob([fs.readFileSync(path.join(__dirname,file))]),file);return req(c,'/api/imports/'+kind+'/preview','POST',f);}
async function main(){const admin=await login('ADMIN'),manager=await login('SALES_MANAGER'),other=await login('ADMIN');
 for(const [kind,c,file] of [['users',admin,'users.xlsx'],['products',manager,'products.xlsx']]){
 const p=await preview(c,kind,file);check(kind+' XLSX preview valid/invalid row',p.status===200&&p.data.valid===1&&p.data.invalid===1,JSON.stringify({status:p.status,valid:p.data?.valid,invalid:p.data?.invalid}));
 const before=await req(admin,kind==='users'?'/api/users/?search='+prefix+'excel':'/api/products/');check(kind+' preview no persistence',kind==='users'?before.data.total===0:!before.data.some(x=>x.sku===prefix.toUpperCase()+'EXCEL'));
 let r=await req(other,'/api/imports/'+kind+'/commit','POST',{token:p.data.token});check(kind+' token bound to session',r.status===400);
 r=await req(c,'/api/imports/'+kind+'/commit','POST',{token:p.data.token});check(kind+' commit partial success',r.status===200&&r.data.committed&&r.data.valid===1&&r.data.invalid===1);
 r=await req(c,'/api/imports/'+kind+'/commit','POST',{token:p.data.token});check(kind+' token single use',r.status===400);
 const after=await req(admin,kind==='users'?'/api/users/?search='+prefix+'excel':'/api/products/');check(kind+' committed persisted',kind==='users'?after.data.total===1:after.data.some(x=>x.sku===prefix.toUpperCase()+'EXCEL'));
 }
 let r=await req({},'/api/dev-mailbox/?email='+prefix+'excel@example.test');check('Imported user receives activation mail',r.status===200&&r.data.some(m=>m.body.includes('Demo@123')));
 r=await req({},'/api/auth/login','POST',{email:prefix+'excel@example.test',password:'Demo@123'});check('Imported user temporary login',r.status===200&&r.data.requiresPasswordChange);
 r=await preview(admin,'users','users.xlsx');check('Duplicate imported user invalid',r.status===200&&r.data.valid===0&&r.data.invalid===2);
 r=await preview(manager,'products','products.xlsx');check('Existing SKU preview update',r.status===200&&r.data.rows[0].action==='Cập nhật');r=await req(manager,'/api/imports/products/commit','POST',{token:r.data.token});check('Existing SKU update commit',r.status===200&&r.data.valid===1);
 r=await preview(manager,'products','formula.xlsx');check('Formula row rejected',r.status===200&&r.data.invalid===1);
 r=await preview(admin,'products','products.xlsx');check('Admin cannot import cost column',r.status===400||(r.data.valid===0),`HTTP ${r.status}; valid ${r.data?.valid}`);
 const jpg=new FormData();jpg.append('file',new Blob([fs.readFileSync(path.join(__dirname,'sample.jpg'))]),'sample.jpg');r=await req(other,'/api/profile/avatar','POST',jpg);check('JPG avatar accepted',r.status===200);
 const products=await req(manager,'/api/products/');const p=products.data.find(x=>x.sku===prefix.toUpperCase()+'P');const image=new FormData();image.append('file',new Blob([fs.readFileSync(path.join(__dirname,'sample.jpg'))]),'sample.jpg');r=await req(manager,`/api/products/${p.id}/image`,'POST',image);check('Product image upload',r.status===200);r=await req(manager,`/api/products/${p.id}/thumbnail`);check('Product thumbnail PNG',r.status===200&&r.bytes.toString('ascii',1,4)==='PNG');
 r=await req(admin,'/api/users/?search='+prefix+'badrole');check('Invalid role leaves no account',r.data.items.length===0);
}
main().catch(e=>check('Harness completion',false,e.stack)).finally(()=>{fs.writeFileSync(resultPath,JSON.stringify({prefix,results},null,2));console.log(JSON.stringify({pass:results.filter(x=>x.status==='PASS').length,fail:results.filter(x=>x.status==='FAIL').length}));});
