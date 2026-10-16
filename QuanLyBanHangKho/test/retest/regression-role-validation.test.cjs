const {test}=require('node:test');const {assert,request,fixture,prefix}=require('./helpers.cjs');
test('Unknown role or warehouse is rejected without partial writes',async t=>{
 const u=await fixture(t);const data={fullName:'QA',phone:'0901234567',territory:'',roles:['NOT_A_ROLE'],warehouses:[]};
 assert.equal((await request(u.admin,`/api/users/${u.id}`,'PUT',data)).status,400);
 assert.deepEqual((await request(u.admin,`/api/users/${u.id}`)).data[0].roles,['CUSTOMER']);
 for(const extra of [{roles:['NOT_A_ROLE'],warehouses:[]},{roles:['WAREHOUSE'],warehouses:['MISSING_QA_WAREHOUSE']}]){
  const username=prefix+'invalid'+Date.now();const r=await request(u.admin,'/api/users/','POST',{...data,...extra,username,email:username+'@example.test'});assert.equal(r.status,400);
  assert.equal((await request(u.admin,'/api/users/?search='+username)).data.total,0);
 }
});
