// Visual QA only. All writes stay in memory; this server never connects to the database.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../../web');
const user = {id:1,fullName:'Kiểm thử giao diện',email:'preview@example.test',role:'ADMIN',territory:'Dữ liệu mô phỏng',
  permissions:['products.read','products.write','catalog.read']};
const data = {
  categories:[{id:1,code:'PHONE',name:'Điện thoại',parent_id:null},{id:2,code:'SMARTPHONE',name:'Điện thoại thông minh',parent_id:1},
    {id:3,code:'ACCESSORY',name:'Phụ kiện',parent_id:null},{id:4,code:'POWER',name:'Sạc và nguồn',parent_id:3},{id:5,code:'CHARGER',name:'Củ sạc',parent_id:4}],
  brands:[{id:1,code:'APPLE',name:'Apple',active:true},{id:2,code:'SAMSUNG',name:'Samsung',active:true}],
  'product-models':[{id:1,code:'IPHONE15',name:'iPhone 15',category_id:2,brand_id:1,active:true}],
  products:[{id:1,sku:'TEST-IP15-128-BLACK',name:'iPhone 15 128GB Đen',model_id:1,category_id:2,base_unit:'Chiếc',packaging:'',
    active:true,condition:'NEW',attributes:{'Màu sắc':'Đen','Bộ nhớ':'128GB'}},
    {id:2,sku:'TEST-IP15-256-BLUE',name:'iPhone 15 256GB Xanh',model_id:1,category_id:2,base_unit:'Chiếc',packaging:'',
    active:true,condition:'NEW',attributes:{'Màu sắc':'Xanh','Bộ nhớ':'256GB'}}], 'price-lists':[]
};
function enrich(endpoint, item) {
  const model = endpoint === 'products' ? data['product-models'].find(row => row.id === item.model_id) : item;
  return {...item,category_name:data.categories.find(row => row.id === item.category_id)?.name,
    model_name:model?.name,brand_id:model?.brand_id,brand_name:data.brands.find(row => row.id === model?.brand_id)?.name,
    variant_count: data.products.filter(row => row.model_id === item.id).length};
}
http.createServer(async (req,res) => {
  const url = new URL(req.url,'http://localhost');
  const send = (body,status=200) => {res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(body));};
  if (url.pathname.startsWith('/api/')) {
    if (['/api/auth/login','/api/auth/me'].includes(url.pathname)) {send(user);return;}
    if (url.pathname === '/api/auth/mail-config') {send({developmentMailbox:false});return;}
    if (url.pathname === '/api/products/attribute-options') {
      send(data.products.flatMap(item => Object.entries(item.attributes || {}).map(([name,value]) => ({name,value}))));return;
    }
    const [, , endpoint, id] = url.pathname.split('/');
    if (!data[endpoint]) {send({message:'Endpoint không có trong bản xem thử'},404);return;}
    if (req.method !== 'GET') {
      let body='';for await (const chunk of req) body+=chunk;
      const input=JSON.parse(body||'{}');
      const saved=id?data[endpoint].find(row=>row.id===Number(id)):{id:Math.max(0,...data[endpoint].map(row=>row.id))+1};
      Object.assign(saved,input);
      if (endpoint === 'products' && input.model_id) saved.category_id=data['product-models'].find(row=>row.id===input.model_id).category_id;
      if(!id)data[endpoint].push(saved);
      send({id:saved.id,message:'Đã lưu dữ liệu mô phỏng'});return;
    }
    let rows=data[endpoint].map(item=>enrich(endpoint,item));
    const category=url.searchParams.get('category_id');
    if(category) {
      const descendants=new Set([Number(category)]);
      for(let i=0;i<data.categories.length;i++)for(const row of data.categories)if(descendants.has(row.parent_id))descendants.add(row.id);
      rows=rows.filter(row=>descendants.has(row.category_id));
    }
    for(const key of ['brand_id','model_id'])if(url.searchParams.get(key))rows=rows.filter(row=>row[key]===Number(url.searchParams.get(key)));
    if(url.searchParams.get('attribute_name'))rows=rows.filter(row=>row.attributes?.[url.searchParams.get('attribute_name')] && (!url.searchParams.get('attribute_value')||row.attributes[url.searchParams.get('attribute_name')]===url.searchParams.get('attribute_value')));
    if(url.searchParams.get('search'))rows=rows.filter(row=>JSON.stringify(row).toLowerCase().includes(url.searchParams.get('search').toLowerCase()));
    const page=Number(url.searchParams.get('page')||1),size=Number(url.searchParams.get('size')||20);
    send(url.searchParams.has('page')?{items:rows.slice((page-1)*size,page*size),total:rows.length,page,size}:rows);return;
  }
  const file=path.resolve(root,'.'+decodeURIComponent(url.pathname==='/'?'/index.html':url.pathname));
  if(!file.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}
  fs.readFile(file,(error,body)=>{if(error){res.writeHead(404);res.end();return;}
    res.writeHead(200,{'Content-Type':file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html'});res.end(body);});
}).listen(8091,'127.0.0.1',()=>console.log('Visual QA with simulated data: http://localhost:8091'));
