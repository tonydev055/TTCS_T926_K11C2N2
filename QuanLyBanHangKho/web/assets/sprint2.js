const s2 = {
  endpoint:'products', rows:[], page:1, request:0,
  labels:{products:'Sản phẩm',categories:'Nhóm hàng','product-units':'Quy đổi đơn vị','customer-groups':'Nhóm khách hàng','price-lists':'Bảng giá',suppliers:'Nhà cung cấp'},
  permissions:{products:'products.write',categories:'products.write','product-units':'units.write','customer-groups':'prices.write','price-lists':'prices.write',suppliers:'suppliers.write'},
  columns:{products:[['sku','SKU'],['name','Sản phẩm'],['category_name','Nhóm hàng'],['base_unit','Đơn vị cơ sở'],['packaging','Đóng gói'],['cost_price','Giá vốn'],['active','Trạng thái']],categories:[['code','Mã nhóm'],['name','Tên nhóm'],['parent_id','Nhóm cha']], 'product-units':[['sku','SKU'],['product_name','Sản phẩm'],['name','Đơn vị'],['factor','Hệ số quy đổi']],suppliers:[['code','Mã NCC'],['name','Nhà cung cấp'],['tax_code','Mã số thuế'],['contact_name','Liên hệ'],['phone','Điện thoại'],['payment_terms','Thanh toán'],['active','Trạng thái']], 'customer-groups':[['code','Mã nhóm'],['name','Nhóm khách hàng']], 'price-lists':[['name','Bảng giá'],['group_name','Nhóm khách hàng'],['valid_from','Từ ngày'],['valid_to','Đến ngày'],['version','Phiên bản'],['active','Áp dụng'],['locked','Đã có đơn']]}
};
function s2Can(permission) { return (currentUser.permissions || []).includes(permission); }
function sprint2Menu() {
  const roles=currentUser.roles || [activeRole];
  const menu=[...new Set(roles.flatMap(role=>roleConfigs[role]?.menu || []))];
  if(s2Can('products.read')&&!menu.includes('Sản phẩm & bảng giá'))menu.splice(1,0,'Sản phẩm & bảng giá');
  if(s2Can('suppliers.read')&&!menu.includes('Nhà cung cấp'))menu.push('Nhà cung cấp');
  if(s2Can('admin.users'))menu.push('Nhập người dùng Excel');
  menu.push('Hồ sơ cá nhân');
  return menu;
}
function openSprint2View(view) {
  if(view==='Sản phẩm & bảng giá'){s2Catalog('products');return true;}
  if(view==='Nhà cung cấp'){s2Catalog('suppliers');return true;}
  if(view==='Danh mục dùng chung'){s2Catalog('categories');return true;}
  if(view==='Hồ sơ cá nhân'){s2Profile();return true;}
  if(view==='Nhật ký hệ thống'){s2Audit();return true;}
  if(view==='Nhập người dùng Excel'){s2Import('users');return true;}
  return false;
}
async function s2Api(path,options={}) {
  const response=await fetch(`api/${path}`,{cache:'no-store',...options});
  const data=await response.json().catch(()=>({message:'Máy chủ trả về dữ liệu không hợp lệ'}));
  if(!response.ok)throw new Error(response.status===401?'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.':data.message || 'Không thể xử lý yêu cầu');
  return data;
}
function s2Json(method,data){return {method,headers:{'Content-Type':'application/json'},body:JSON.stringify(data)};}
function s2Error(error){showToast(error.message || 'Không thể xử lý yêu cầu');}
const s2Money=value=>value==null?'—':new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(value);

async function s2Catalog(endpoint) {
  const revision=viewRevision,request=++s2.request;s2.endpoint=endpoint;s2.page=1;
  const writable=s2Can(s2.permissions[endpoint]);
  const tabs=endpoint==='suppliers'?['suppliers']:['products','categories','product-units','customer-groups','price-lists'];
  moduleContent.innerHTML=`<section class="module-head"><div><span class="eyebrow">DANH MỤC</span><h2>${s2.labels[endpoint]}</h2><p>Thông tin được lưu trong hệ thống và áp dụng theo quyền của bạn.</p></div><div class="module-actions">${endpoint==='products'&&writable?'<button class="outline-button" id="s2Import">Nhập Excel</button>':''}${writable?'<button class="orange-button" id="s2Create">+ Thêm mới</button>':''}</div></section><div class="s2-tabs">${tabs.map(key=>`<button data-catalog="${key}" class="${key===endpoint?'selected':''}">${s2.labels[key]}</button>`).join('')}</div><section class="inventory-panel"><div class="inventory-toolbar"><label class="search-control"><span>⌕</span><input type="search" id="s2Search" placeholder="Tìm mã, tên hoặc thông tin..."></label><select id="s2Status" class="filter-select"><option value="">Tất cả trạng thái</option><option value="true">Đang hoạt động</option><option value="false">Ngừng hoạt động</option></select><button class="outline-button" id="s2Reload">Tải lại</button><span id="s2Count" class="filter-result"></span></div><div id="s2Table" class="table-scroll" aria-live="polite"><p class="s2-empty">Đang tải dữ liệu...</p></div><div class="inventory-footer"><span id="s2Footer"></span><div id="s2Pages" class="pagination"></div></div></section>`;
  const host=document.getElementById('s2Table');
  moduleContent.querySelectorAll('[data-catalog]').forEach(button=>button.onclick=()=>s2Catalog(button.dataset.catalog));
  document.getElementById('s2Reload').onclick=()=>s2Catalog(endpoint);
  document.getElementById('s2Create')?.addEventListener('click',()=>s2Edit(endpoint));
  document.getElementById('s2Import')?.addEventListener('click',()=>s2Import('products'));
  try{
    const rows=await s2Api(`${endpoint}/`);if(revision!==viewRevision||request!==s2.request||!host.isConnected)return;
    s2.rows=rows;
    const columns=s2.columns[endpoint].filter(([key])=>key!=='cost_price'||s2Can('products.cost'));
    const display=(row,key)=>{
      const value=row[key];if(key==='active')return value?'Đang hoạt động':'Ngừng hoạt động';if(key==='locked')return value?'Đã khóa nội dung':'Chưa phát sinh đơn';if(key==='cost_price')return s2Money(value);
      if(key==='parent_id')return rows.find(item=>item.id===value)?.name || 'Nhóm gốc';
      if(key==='factor')return `× ${number(value)}`;
      return value==null?'—':String(value);
    };
    const draw=()=>{
      const query=document.getElementById('s2Search').value.trim().toLocaleLowerCase('vi'),status=document.getElementById('s2Status').value;
      const filtered=rows.filter(row=>(!status||row.active==null||String(row.active)===status)&&columns.some(([key])=>display(row,key).toLocaleLowerCase('vi').includes(query)));
      const pageCount=Math.max(1,Math.ceil(filtered.length/20));s2.page=Math.min(s2.page,pageCount);
      document.getElementById('s2Count').textContent=`${number(filtered.length)} bản ghi`;
      document.getElementById('s2Footer').textContent=`Trang ${s2.page} / ${pageCount}`;
      host.innerHTML=filtered.length?`<table class="feature-table s2-table"><thead><tr>${columns.map(([,label])=>`<th>${label}</th>`).join('')}<th>Thao tác</th></tr></thead><tbody>${filtered.slice((s2.page-1)*20,s2.page*20).map(row=>`<tr>${columns.map(([key])=>`<td>${key==='name'&&row.has_image?`<img class="s2-product-thumb" src="api/products/${row.id}/thumbnail" alt="">`:''}${escapeHtml(display(row,key))}</td>`).join('')}<td><div class="s2-row-actions">${endpoint==='price-lists'?`<button class="row-action" data-details="${row.id}">Chi tiết</button>`:''}${writable?`${endpoint==='price-lists'?`<button class="row-action" data-version="${row.id}">Phiên bản mới</button>`:''}${!row.locked?`<button class="row-action" data-edit="${row.id}">Sửa</button><button class="row-action" data-delete="${row.id}">Xóa</button>`:''}`:''}</div></td></tr>`).join('')}</tbody></table>`:`<div class="s2-empty"><strong>${rows.length?'Không có kết quả phù hợp':'Chưa có dữ liệu'}</strong><p>${writable?'Thêm bản ghi đầu tiên hoặc điều chỉnh bộ lọc.':'Dữ liệu sẽ xuất hiện sau khi được khai báo.'}</p></div>`;
      document.getElementById('s2Pages').innerHTML=`<button id="s2Prev" ${s2.page===1?'disabled':''}>‹</button><button disabled>${s2.page}</button><button id="s2Next" ${s2.page===pageCount?'disabled':''}>›</button>`;
      document.getElementById('s2Prev').onclick=()=>{s2.page--;draw();};document.getElementById('s2Next').onclick=()=>{s2.page++;draw();};
      host.querySelectorAll('[data-edit]').forEach(button=>button.onclick=()=>s2Edit(endpoint,rows.find(row=>row.id===Number(button.dataset.edit))));
      host.querySelectorAll('[data-version]').forEach(button=>button.onclick=()=>s2Edit(endpoint,rows.find(row=>row.id===Number(button.dataset.version)),true));
      host.querySelectorAll('[data-details]').forEach(button=>button.onclick=()=>s2PriceDetails(rows.find(row=>row.id===Number(button.dataset.details))));
      host.querySelectorAll('[data-delete]').forEach(button=>button.onclick=async()=>{if(!confirm('Xóa bản ghi này? Dữ liệu đã sử dụng sẽ được giữ lại.'))return;button.disabled=true;try{await s2Api(`${endpoint}/${button.dataset.delete}`,{method:'DELETE'});showToast('Đã xóa dữ liệu');s2Catalog(endpoint);}catch(e){s2Error(e);button.disabled=false;}});
    };
    document.getElementById('s2Search').oninput=()=>{s2.page=1;draw();};document.getElementById('s2Status').onchange=()=>{s2.page=1;draw();};draw();
  }catch(e){if(host.isConnected&&revision===viewRevision)host.innerHTML=`<p class="s2-empty" role="alert">${escapeHtml(e.message)}</p>`;}
}

function s2Dialog(title,body,submitLabel='Lưu dữ liệu') {
  document.getElementById('s2Dialog')?.remove();
  const modal=document.createElement('div');modal.id='s2Dialog';modal.className='form-modal open';modal.innerHTML=`<div class="form-dialog" role="dialog" aria-modal="true" aria-labelledby="s2DialogTitle"><header><div><span>THÔNG TIN</span><h2 id="s2DialogTitle">${escapeHtml(title)}</h2><p>Trường có dấu * là bắt buộc.</p></div><button type="button" id="s2Close" aria-label="Đóng">×</button></header><form id="s2Form"><div class="form-fields">${body}</div><p class="s2-form-error" id="s2FormError" role="alert"></p><footer><button type="button" class="outline-button" id="s2Cancel">Đóng</button>${submitLabel?`<button type="submit" class="orange-button">${submitLabel}</button>`:''}</footer></form></div>`;
  document.body.append(modal);document.body.style.overflow='hidden';
  const close=()=>{modal.remove();document.body.style.overflow='';};document.getElementById('s2Close').onclick=close;document.getElementById('s2Cancel').onclick=close;
  modal.addEventListener('keydown',event=>{if(event.key==='Escape')close();if(event.key==='Tab'){const fields=[...modal.querySelectorAll('button,input,select,textarea')].filter(x=>!x.disabled);if(event.shiftKey&&document.activeElement===fields[0]){event.preventDefault();fields.at(-1).focus();}else if(!event.shiftKey&&document.activeElement===fields.at(-1)){event.preventDefault();fields[0].focus();}}});
  modal.querySelector('input,select,button')?.focus();return {modal,form:document.getElementById('s2Form'),close};
}
function s2Field(name,label,value='',type='text',required=true,options=null,readonly=false) {
  let control;
  if(options)control=`<select name="${name}" ${required?'required':''} ${readonly?'disabled':''}><option value="">Chọn...</option>${options.map(([id,text])=>`<option value="${escapeHtml(id)}" ${String(id)===String(value)?'selected':''}>${escapeHtml(text)}</option>`).join('')}</select>`;
  else if(type==='textarea')control=`<textarea name="${name}" ${required?'required':''}>${escapeHtml(value)}</textarea>`;
  else control=`<input name="${name}" type="${type}" value="${type==='file'?'':escapeHtml(value)}" ${required?'required':''} ${readonly?'readonly':''} ${type==='number'?'min="0" step="any"':''} ${type==='file'?'accept="image/png,image/jpeg"':''}>`;
  return `<div class="form-group ${type==='textarea'?'full':''}"><label>${label}${required?' *':''}${control}</label></div>`;
}
function s2CategoryLabel(category,categories) {const names=[category.name],seen=new Set([category.id]);let parent=categories.find(c=>c.id===category.parent_id);while(parent&&!seen.has(parent.id)){names.unshift(parent.name);seen.add(parent.id);parent=categories.find(c=>c.id===parent.parent_id);}return names.join(' › ');}

async function s2Edit(endpoint,record={},version=false) {
  const revision=viewRevision;try{
    let fields='',products=[],groups=[];
    const activeField=()=>s2Field('active','Trạng thái',record.active??true,'text',true,[[true,'Đang hoạt động'],[false,'Ngừng hoạt động']]);
    if(endpoint==='products'){
      const categories=await s2Api('categories/');
      fields=s2Field('sku','Mã SKU',record.sku)+s2Field('name','Tên sản phẩm',record.name)+s2Field('category_id','Nhóm hàng',record.category_id,'text',true,categories.map(c=>[c.id,`${c.code} · ${s2CategoryLabel(c,categories)}`]))+s2Field('base_unit','Đơn vị cơ sở',record.base_unit,'text',true,null,!!record.id)+s2Field('packaging','Quy cách đóng gói',record.packaging,'text',false)+(s2Can('products.cost')?s2Field('cost_price','Giá vốn (VND)',record.cost_price??0,'number'):'')+activeField()+s2Field('image','Ảnh JPG/PNG, tối đa 2 MB','','file',false);
    }else if(endpoint==='categories'){
      const categories=await s2Api('categories/');fields=s2Field('code','Mã nhóm',record.code)+s2Field('name','Tên nhóm',record.name)+s2Field('parent_id','Nhóm cha (bỏ trống để tạo nhóm gốc)',record.parent_id,'text',false,categories.filter(c=>c.id!==record.id).map(c=>[c.id,s2CategoryLabel(c,categories)]));
    }else if(endpoint==='product-units'){
      products=await s2Api('products/');fields=s2Field('product_id','Sản phẩm',record.product_id,'text',true,products.map(p=>[p.id,`${p.sku} · ${p.name}`]),!!record.id)+s2Field('name','Tên đơn vị',record.name)+s2Field('factor','Số đơn vị cơ sở tương ứng',record.factor??1,'number')+'<p class="s2-hint">Ví dụ: 1 thùng = 24 lon thì hệ số là 24. Lịch sử giao dịch giữ nguyên khi bạn đổi hệ số.</p>';
    }else if(endpoint==='suppliers'){
      fields=s2Field('code','Mã nhà cung cấp',record.code)+s2Field('name','Tên nhà cung cấp',record.name)+s2Field('tax_code','Mã số thuế',record.tax_code)+s2Field('contact_name','Người liên hệ',record.contact_name)+s2Field('phone','Số điện thoại',record.phone,'tel',false)+activeField()+s2Field('payment_terms','Điều khoản thanh toán',record.payment_terms,'textarea');
    }else if(endpoint==='customer-groups')fields=s2Field('code','Mã nhóm',record.code)+s2Field('name','Tên nhóm khách hàng',record.name);
    else if(endpoint==='price-lists'){
      [products,groups]=await Promise.all([s2Api('products/'),s2Api('customer-groups/')]);
      fields=s2Field('name','Tên bảng giá',record.name)+s2Field('customer_group_id','Nhóm khách hàng',record.customer_group_id,'text',true,groups.map(g=>[g.id,g.name]))+s2Field('valid_from','Ngày bắt đầu',version?'':record.valid_from,'date')+s2Field('valid_to','Ngày kết thúc',version?'':record.valid_to,'date')+activeField()+'<div class="form-group full"><label>Giá theo đơn vị cơ sở</label><div id="s2PriceLines"></div><button type="button" class="outline-button" id="s2AddLine">+ Thêm dòng giá</button></div>';
    }
    if(revision!==viewRevision)return;
    const dialog=s2Dialog(`${version?'Tạo phiên bản mới':record.id?'Chỉnh sửa':'Thêm'} · ${s2.labels[endpoint]}`,fields);
    const addLine=(line={})=>{const div=document.createElement('div');div.className='s2-price-line';div.innerHTML=`<label>Sản phẩm<select name="line_product" required><option value="">Chọn sản phẩm</option>${products.map(p=>`<option value="${p.id}" ${p.id===line.product_id?'selected':''}>${escapeHtml(p.sku+' · '+p.name+' / '+p.base_unit)}</option>`).join('')}</select></label><label>Giá bán<input name="line_price" type="number" min="0" step="0.01" required value="${escapeHtml(line.price??'')}"></label><label>Giá sàn<input name="line_floor" type="number" min="0" step="0.01" required value="${escapeHtml(line.floor_price??'')}"></label><button type="button" aria-label="Xóa dòng giá">×</button>`;div.querySelector('button').onclick=()=>div.remove();document.getElementById('s2PriceLines').append(div);};
    if(endpoint==='price-lists'){(record.lines?.length?record.lines:[{}]).forEach(addLine);document.getElementById('s2AddLine').onclick=()=>addLine();}
    dialog.form.onsubmit=async event=>{
      event.preventDefault();const button=dialog.form.querySelector('[type="submit"]');button.disabled=true;const error=document.getElementById('s2FormError');error.textContent='';
      try{
        const data=Object.fromEntries(new FormData(dialog.form).entries());const file=data.image;delete data.image;
        if('active' in data)data.active=data.active==='true';for(const key of ['category_id','parent_id','product_id','customer_group_id'])if(key in data)data[key]=data[key]?Number(data[key]):null;
        if(endpoint==='product-units'&&record.id)data.product_id=record.product_id;
        if(endpoint==='price-lists'){data.lines=[...dialog.form.querySelectorAll('.s2-price-line')].map(line=>({product_id:Number(line.querySelector('[name="line_product"]').value),price:line.querySelector('[name="line_price"]').value,floor_price:line.querySelector('[name="line_floor"]').value}));delete data.line_product;delete data.line_price;delete data.line_floor;if(version)data.previous_id=record.id;}
        if(file?.size>2*1024*1024)throw new Error('Ảnh phải nhỏ hơn hoặc bằng 2 MB');
        const id=version?null:record.id;const result=await s2Api(`${endpoint}/${id||''}`,s2Json(id?'PUT':'POST',data));
        if(file?.size){try{const upload=new FormData();upload.append('file',file);await s2Api(`products/${result.id}/image`,{method:'POST',body:upload});}catch(imageError){dialog.close();s2Catalog(endpoint);showToast(`Đã lưu sản phẩm; ảnh chưa lưu: ${imageError.message}`);return;}}
        dialog.close();showToast(result.message);s2Catalog(endpoint);
      }catch(e){error.textContent=e.message;}finally{button.disabled=false;}
    };
  }catch(e){s2Error(e);}
}
function s2PriceDetails(row) {
  s2Dialog(row.name,`<div class="form-group full"><p>${escapeHtml(row.group_name)} · ${escapeHtml(row.valid_from)} → ${escapeHtml(row.valid_to)}</p><div class="table-scroll"><table class="feature-table"><thead><tr><th>SKU</th><th>Sản phẩm</th><th>Đơn vị</th><th>Giá bán</th>${row.lines.some(l=>'floor_price' in l)?'<th>Giá sàn</th>':''}</tr></thead><tbody>${row.lines.map(line=>`<tr><td>${escapeHtml(line.sku)}</td><td>${escapeHtml(line.product_name)}</td><td>${escapeHtml(line.base_unit)}</td><td>${s2Money(line.price)}</td>${'floor_price' in line?`<td>${s2Money(line.floor_price)}</td>`:''}</tr>`).join('')}</tbody></table></div></div>`,null);
}

async function s2Profile() {
  const revision=viewRevision;moduleContent.innerHTML='<p class="s2-empty">Đang tải hồ sơ...</p>';
  try{const user=await s2Api('profile/');if(revision!==viewRevision)return;
    moduleContent.innerHTML=`<section class="module-head"><div><h2>Hồ sơ cá nhân</h2><p>Cập nhật thông tin liên lạc và ảnh đại diện của bạn.</p></div></section><section class="inventory-panel s2-profile"><div class="s2-profile-head">${user.has_avatar?'<img src="api/profile/avatar" class="s2-avatar" alt="Ảnh đại diện">':'<div class="s2-avatar-placeholder">'+escapeHtml(user.full_name.slice(0,1))+'</div>'}<div><h3>${escapeHtml(user.full_name)}</h3><p>${escapeHtml(user.email)}</p><p>${escapeHtml(user.roles.map(role=>roleConfigs[role]?.label||role).join(', '))}</p></div></div><form id="s2ProfileForm"><div class="form-fields">${s2Field('full_name','Họ và tên',user.full_name)+s2Field('phone','Số điện thoại',user.phone,'tel')}${s2Field('username','Tài khoản',user.username,'text',false,null,true)}${s2Field('scope','Kho / địa bàn',user.warehouses.join(', ')||user.territory||'Chưa gán','text',false,null,true)}</div><p class="s2-form-error" id="s2ProfileError" role="alert"></p><button class="orange-button" type="submit">Lưu hồ sơ</button></form><hr><form id="s2AvatarForm"><label>Ảnh JPG/PNG, tối đa 2 MB <input type="file" name="file" accept="image/png,image/jpeg" required></label><p>Ảnh tự cắt vuông ở giữa và tạo bản thu nhỏ.</p><button class="outline-button" type="submit">Cập nhật ảnh</button></form></section>`;
    document.getElementById('s2ProfileForm').onsubmit=async event=>{event.preventDefault();const button=event.currentTarget.querySelector('button');button.disabled=true;const form=new FormData(event.currentTarget);try{await s2Api('profile/',s2Json('PUT',{full_name:form.get('full_name'),phone:form.get('phone')}));currentUser.fullName=form.get('full_name');document.querySelector('.user-chip strong').textContent=currentUser.fullName;showToast('Đã cập nhật hồ sơ');s2Profile();}catch(e){document.getElementById('s2ProfileError').textContent=e.message;}finally{button.disabled=false;}};
    document.getElementById('s2AvatarForm').onsubmit=async event=>{event.preventDefault();const form=new FormData(event.currentTarget),button=event.currentTarget.querySelector('button');button.disabled=true;try{if(form.get('file').size>2097152)throw new Error('Ảnh vượt quá 2 MB');await s2Api('profile/avatar',{method:'POST',body:form});showToast('Đã cập nhật ảnh đại diện');s2Profile();}catch(e){s2Error(e);}finally{button.disabled=false;}};
  }catch(e){if(revision===viewRevision)moduleContent.innerHTML=`<p class="s2-empty">${escapeHtml(e.message)}</p>`;}
}

async function s2Audit() {
  const revision=viewRevision;let page=1,request=0;
  moduleContent.innerHTML=`<section class="module-head"><div><h2>Nhật ký hệ thống</h2><p>Theo dõi người thực hiện, thời điểm và dữ liệu trước/sau thay đổi.</p></div></section><section class="inventory-panel"><form id="s2AuditFilters" class="inventory-toolbar"><select name="actor" aria-label="Người thực hiện" class="filter-select"><option value="">Tất cả người dùng</option></select><select name="type" aria-label="Loại đối tượng" class="filter-select"><option value="">Tất cả đối tượng</option></select><label>Từ ngày <input name="from" type="date"></label><label>Đến ngày <input name="to" type="date"></label><button class="outline-button" type="submit">Lọc</button></form><div id="s2AuditRows" class="table-scroll"></div><div id="s2AuditPages" class="inventory-footer"></div></section>`;
  const form=document.getElementById('s2AuditFilters'),host=document.getElementById('s2AuditRows');
  const load=async()=>{const call=++request;host.innerHTML='<p class="s2-empty">Đang tải nhật ký...</p>';try{const params=new URLSearchParams(new FormData(form));params.set('page',page);const data=await s2Api(`audit/?${params}`);if(revision!==viewRevision||call!==request||!host.isConnected)return;host.innerHTML=data.items.length?`<table class="feature-table s2-table"><thead><tr><th>Thời điểm</th><th>Người thực hiện</th><th>Thao tác</th><th>Đối tượng</th><th>Trước / Sau</th></tr></thead><tbody>${data.items.map(row=>`<tr><td>${escapeHtml(row.created_at)}</td><td>${escapeHtml(row.actor_name||'Hệ thống')}</td><td>${escapeHtml(row.action)}</td><td>${escapeHtml(row.object_type)} #${escapeHtml(row.object_id)}</td><td><details><summary>Xem thay đổi</summary><strong>Trước</strong><pre>${escapeHtml(JSON.stringify(row.old_value,null,2))}</pre><strong>Sau</strong><pre>${escapeHtml(JSON.stringify(row.new_value,null,2))}</pre></details></td></tr>`).join('')}</tbody></table>`:'<p class="s2-empty">Chưa có nhật ký phù hợp.</p>';document.getElementById('s2AuditPages').innerHTML=`<span>${number(data.total)} bản ghi · Trang ${page}</span><div class="pagination"><button id="s2AuditPrev" ${page===1?'disabled':''}>‹</button><button id="s2AuditNext" ${page*50>=data.total?'disabled':''}>›</button></div>`;document.getElementById('s2AuditPrev').onclick=()=>{page--;load();};document.getElementById('s2AuditNext').onclick=()=>{page++;load();};}catch(e){if(host.isConnected)host.innerHTML=`<p class="s2-empty">${escapeHtml(e.message)}</p>`;}};
  form.onsubmit=event=>{event.preventDefault();page=1;load();};
  // Filter options are loaded separately to keep failures visible without blocking the log.
  s2Api('audit/filters').then(filters=>{if(!form.isConnected)return;form.elements.actor.innerHTML+=filters.users.map(u=>`<option value="${u.id}">${escapeHtml(u.full_name)}</option>`).join('');form.elements.type.innerHTML+=filters.types.map(t=>`<option>${escapeHtml(t.object_type)}</option>`).join('');}).catch(s2Error);
  load();
}

function s2Import(kind) {
  s2.request++;const revision=viewRevision;let preview=null,resultPage=1;
  const noun=kind==='users'?'người dùng':'sản phẩm';
  moduleContent.innerHTML=`<section class="module-head"><div><h2>Nhập ${noun} từ Excel</h2><p>1. Tải mẫu và điền dữ liệu → 2. Xem trước → 3. Xác nhận nhập.</p></div><a class="outline-button" href="api/templates/${kind}">Tải tệp mẫu .xlsx</a></section><section class="inventory-panel s2-import"><p>${kind==='products'?'Nhập mã nhóm hàng đã được tạo. SKU đã có sẽ được cập nhật. active dùng true/false; giá vốn dùng số, không có dấu phân cách.':'Vai trò dùng mã ADMIN, SALES_MANAGER, SALES_REP, WH_MANAGER, WAREHOUSE, ACCOUNTANT hoặc CUSTOMER. Người dùng kho cần warehouse_code hợp lệ.'}</p><form id="s2ImportForm"><label>Tệp Excel (.xlsx, tối đa 8 MB, 5.000 dòng)<input name="file" type="file" accept=".xlsx" required></label><button type="submit" class="orange-button">Xem trước dữ liệu</button></form><p id="s2ImportMessage" role="status"></p><div id="s2ImportSummary"></div><div id="s2ImportRows" class="table-scroll"></div><div id="s2ImportPages" class="pagination"></div><div class="module-actions"><button id="s2CommitImport" class="orange-button" hidden>Xác nhận nhập các dòng hợp lệ</button><button id="s2DownloadReport" class="outline-button" hidden>Tải báo cáo CSV</button></div></section>`;
  const form=document.getElementById('s2ImportForm'),message=document.getElementById('s2ImportMessage'),commit=document.getElementById('s2CommitImport');
  const draw=()=>{document.getElementById('s2ImportSummary').innerHTML=`<div class="s2-import-stats"><strong>${preview.total} dòng</strong><span>${preview.valid} ${preview.committed?'đã nhập':'hợp lệ'}</span><span>${preview.invalid} ${preview.committed?'bỏ qua':'có lỗi'}</span></div>`;document.getElementById('s2ImportRows').innerHTML=`<table class="feature-table s2-table"><thead><tr><th>Dòng Excel</th><th>Dữ liệu</th><th>Thao tác</th><th>Kết quả</th></tr></thead><tbody>${preview.rows.slice((resultPage-1)*50,resultPage*50).map(row=>`<tr><td>${row.row}</td><td>${escapeHtml(Object.values(row.data).join(' · '))}</td><td>${escapeHtml(row.action)}</td><td class="${row.valid?'s2-valid':'s2-invalid'}">${escapeHtml(row.message)}</td></tr>`).join('')}</tbody></table>`;document.getElementById('s2ImportPages').innerHTML=`<button id="s2ImportPrev" ${resultPage===1?'disabled':''}>‹</button><span>Trang ${resultPage}</span><button id="s2ImportNext" ${resultPage*50>=preview.total?'disabled':''}>›</button>`;document.getElementById('s2ImportPrev').onclick=()=>{resultPage--;draw();};document.getElementById('s2ImportNext').onclick=()=>{resultPage++;draw();};commit.hidden=preview.committed||preview.valid===0;document.getElementById('s2DownloadReport').hidden=false;};
  form.elements.file.onchange=()=>{preview=null;commit.hidden=true;document.getElementById('s2ImportRows').innerHTML='';document.getElementById('s2ImportSummary').innerHTML='';document.getElementById('s2ImportPages').innerHTML='';document.getElementById('s2DownloadReport').hidden=true;};
  form.onsubmit=async event=>{event.preventDefault();const button=form.querySelector('button');button.disabled=true;commit.hidden=true;message.textContent='Đang kiểm tra từng dòng...';try{const data=new FormData(form);if(data.get('file').size>8388608)throw new Error('Tệp vượt quá 8 MB');const result=await s2Api(`imports/${kind}/preview`,{method:'POST',body:data});if(revision!==viewRevision||!form.isConnected)return;preview=result;resultPage=1;message.textContent='Chưa có dữ liệu nào được lưu. Kiểm tra kết quả trước khi xác nhận.';draw();}catch(e){message.textContent=e.message;}finally{button.disabled=false;}};
  commit.onclick=async()=>{commit.disabled=true;form.querySelector('button').disabled=true;message.textContent='Đang nhập dữ liệu...';try{const result=await s2Api(`imports/${kind}/commit`,s2Json('POST',{token:preview.token}));if(revision!==viewRevision||!form.isConnected)return;preview=result;resultPage=1;message.textContent='Đã hoàn tất. Các dòng lỗi được bỏ qua.';draw();}catch(e){message.textContent=e.message+' Hãy xem trước lại để tiếp tục.';commit.hidden=true;}finally{commit.disabled=false;form.querySelector('button').disabled=false;}};
  document.getElementById('s2DownloadReport').onclick=()=>{if(!preview)return;const cell=value=>'"'+String(value).replace(/^[=+@-]/,"'$&").replace(/"/g,'""')+'"';const csv='\ufeff'+[['Dòng','Thao tác','Kết quả'],...preview.rows.map(row=>[row.row,row.action,row.message])].map(row=>row.map(cell).join(',')).join('\r\n');const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=`ket-qua-nhap-${kind}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
}
