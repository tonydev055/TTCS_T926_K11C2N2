const {test}=require('node:test');
const {assert,request,login,fixture,prefix}=require('./helpers.cjs');

test('Path parameters cannot borrow another endpoint permission',async()=>{
  const customer=await login(prefix+'CUSTOMER@example.test');
  for(const endpoint of ['/api/audit;/api/profile','/api/audit/;/api/profile','/api/users;/api/profile','/api/price-history;/api/profile?productId=1']){
    const result=await request(customer,endpoint);
    assert.equal(result.status,403,endpoint);
  }
});

test('Login does not reveal whether an account exists',async(t)=>{
  const {admin,email,id}=await fixture(t);
  assert.equal((await request(admin,`/api/users/${id}/lock`,'POST',{reason:'QA khóa'})).status,200);
  const locked=await request({},'/api/auth/login','POST',{email,password:'WrongPass1'});
  const missing=await request({},'/api/auth/login','POST',{email:prefix+'missing'+Date.now()+'@example.test',password:'WrongPass1'});
  assert.equal(locked.status,401);
  assert.equal(missing.status,401);
  assert.equal(locked.data.message,missing.data.message);
  const correct=await request({},'/api/auth/login','POST',{email,password:'Demo@123'});
  assert.equal(correct.status,423,'Correct password still explains an administrator lock');
});

test('Unknown accounts lock after five failures like real accounts',async()=>{
  const email=prefix+'ghost'+Date.now()+'@example.test';
  for(let i=0;i<5;i++)assert.equal((await request({},'/api/auth/login','POST',{email,password:'WrongPass1'})).status,401);
  assert.equal((await request({},'/api/auth/login','POST',{email,password:'WrongPass1'})).status,423);
});

test('Signed-in payload includes the assigned territory',async(t)=>{
  const {admin,email,id}=await fixture(t,['SALES_REP']);
  const update=await request(admin,'/api/users/'+id,'PUT',{fullName:'QA regression',phone:'0901234567',territory:'Hà Nội - Cầu Giấy',roles:['SALES_REP'],warehouses:[]});
  assert.equal(update.status,200);
  const result=await request({},'/api/auth/login','POST',{email,password:'Demo@123'});
  assert.equal(result.status,200);
  assert.equal(result.data.territory,'Hà Nội - Cầu Giấy');
});
