const {test}=require('node:test');const {assert,request,login,fixture}=require('./helpers.cjs');
test('Changing roles revokes existing session permissions',async t=>{
 const u=await fixture(t,['SALES_MANAGER']),c=await login(u.email);
 await request(c,'/api/auth/change-password','POST',{currentPassword:'Demo@123',newPassword:'Regression123'});
 assert.equal((await request(c,'/api/products/')).status,200);
 assert.equal((await request(u.admin,`/api/users/${u.id}`,'PUT',{fullName:'QA customer',phone:'0901234567',territory:'',roles:['CUSTOMER'],warehouses:[]})).status,200);
 assert.equal((await request(c,'/api/products/')).status,401);
 const fresh=await login(u.email,'Regression123');assert.deepEqual((await request(fresh,'/api/auth/me')).data.roles,['CUSTOMER']);
 assert.equal((await request(fresh,'/api/products/','POST',{})).status,403);
});
