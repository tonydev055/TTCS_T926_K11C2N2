const {test}=require('node:test');
const {assert,request,login,fixture,prefix}=require('./helpers.cjs');
test('Temporary password blocks protected APIs until changed',async t=>{
  const u=await fixture(t),c=await login(u.email);
  for(const endpoint of ['/api/products/','/api/profile/','/api/price-lists/','/api/products/1/api/auth/login']){
    const r=await request(c,endpoint);assert.equal(r.status,403,endpoint);
  }
  assert.equal((await request(c,'/api/auth/me')).status,200);
  assert.equal((await request(c,'/api/auth/change-password','POST',{currentPassword:'Demo@123',newPassword:'Regression123'})).status,200);
  assert.equal((await request(c,'/api/products/')).status,200);
  assert.equal((await request(c,'/api/profile/')).status,200);
});
test('Self-selected registration password is not temporary',async t=>{
  const admin=await login(prefix+'ADMIN@example.test'),username=prefix+'self'+Date.now(),email=username+'@example.test';
  const r=await request({},'/api/auth/register','POST',{username,email,fullName:'QA self',phone:'0901234567',password:'Register123'});assert.equal(r.status,201);
  t.after(()=>request(admin,'/api/users/'+r.data.id,'DELETE'));
  const c=await login(email,'Register123');assert.equal((await request(c,'/api/auth/me')).data.requiresPasswordChange,false);assert.equal((await request(c,'/api/products/')).status,200);
});
