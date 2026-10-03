const assert = require('node:assert/strict');
const prefix = process.env.QA_PREFIX;
const base = process.env.TEST_BASE_URL || 'http://localhost:8080/QuanLyBanHangKho';
async function request(client, endpoint, method='GET', body) {
  const headers = {'Content-Type':'application/json'};
  if (client.cookie) headers.Cookie=client.cookie;
  const response=await fetch(base+endpoint,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});
  if(response.headers.get('set-cookie'))client.cookie=response.headers.get('set-cookie').split(';')[0];
  return {status:response.status,data:await response.json()};
}
async function login(email,password='Demo@123') {
  const client={};const result=await request(client,'/api/auth/login','POST',{email,password});
  assert.equal(result.status,200,JSON.stringify(result.data));return client;
}
async function fixture(t,roles=['CUSTOMER']) {
  assert.match(prefix||'',/^qa[a-zA-Z0-9]+$/,'Set QA_PREFIX to an isolated seeded test namespace');
  const admin=await login(prefix+'ADMIN@example.test');
  const username=prefix+Date.now()+Math.random().toString(36).slice(2,7),email=username+'@example.test';
  const result=await request(admin,'/api/users/','POST',{username,email,fullName:'QA regression',phone:'0901234567',roles,warehouses:[]});
  assert.equal(result.status,201);
  t.after(async()=>{
    await request(admin,'/api/users/'+result.data.id,'DELETE');
    await request({},'/api/dev-mailbox/?email='+encodeURIComponent(email),'DELETE');
  });
  return {admin,email,id:result.data.id};
}
module.exports={assert,request,login,fixture,prefix};
