const {test}=require('node:test');const {assert,request,login,fixture}=require('./helpers.cjs');
test('Admin unlock clears failed attempts and temporary lock',async t=>{
 const u=await fixture(t);for(let i=0;i<5;i++)assert.equal((await request({},'/api/auth/login','POST',{email:u.email,password:'wrong'})).status,401);
 assert.equal((await request({},'/api/auth/login','POST',{email:u.email,password:'Demo@123'})).status,423);
 assert.equal((await request(u.admin,`/api/users/${u.id}/unlock`,'POST',{})).status,200);
 await login(u.email);
 assert.equal((await request({},'/api/auth/login','POST',{email:u.email,password:'wrong'})).status,401);
 await login(u.email);
});
