const {test}=require('node:test');const {assert,request,login,prefix}=require('./helpers.cjs');
test('Admin create and self registration reject invalid account fields',async()=>{
 const admin=await login(prefix+'ADMIN@example.test');let n=0;
 for(const invalid of [{email:'invalid'},{fullName:' '},{username:'!'},{phone:'123'}]){
  const username=prefix+'validation'+Date.now()+(n++),body={username,email:username+'@example.test',fullName:'QA',phone:'0901234567',roles:['CUSTOMER'],warehouses:[],password:'Register123',...invalid};
  for(const [c,url] of [[admin,'/api/users/'],[{},'/api/auth/register']])assert.equal((await request(c,url,'POST',body)).status,400,JSON.stringify(invalid));
  assert.equal((await request(admin,'/api/users/?search='+username)).data.total,0);
 }
});
