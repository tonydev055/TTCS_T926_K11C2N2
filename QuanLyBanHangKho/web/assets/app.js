const loginView = document.getElementById('loginView');
const registerView = document.getElementById('registerView');
const forgotPasswordView = document.getElementById('forgotPasswordView');
const resetPasswordView = document.getElementById('resetPasswordView');
const appView = document.getElementById('appView');
const loginForm = document.getElementById('loginForm');
const loginError = document.getElementById('loginError');
const emailInput = document.getElementById('email');
const passwordInput = document.getElementById('password');
const dashboardContent = document.getElementById('dashboardContent');
const moduleContent = document.getElementById('moduleContent');
let toastTimer;

const roleConfigs = {
  CUSTOMER: { label:'Đại lý', menu:['Tổng quan','Sản phẩm & bảng giá','Đơn hàng','Giao hàng','Hóa đơn & công nợ','Trả hàng'] },
  SALES_REP: { label:'Nhân viên kinh doanh', menu:['Tổng quan','Sản phẩm & bảng giá','Đại lý phụ trách','Đơn hàng','Thu tiền theo tuyến','Chỉ tiêu cá nhân'] },
  SALES_MANAGER: { label:'Quản lý kinh doanh', menu:['Tổng quan','Sản phẩm & bảng giá','Đại lý & hạn mức','Đơn hàng','Duyệt ngoại lệ','Doanh số & chỉ tiêu','Báo cáo kinh doanh'] },
  WAREHOUSE: { label:'Nhân viên kho', menu:['Tổng quan','Tồn kho','Nhập kho','Xuất kho','Lô & hạn dùng','Kiểm kê'] },
  WH_MANAGER: { label:'Quản lý kho', menu:['Tổng quan','Tồn kho','Nhập kho','Xuất kho','Chuyển kho','Lô & hạn dùng','Kiểm kê','Nhà cung cấp','Báo cáo kho'] },
  ACCOUNTANT: { label:'Kế toán công nợ', menu:['Tổng quan','Đại lý & hạn mức','Đơn hàng','Hóa đơn','Thanh toán','Sổ công nợ','Đối chiếu công nợ','Báo cáo công nợ'] },
  ADMIN: { label:'Quản trị hệ thống', menu:['Tổng quan','Người dùng','Vai trò & quyền','Danh mục dùng chung','Nhật ký hệ thống','Cấu hình'] }
};
let activeRole = 'WH_MANAGER';

const formSchemas = {
  users:[['username','Tên đăng nhập','text',true],['fullName','Họ và tên','text',true],['email','Email','email',true],['phone','Số điện thoại','tel',true],['role','Vai trò','select',true,Object.values(roleConfigs).map(item => item.label)],['scope','Kho / địa bàn','text',true],['status','Trạng thái','select',true,['Hoạt động','Tạm khóa']]],
};
let currentUser = {};
let viewRevision = 0;

function openApp(user = {}) {
  currentUser = user;
  loginView.classList.add('is-hidden');
  registerView.classList.add('is-hidden');
  appView.classList.remove('is-hidden');
  activeRole = roleConfigs[user.role] ? user.role : (user.role === 'WH_MANAGER' ? user.role : activeRole);
  const config = roleConfigs[activeRole];
  const displayName = user.fullName || user.email || 'Người dùng';
  document.querySelector('.user-chip strong').textContent = displayName;
  document.querySelector('.user-chip small').textContent = config.label;
  document.querySelector('.avatar').textContent = user.fullName ? displayName.split(' ').slice(-2).map(word => word[0]).join('').toUpperCase() : 'ND';
  buildNavigation(typeof sprint2Menu === 'function' ? sprint2Menu() : config.menu);
  sessionStorage.setItem('khoFlowSession', JSON.stringify({ fullName: displayName, role: activeRole }));
  openView('Tổng quan');
}

function showToast(message) {
  const toast = document.getElementById('toast');
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 2600);
}

const formModal = document.getElementById('formModal');
let activeFormContext = null;

function openEntityForm(view, mode = 'create', recordLabel = '', metadata = {}) {
  if (view !== 'Người dùng') return;
  const schema = mode === 'edit' ? formSchemas.users.filter(field => field[0] === 'status') : formSchemas.users;
  activeFormContext = { view, mode, recordLabel, ...metadata };
  document.getElementById('formModalEyebrow').textContent = mode === 'edit' ? 'CHỈNH SỬA THÔNG TIN' : 'TẠO DỮ LIỆU MỚI';
  document.getElementById('formModalTitle').textContent = mode === 'edit' ? `Sửa ${recordLabel || view}` : `${view} — Tạo mới`;
  document.getElementById('formModalDescription').textContent = `Biểu mẫu dành cho vai trò ${roleConfigs[activeRole].label}. Các trường có dấu * là bắt buộc.`;
  document.getElementById('formFields').innerHTML = `<div class="form-section-title">THÔNG TIN CHUNG</div>${schema.map((field,index) => renderFormField(field, mode, recordLabel, index)).join('')}`;
  if (view === 'Người dùng' && mode === 'edit' && document.getElementById('field-status')) document.getElementById('field-status').value = metadata.locked ? 'Tạm khóa' : 'Hoạt động';
  formModal.classList.add('open');
  formModal.setAttribute('aria-hidden','false');
  document.body.style.overflow = 'hidden';
  setTimeout(() => document.querySelector('#formFields input, #formFields select, #formFields textarea')?.focus(), 80);
}

function renderFormField(field, mode, recordLabel, index) {
  const [name,label,type,required,options,full] = field;
  const sample = '';
  let control;
  if (type === 'select') control = `<select id="field-${name}" name="${name}" ${required ? 'required' : ''}><option value="">Chọn ${label.toLowerCase()}</option>${options.map(option => `<option ${sample === option ? 'selected' : ''}>${option}</option>`).join('')}</select>`;
  else if (type === 'textarea') control = `<textarea id="field-${name}" name="${name}" ${required ? 'required' : ''} placeholder="Nhập ${label.toLowerCase()}">${sample}</textarea>`;
  else control = `<input id="field-${name}" name="${name}" type="${type}" ${required ? 'required' : ''} value="${sample}" placeholder="Nhập ${label.toLowerCase()}">`;
  return `<div class="form-group ${full ? 'full' : ''}"><label for="field-${name}">${label}${required ? ' <b>*</b>' : ''}</label>${control}<small class="field-error">Vui lòng nhập ${label.toLowerCase()}.</small></div>`;
}

function closeEntityForm() {
  formModal.classList.remove('open');
  formModal.setAttribute('aria-hidden','true');
  document.body.style.overflow = '';
  activeFormContext = null;
}

document.getElementById('closeFormModal').addEventListener('click', closeEntityForm);
document.getElementById('cancelEntityForm').addEventListener('click', closeEntityForm);
formModal.addEventListener('click', event => { if (event.target === formModal) closeEntityForm(); });
document.addEventListener('keydown', event => { if (event.key === 'Escape' && formModal.classList.contains('open')) closeEntityForm(); });
document.getElementById('entityForm').addEventListener('submit', async event => {
  event.preventDefault();
  let valid = true;
  document.querySelectorAll('#formFields [required]').forEach(control => {
    const group = control.closest('.form-group');
    const invalid = !String(control.value).trim();
    group.classList.toggle('invalid', invalid);
    if (invalid) valid = false;
  });
  if (!valid) { showToast('Vui lòng điền đủ các trường bắt buộc'); return; }
  if (activeFormContext?.view === 'Người dùng' && activeFormContext.mode === 'create') {
    const data = Object.fromEntries(new FormData(event.currentTarget).entries());
    const roleByLabel = Object.fromEntries(Object.entries(roleConfigs).map(([code,item]) => [item.label,code]));
    const role = roleByLabel[data.role] || 'CUSTOMER';
    const payload = { username:data.username, fullName:data.fullName, email:data.email, phone:data.phone, territory:data.scope, roles:[role], warehouses:['WAREHOUSE','WH_MANAGER'].includes(role) ? [data.scope] : [] };
    try {
      const response = await fetch('api/users/',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
      const result = await response.json().catch(()=>({})); if(!response.ok) throw new Error(result.message || 'Không thể tạo tài khoản.');
      closeEntityForm(); showToast(result.message); openView('Người dùng');
    } catch (e) { showToast(e.message); }
    return;
  }
  if (activeFormContext?.view === 'Người dùng' && activeFormContext.mode === 'edit' && activeFormContext.userId) {
    const data = Object.fromEntries(new FormData(event.currentTarget).entries());
    const shouldLock = data.status === 'Tạm khóa';
    const action = shouldLock ? 'lock' : 'unlock';
    const reason = shouldLock ? window.prompt('Nhập lý do khóa tài khoản:') : '';
    if (shouldLock && (!reason || !reason.trim())) return;
    try {
      const response = await fetch(`api/users/${activeFormContext.userId}/${action}`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason:reason||''})});
      const result = await response.json().catch(()=>({})); if(!response.ok) throw new Error(result.message || 'Không thể cập nhật trạng thái tài khoản.');
      closeEntityForm(); showToast(result.message); openView('Người dùng');
    } catch (e) { showToast(e.message); }
    return;
  }
  showToast('Chức năng lưu dữ liệu này chưa được kết nối.');
});

loginForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  loginError.textContent = '';
  const email = emailInput.value.trim();
  const password = passwordInput.value;
  if (!email || !password) {
    loginError.textContent = 'Vui lòng nhập đầy đủ email và mật khẩu.';
    return;
  }
  const button = loginForm.querySelector('.primary-button');
  button.disabled = true;
  button.firstChild.textContent = 'Đang đăng nhập ';
  try {
    const response = await fetch('api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.message || 'Không thể đăng nhập.');
    openApp(payload);
  } catch (error) {
    loginError.textContent = error.message === 'Failed to fetch'
      ? 'Không kết nối được máy chủ. Vui lòng thử lại.'
      : error.message;
  } finally {
    button.disabled = false;
    button.firstChild.textContent = 'Đăng nhập ';
  }
});

document.getElementById('togglePassword').addEventListener('click', () => {
  passwordInput.type = passwordInput.type === 'password' ? 'text' : 'password';
});

document.getElementById('logoutButton').addEventListener('click', async () => {
  try { await fetch('api/auth/logout', { method:'POST' }); } catch (_) {}
  sessionStorage.removeItem('khoFlowSession');
  appView.classList.add('is-hidden');
  loginView.classList.remove('is-hidden');
  passwordInput.value = '';
});

document.getElementById('openRegister').addEventListener('click', () => {
  loginView.classList.add('is-hidden');
  registerView.classList.remove('is-hidden');
  document.getElementById('registerFullName').focus();
});
document.getElementById('backToLogin').addEventListener('click', () => {
  registerView.classList.add('is-hidden');
  loginView.classList.remove('is-hidden');
});
document.getElementById('registerForm').addEventListener('submit', async event => {
  event.preventDefault();
  const error = document.getElementById('registerError');
  const password = document.getElementById('registerPassword').value;
  const confirm = document.getElementById('registerConfirm').value;
  const payload = {
    fullName: document.getElementById('registerFullName').value.trim(),
    username: document.getElementById('registerUsername').value.trim(),
    email: document.getElementById('registerEmail').value.trim(),
    phone: document.getElementById('registerPhone').value.trim(), password
  };
  if (!payload.fullName || !payload.username || !payload.email || !password) { error.textContent = 'Vui lòng điền đủ các trường bắt buộc.'; return; }
  if (password !== confirm) { error.textContent = 'Mật khẩu xác nhận không khớp.'; return; }
  if (!document.getElementById('registerTerms').checked) { error.textContent = 'Bạn cần đồng ý với điều khoản sử dụng.'; return; }
  try {
    const response = await fetch('api/auth/register', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload) });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.message || 'Không thể tạo tài khoản.');
    emailInput.value = payload.email; registerView.classList.add('is-hidden'); loginView.classList.remove('is-hidden'); showToast(result.message);
  } catch (e) {
    error.textContent = e.message === 'Failed to fetch'
      ? 'Không kết nối được máy chủ. Vui lòng thử lại.'
      : e.message;
  }
});
const forgotEmailInput = document.getElementById('forgotEmail');
const forgotPasswordError = document.getElementById('forgotPasswordError');
const forgotSubmitButton = document.getElementById('forgotSubmitButton');

function openForgotPassword() {
  loginView.classList.add('is-hidden');
  registerView.classList.add('is-hidden');
  forgotPasswordView.classList.remove('is-hidden');
  document.getElementById('forgotRequestPanel').classList.remove('is-hidden');
  document.getElementById('forgotSuccessPanel').classList.add('is-hidden');
  forgotPasswordError.textContent = '';
  forgotEmailInput.value = emailInput.value.trim();
  forgotEmailInput.focus();
}

function closeForgotPassword() {
  forgotPasswordView.classList.add('is-hidden');
  loginView.classList.remove('is-hidden');
  if (forgotEmailInput.value.trim()) emailInput.value = forgotEmailInput.value.trim();
  passwordInput.focus();
}

async function sendPasswordReset(email) {
  const response = await fetch('api/auth/forgot-password', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email})});
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.message || 'Không thể gửi yêu cầu đặt lại mật khẩu.');
  return result;
}

document.getElementById('forgotPasswordButton').addEventListener('click', openForgotPassword);
document.getElementById('backFromForgot').addEventListener('click', closeForgotPassword);
document.getElementById('backAfterForgot').addEventListener('click', closeForgotPassword);
document.getElementById('forgotPasswordForm').addEventListener('submit', async event => {
  event.preventDefault();
  const email = forgotEmailInput.value.trim();
  forgotPasswordError.textContent = '';
  if (!email || !forgotEmailInput.checkValidity()) { forgotPasswordError.textContent = 'Vui lòng nhập địa chỉ email hợp lệ.'; return; }
  forgotSubmitButton.disabled = true;
  forgotSubmitButton.firstChild.textContent = 'Đang gửi ';
  try {
    await sendPasswordReset(email);
    document.getElementById('forgotSentEmail').textContent = email;
    document.getElementById('forgotRequestPanel').classList.add('is-hidden');
    document.getElementById('forgotSuccessPanel').classList.remove('is-hidden');
  } catch (error) {
    forgotPasswordError.textContent = error.message === 'Failed to fetch' ? 'Không kết nối được máy chủ. Vui lòng thử lại.' : error.message;
  } finally {
    forgotSubmitButton.disabled = false;
    forgotSubmitButton.firstChild.textContent = 'Gửi liên kết đặt lại ';
  }
});
document.getElementById('resendForgotPassword').addEventListener('click', async event => {
  event.currentTarget.disabled = true;
  try { const result = await sendPasswordReset(forgotEmailInput.value.trim()); showToast(result.message); }
  catch (_) { showToast('Không thể gửi lại liên kết lúc này.'); }
  finally { event.currentTarget.disabled = false; }
});
document.getElementById('openDevMailbox').addEventListener('click', () => {
  window.location.href = `mailbox.html?email=${encodeURIComponent(forgotEmailInput.value.trim())}`;
});

function openView(view) {
  const menu = typeof sprint2Menu === 'function' ? sprint2Menu() : roleConfigs[activeRole].menu;
  if (!menu.includes(view)) return;
  viewRevision++;
  document.querySelectorAll('.nav-item').forEach(nav => nav.classList.toggle('active', nav.dataset.view === view));
  document.getElementById('breadcrumb').textContent = view;
  document.getElementById('pageTitle').textContent = view;
  dashboardContent.classList.add('is-hidden');
  moduleContent.classList.remove('is-hidden');
  if (typeof openSprint2View === 'function' && openSprint2View(view)) { closeSidebar(); return; }
  if (view === 'Tổng quan') renderRealHome();
  else if (view === 'Người dùng') renderUsersModule();
  else renderRealModule(view);
  closeSidebar();
}

function buildNavigation(menu) {
  const warehouseControl = new Set(['Lô & hạn dùng','Kiểm kê','Nhà cung cấp','Báo cáo kho']);
  const main = menu.filter(item => !warehouseControl.has(item));
  const control = menu.filter(item => warehouseControl.has(item));
  const iconMap = {'Tổng quan':'▦','Tồn kho':'▤','Nhập kho':'↙','Xuất kho':'↗','Chuyển kho':'⇄','Lô & hạn dùng':'◷','Kiểm kê':'✓','Nhà cung cấp':'◇','Báo cáo kho':'▥','Sản phẩm & bảng giá':'▤','Đơn hàng':'▣','Giao hàng':'↗','Hóa đơn & công nợ':'₫','Trả hàng':'↩','Đại lý phụ trách':'◇','Thu tiền theo tuyến':'₫','Chỉ tiêu cá nhân':'◎','Đại lý & hạn mức':'◇','Duyệt ngoại lệ':'✓','Doanh số & chỉ tiêu':'◎','Báo cáo kinh doanh':'▥','Hóa đơn':'▤','Thanh toán':'₫','Sổ công nợ':'▥','Đối chiếu công nợ':'✓','Báo cáo công nợ':'▥','Người dùng':'♙','Vai trò & quyền':'⌘','Danh mục dùng chung':'▤','Nhật ký hệ thống':'◷','Cấu hình':'⚙'};
  const items = values => values.map(view => `<button class="nav-item ${view === 'Tổng quan' ? 'active' : ''}" data-view="${view}"><span>${iconMap[view] || '◇'}</span>${view}</button>`).join('');
  document.querySelector('.nav-list').innerHTML = `<p class="nav-label">CHỨC NĂNG</p>${items(main)}${control.length ? `<p class="nav-label">KIỂM SOÁT</p>${items(control)}` : ''}`;
  document.querySelectorAll('.nav-item').forEach(item => item.addEventListener('click', () => openView(item.dataset.view)));
}

document.querySelectorAll('[data-toast]').forEach(button => button.addEventListener('click', () => showToast(button.dataset.toast)));
const sidebar = document.getElementById('sidebar');
const sidebarOverlay = document.getElementById('sidebarOverlay');
function closeSidebar(){ sidebar.classList.remove('open'); sidebarOverlay.classList.remove('show'); }
document.getElementById('openSidebar').addEventListener('click', () => { sidebar.classList.add('open'); sidebarOverlay.classList.add('show'); });
document.getElementById('closeSidebar').addEventListener('click', closeSidebar);
sidebarOverlay.addEventListener('click', closeSidebar);

function number(value) { return new Intl.NumberFormat('vi-VN').format(value); }
function escapeHtml(value) { const element=document.createElement('div');element.textContent=value??'';return element.innerHTML.replace(/"/g,'&quot;').replace(/'/g,'&#39;'); }

function renderUsersModule() {
  const roleLabels=Object.fromEntries(Object.entries(roleConfigs).map(([code,item])=>[code,item.label]));
  let page=1;const size=5;let debounce;let requestId=0;const revision=viewRevision;
  moduleContent.innerHTML=`<section class="module-head"><div><h2>Người dùng</h2><p>Quản lý tài khoản, trạng thái hoạt động và phạm vi làm việc từ PostgreSQL.</p></div><div class="module-actions"><button class="orange-button" id="createRealUser">+ Tạo người dùng</button></div></section><section class="operation-summary compact-summary"><article><span class="op-icon orange">▦</span><div><small>Tổng tài khoản</small><strong id="userStatTotal">—</strong><em>Dữ liệu toàn hệ thống</em></div></article><article><span class="op-icon blue">✓</span><div><small>Đang hoạt động</small><strong id="userStatActive">—</strong><em>Có thể đăng nhập</em></div></article><article><span class="op-icon purple">⌁</span><div><small>Cần đổi mật khẩu</small><strong id="userStatPassword">—</strong><em>Yêu cầu khi đăng nhập</em></div></article><article><span class="op-icon red">!</span><div><small>Đang bị khóa</small><strong id="userStatLocked">—</strong><em>Khóa tay hoặc tạm thời</em></div></article></section><section class="inventory-panel"><div class="inventory-toolbar"><label class="search-control"><span>⌕</span><input id="realUserSearch" type="search" placeholder="Tìm tên, tài khoản, email hoặc số điện thoại..."></label><select id="realUserStatus" class="filter-select"><option value="">Tất cả trạng thái</option><option value="active">Hoạt động</option><option value="locked">Đã khóa</option></select><span id="realUserResult" class="filter-result"></span></div><div class="table-scroll"><table class="feature-table role-module-table"><thead><tr><th>Tài khoản</th><th>Họ tên</th><th>Vai trò</th><th>Kho / địa bàn</th><th>Lần đăng nhập cuối</th><th>Trạng thái</th><th></th></tr></thead><tbody id="realUserRows"></tbody></table></div><div class="inventory-footer"><span id="realUserFooter">Đang tải dữ liệu...</span><div id="realUserPagination" class="pagination"></div></div></section>`;
  const formatDate=value=>value?new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short'}).format(new Date(value)):'Chưa đăng nhập';
  const load=async()=>{if(revision!==viewRevision)return;const request=++requestId;const search=document.getElementById('realUserSearch').value.trim(),status=document.getElementById('realUserStatus').value;document.getElementById('realUserRows').innerHTML='<tr><td colspan="7" style="text-align:center;padding:38px;color:#8c7d72">Đang tải dữ liệu...</td></tr>';try{const response=await fetch(`api/users/?page=${page}&size=${size}&search=${encodeURIComponent(search)}&status=${encodeURIComponent(status)}`);const payload=await response.json();if(revision!==viewRevision||request!==requestId)return;if(!response.ok)throw new Error(dataError(response,payload));document.getElementById('userStatTotal').textContent=number(payload.stats.total);document.getElementById('userStatActive').textContent=number(payload.stats.active);document.getElementById('userStatPassword').textContent=number(payload.stats.requires_password_change);document.getElementById('userStatLocked').textContent=number(payload.stats.locked);document.getElementById('realUserResult').textContent=`${payload.total} kết quả`;document.getElementById('realUserFooter').textContent=`Trang ${payload.page} · ${payload.total} tài khoản phù hợp`;document.getElementById('realUserRows').innerHTML=payload.items.map(item=>{const locked=item.is_locked===true,statusLabel=locked?'Đã khóa':'Hoạt động';const roles=(item.roles||'').split(',').filter(Boolean).map(code=>roleLabels[code]||code).join(', ')||'Chưa gán';const scope=item.warehouses||item.territory||'—';return `<tr><td><a href="#" data-user-id="${item.id}">${escapeHtml(item.username)}</a><small style="display:block;color:#988a7f;margin-top:4px">${escapeHtml(item.email)}</small></td><td><strong>${escapeHtml(item.full_name)}</strong></td><td>${escapeHtml(roles)}</td><td>${escapeHtml(scope)}</td><td>${escapeHtml(formatDate(item.last_login_at))}</td><td><span class="status-pill ${operationStatusClass(statusLabel)}">${statusLabel}</span>${item.failed_login_attempts?`<small style="display:block;color:#b15a36;margin-top:4px">${item.failed_login_attempts} lần sai</small>`:''}</td><td><div class="user-row-actions"><button class="row-action real-user-detail" data-user-id="${item.id}" data-user-name="${escapeHtml(item.username)}" data-locked="${locked}">Chi tiết</button><button class="row-action real-user-lock ${locked?'unlock':''}" data-user-id="${item.id}" data-locked="${locked}">${locked?'Mở khóa':'Khóa'}</button></div></td></tr>`}).join('')||'<tr><td colspan="7" style="text-align:center;padding:38px;color:#8c7d72">Không tìm thấy tài khoản phù hợp.</td></tr>';const pages=Math.max(1,Math.ceil(payload.total/size));if(page>pages){page=pages;return load()}document.getElementById('realUserPagination').innerHTML=`<button data-page="${Math.max(1,page-1)}" ${page===1?'disabled':''}>‹</button>${Array.from({length:pages},(_,i)=>i+1).map(value=>`<button data-page="${value}" class="${value===page?'active':''}">${value}</button>`).join('')}<button data-page="${Math.min(pages,page+1)}" ${page===pages?'disabled':''}>›</button>`;document.querySelectorAll('#realUserPagination button').forEach(button=>button.addEventListener('click',()=>{if(button.disabled)return;page=Number(button.dataset.page);load()}));document.querySelectorAll('.real-user-detail').forEach(button=>button.addEventListener('click',()=>openEntityForm('Người dùng','edit',button.dataset.userName,{userId:Number(button.dataset.userId),locked:button.dataset.locked==='true'})));document.querySelectorAll('.real-user-lock').forEach(button=>button.addEventListener('click',async()=>{const locked=button.dataset.locked==='true';const reason=locked?'':window.prompt('Nhập lý do khóa tài khoản:');if(!locked&&(!reason||!reason.trim()))return;button.disabled=true;try{const response=await fetch(`api/users/${button.dataset.userId}/${locked?'unlock':'lock'}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason:reason||''})});const result=await response.json().catch(()=>({}));if(!response.ok)throw new Error(result.message||'Không thể cập nhật trạng thái tài khoản');showToast(result.message);await load()}catch(error){showToast(error.message)}finally{button.disabled=false}}));}catch(error){if(revision!==viewRevision||request!==requestId)return;['userStatTotal','userStatActive','userStatPassword','userStatLocked'].forEach(id=>document.getElementById(id).textContent='—');document.getElementById('realUserFooter').textContent='Chưa tải được dữ liệu';document.getElementById('realUserPagination').innerHTML='';document.getElementById('realUserResult').textContent='';document.getElementById('realUserRows').innerHTML=`<tr><td colspan="7" style="text-align:center;padding:38px;color:#bd2b2b">${escapeHtml(error.message)}</td></tr>`}};
  document.getElementById('realUserSearch').addEventListener('input',()=>{clearTimeout(debounce);debounce=setTimeout(()=>{page=1;load()},300)});document.getElementById('realUserStatus').addEventListener('change',()=>{page=1;load()});document.getElementById('createRealUser').addEventListener('click',()=>openEntityForm('Người dùng'));load();
}

function operationStatusClass(status) {
  if (['Hoạt động','Đã hoàn tất','Đã xuất','Đã giao','Đã nhận','Đã chốt'].includes(status)) return 'status-ok';
  if (['Đã khóa','Có sai lệch','Soạn thiếu'].includes(status)) return 'status-out';
  return 'status-low';
}

if (sessionStorage.getItem('khoFlowSession') && !new URLSearchParams(window.location.search).get('resetToken')) {
  fetch('api/auth/me').then(async response => {
    if (!response.ok) throw new Error();
    openApp(await response.json());
  }).catch(() => sessionStorage.removeItem('khoFlowSession'));
}

const previewParams = new URLSearchParams(window.location.search);
if (previewParams.get('resetToken')) {
  loginView.classList.add('is-hidden');registerView.classList.add('is-hidden');forgotPasswordView.classList.add('is-hidden');appView.classList.add('is-hidden');resetPasswordView.classList.remove('is-hidden');
}
const resetPasswordInput=document.getElementById('resetPassword');
const resetPasswordConfirm=document.getElementById('resetPasswordConfirm');
function updatePasswordStrength(){const value=resetPasswordInput.value;const checks=[value.length>=8,/[A-Za-zÀ-ỹ]/.test(value),/\d/.test(value)];document.getElementById('ruleLength').classList.toggle('passed',checks[0]);document.getElementById('ruleLength').textContent=`${checks[0]?'✓':'○'} Ít nhất 8 ký tự`;document.getElementById('ruleLetter').classList.toggle('passed',checks[1]);document.getElementById('ruleLetter').textContent=`${checks[1]?'✓':'○'} Có chữ cái`;document.getElementById('ruleNumber').classList.toggle('passed',checks[2]);document.getElementById('ruleNumber').textContent=`${checks[2]?'✓':'○'} Có chữ số`;let score=checks.filter(Boolean).length;if(checks.every(Boolean)&&/[A-Z]/.test(value)&&/[^A-Za-zÀ-ỹ0-9]/.test(value))score=4;document.querySelector('.strength-meter').dataset.score=score;return checks.every(Boolean)}
resetPasswordInput.addEventListener('input',updatePasswordStrength);
document.querySelectorAll('.reset-toggle').forEach(button=>button.addEventListener('click',()=>{const input=document.getElementById(button.dataset.passwordTarget);input.type=input.type==='password'?'text':'password';button.textContent=input.type==='password'?'◉':'⊘'}));
document.getElementById('resetPasswordForm').addEventListener('submit',async event=>{event.preventDefault();const error=document.getElementById('resetPasswordError');const button=document.getElementById('resetSubmitButton');error.textContent='';if(!previewParams.get('resetToken')){error.textContent='Liên kết đặt lại mật khẩu không hợp lệ.';return}if(!updatePasswordStrength()){error.textContent='Mật khẩu cần ít nhất 8 ký tự, có chữ cái và chữ số.';return}if(resetPasswordInput.value!==resetPasswordConfirm.value){error.textContent='Mật khẩu xác nhận không khớp.';return}button.disabled=true;button.firstChild.textContent='Đang cập nhật ';try{const response=await fetch('api/auth/reset-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:previewParams.get('resetToken'),password:resetPasswordInput.value})});const result=await response.json().catch(()=>({}));if(!response.ok)throw new Error(result.message||'Không thể đặt lại mật khẩu.');document.getElementById('resetRequestPanel').classList.add('is-hidden');document.getElementById('resetSuccessPanel').classList.remove('is-hidden');history.replaceState(null,'','/');}catch(e){error.textContent=e.message==='Failed to fetch'?'Không kết nối được máy chủ. Vui lòng thử lại.':e.message}finally{button.disabled=false;button.firstChild.textContent='Cập nhật mật khẩu '}});
document.getElementById('loginAfterReset').addEventListener('click',()=>{window.location.href='/'});

// Only these screens have read APIs. Other screens explicitly show unavailable data.
const realSources = {
  'Sản phẩm & bảng giá': [['Sản phẩm','products'],['Bảng giá','price-lists']],
  'Đơn hàng': [['Đơn hàng','orders']],
  'Đại lý phụ trách': [['Đại lý','customers']],
  'Đại lý & hạn mức': [['Đại lý','customers']],
  'Hóa đơn': [['Hóa đơn','invoices']],
  'Hóa đơn & công nợ': [['Hóa đơn','invoices']],
  'Thanh toán': [['Thanh toán','payments']],
  'Thu tiền theo tuyến': [['Thanh toán','payments']],
  'Trả hàng': [['Trả hàng','returns']],
  'Tồn kho': [['Tồn kho','inventory'],['Kho hàng','warehouses']],
  'Nhà cung cấp': [['Nhà cung cấp','suppliers']]
};
const fieldLabels = {
  id:'ID', code:'Mã', sku:'SKU', name:'Tên', product_name:'Sản phẩm',
  customer_name:'Khách hàng', category_id:'ID nhóm hàng', category_name:'Nhóm hàng',
  unit:'Đơn vị', price:'Giá bán', selling_price:'Giá bán', min_stock:'Tồn tối thiểu',
  active:'Hoạt động', status:'Trạng thái', description:'Mô tả',
  created_at:'Ngày tạo', updated_at:'Cập nhật', order_date:'Ngày đặt',
  order_number:'Mã đơn', order_code:'Mã đơn', customer_id:'ID khách hàng',
  warehouse_id:'ID kho', product_id:'ID sản phẩm', quantity:'Số lượng',
  reserved_quantity:'Giữ chỗ', available_quantity:'Khả dụng', total_amount:'Tổng tiền',
  total:'Tổng tiền', amount:'Số tiền', paid_amount:'Đã thanh toán',
  due_date:'Hạn thanh toán', invoice_number:'Số hóa đơn', invoice_id:'ID hóa đơn',
  payment_date:'Ngày thanh toán', payment_method:'Hình thức thanh toán',
  phone:'Điện thoại', email:'Email', address:'Địa chỉ', credit_limit:'Hạn mức',
  contact_name:'Người liên hệ', tax_code:'Mã số thuế', note:'Ghi chú', reason:'Lý do'
};

function renderRealHome() {
  const menu = (typeof sprint2Menu === 'function' ? sprint2Menu() : roleConfigs[activeRole].menu).filter(view => view !== 'Tổng quan');
  moduleContent.innerHTML = `<section class="role-hero"><div><span class="eyebrow">KHÔNG GIAN LÀM VIỆC</span><h2>Chào ${escapeHtml(currentUser.fullName || currentUser.email || 'bạn')}</h2><p>${escapeHtml(roleConfigs[activeRole].label)} · Chọn một mục để xem dữ liệu được lưu trong hệ thống.</p></div></section><section class="panel"><div class="panel-heading"><div><h3>Truy cập nhanh</h3><p>Danh mục và thông tin làm việc của bạn.</p></div></div><div class="role-shortcuts">${menu.map(view => `<button data-open-view="${escapeHtml(view)}"><span>◇</span><strong>${escapeHtml(view)}</strong></button>`).join('')}</div></section>`;
  moduleContent.querySelectorAll('[data-open-view]').forEach(button => button.addEventListener('click', () => openView(button.dataset.openView)));
}

function dataError(response, payload) {
  if (response.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (response.status === 403) return 'Tài khoản hiện tại chưa được cấp quyền đọc dữ liệu này.';
  if (/relation .* does not exist/i.test(payload.message || '')) return 'Chưa có nguồn dữ liệu: bảng dữ liệu này chưa được khởi tạo trong hệ thống.';
  return 'Không thể tải dữ liệu từ máy chủ. Vui lòng thử lại.';
}

async function renderRealModule(view, sourceIndex = 0) {
  const revision = viewRevision;
  const sources = realSources[view];
  if (!sources) {
    moduleContent.innerHTML = `<section class="inventory-panel empty-module"><span>▦</span><h2>${escapeHtml(view)}</h2><p>Chức năng này chưa có nguồn dữ liệu được kết nối.</p></section>`;
    return;
  }
  const [label, endpoint] = sources[sourceIndex];
  moduleContent.innerHTML = `<section class="module-head"><div><h2>${escapeHtml(view)}</h2><p>Dữ liệu từ hệ thống · ${escapeHtml(label)}</p></div><button class="outline-button" id="reloadRecords">↻ Tải lại</button></section><section class="inventory-panel"><div class="inventory-toolbar"><select id="recordSource" class="filter-select">${sources.map(([name], index) => `<option value="${index}" ${index === sourceIndex ? 'selected' : ''}>${escapeHtml(name)}</option>`).join('')}</select><label class="search-control"><span>⌕</span><input id="recordSearch" type="search" placeholder="Tìm kiếm dữ liệu..." disabled></label><span id="recordCount" class="filter-result" aria-live="polite">Đang tải...</span></div><div id="recordTable" class="table-scroll" aria-live="polite"><p style="padding:24px">Đang tải dữ liệu...</p></div><div class="inventory-footer"><span id="recordFooter"></span><div id="recordPages" class="pagination"></div></div></section>`;
  const table = document.getElementById('recordTable');
  const count = document.getElementById('recordCount');
  const search = document.getElementById('recordSearch');
  const footer = document.getElementById('recordFooter');
  const pages = document.getElementById('recordPages');
  const isCurrent = () => revision === viewRevision && table.isConnected;
  document.getElementById('reloadRecords').addEventListener('click', () => renderRealModule(view, sourceIndex));
  document.getElementById('recordSource').addEventListener('change', event => renderRealModule(view, Number(event.target.value)));
  try {
    const response = await fetch(`api/${endpoint}/`, {cache:'no-store'});
    const payload = await response.json();
    if (!isCurrent()) return;
    if (!response.ok) throw new Error(dataError(response, payload));
    if (!Array.isArray(payload) || payload.some(row => !row || typeof row !== 'object' || Array.isArray(row))) throw new Error('Dữ liệu trả về không hợp lệ.');
    // Render actual fields only; unknown fields keep their original names.
    const columns = [...new Set(payload.flatMap(row => Object.keys(row)))];
    let page = 1;
    const size = 20;
    const valueText = value => value == null ? '—' : typeof value === 'boolean' ? (value ? 'Có' : 'Không') : String(value);
    const draw = () => {
      const query = search.value.trim().toLocaleLowerCase('vi');
      const rows = payload.filter(row => columns.some(key => valueText(row[key]).toLocaleLowerCase('vi').includes(query)));
      const totalPages = Math.max(1, Math.ceil(rows.length / size));
      page = Math.min(page, totalPages);
      count.textContent = `${number(rows.length)} kết quả / ${number(payload.length)} bản ghi`;
      footer.textContent = `Trang ${page} / ${totalPages}`;
      table.innerHTML = rows.length ? `<table class="feature-table"><thead><tr>${columns.map(key => `<th>${escapeHtml(fieldLabels[key] || key)}</th>`).join('')}</tr></thead><tbody>${rows.slice((page-1)*size,page*size).map(row => `<tr>${columns.map(key => `<td>${escapeHtml(valueText(row[key]))}</td>`).join('')}</tr>`).join('')}</tbody></table>` : `<p style="padding:32px;text-align:center">${payload.length ? 'Không tìm thấy dữ liệu phù hợp.' : 'Chưa có dữ liệu được lưu trong hệ thống.'}</p>`;
      pages.innerHTML = `<button id="recordsPrevious" ${page === 1 ? 'disabled' : ''}>‹</button><button disabled>${page}</button><button id="recordsNext" ${page === totalPages ? 'disabled' : ''}>›</button>`;
      document.getElementById('recordsPrevious').onclick = () => { page--; draw(); };
      document.getElementById('recordsNext').onclick = () => { page++; draw(); };
    };
    search.disabled = false;
    search.addEventListener('input', () => { page = 1; draw(); });
    draw();
  } catch (error) {
    if (!isCurrent()) return;
    count.textContent = 'Chưa tải được dữ liệu';
    table.innerHTML = `<p role="alert" style="padding:32px;text-align:center">${escapeHtml(error.message === 'Failed to fetch' ? 'Không kết nối được máy chủ. Vui lòng thử lại.' : error.message)}</p>`;
    footer.textContent = 'Nhấn Tải lại để thử lại.';
  }
}
