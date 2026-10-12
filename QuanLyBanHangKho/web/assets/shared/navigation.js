function readViewFromUrl() {
  try { return decodeURIComponent((window.location.hash || '').slice(1)) || 'Tổng quan'; }
  catch (_) { return 'Tổng quan'; }
}
function openApp(user = {}) {
  currentUser = user;
  loginView.classList.add('is-hidden');
  registerView.classList.add('is-hidden');
  appView.classList.remove('is-hidden');
  activeRole = roleConfigs[user.role]
    ? user.role
    : user.role === 'WH_MANAGER'
      ? user.role
      : activeRole;
  const config = roleConfigs[activeRole];
  const displayName = user.fullName || user.email || 'Người dùng';
  document.querySelector('.user-chip strong').textContent = displayName;
  document.querySelector('.user-chip small').textContent = config.label;
  const workAreas = user.warehouses?.length
    ? user.warehouses.join(', ')
    : user.territory || 'Toàn hệ thống';
  const contextBadge = document.getElementById('workContextBadge');
  if (contextBadge) contextBadge.querySelector('strong').textContent = workAreas;
  document.querySelector('.avatar').textContent = user.fullName
    ? displayName
        .split(' ')
        .slice(-2)
        .map((word) => word[0])
        .join('')
        .toUpperCase()
    : 'ND';
  buildNavigation(typeof getAvailableMenu === 'function' ? getAvailableMenu() : config.menu);
  sessionStorage.setItem(
    'khoFlowSession',
    JSON.stringify({ fullName: displayName, role: activeRole })
  );
  const requested = readViewFromUrl();
  openView(getAvailableMenu().includes(requested) ? requested : 'Tổng quan', true);
}

function openView(view, replaceUrl = false) {
  if (currentUser.requiresPasswordChange) view = 'Đổi mật khẩu';
  const menu =
    typeof getAvailableMenu === 'function' ? getAvailableMenu() : roleConfigs[activeRole].menu;
  if (!menu.includes(view)) {
    viewRevision++;
    document.getElementById('breadcrumb').textContent = 'Không đủ quyền';
    document.getElementById('pageTitle').textContent = 'Truy cập bị từ chối';
    renderAccessError(view);
    closeSidebar();
    return;
  }
  viewRevision++;
  if (typeof activeFormContext !== 'undefined' && activeFormContext) closeEntityForm();
  document.getElementById('s2Dialog')?.remove();
  if (document.body) document.body.style.overflow = '';
  if (window.history) {
    const hash = '#' + encodeURIComponent(view);
    if (window.location.hash !== hash) {
      window.history[replaceUrl ? 'replaceState' : 'pushState'](null, '', hash);
    }
  }
  const feature = {
    'Tổng quan': 'tong-quan', 'Người dùng': 'nguoi-dung',
"Người dùng": "nguoi-dung","Vai trò & quyền": "phan-quyen","Đổi mật khẩu": "xac-thuc",
    'Sản phẩm & bảng giá': 'danh-muc', 'Nhà cung cấp': 'danh-muc',
    'Vai trò & quyền': 'phan-quyen', 'Nhật ký hệ thống': 'nhat-ky',
    'Nhập người dùng Excel': 'nhap-excel', 'Hồ sơ cá nhân': 'ho-so'
  }[view];
  if (typeof failedFeatures !== 'undefined' && failedFeatures.has(feature)) {
    renderAccessError(view, 'Không tải được giao diện chức năng. Kiểm tra kết nối rồi thử lại.');
    const button = document.getElementById('accessBackHome');
    button.textContent = 'Thử lại';
    button.onclick = async () => {
      const revision = viewRevision;
      button.disabled = true;
      try {
        const link = [...document.querySelectorAll('[data-feature]')].find((item) => item.getAttribute('href').startsWith(feature + '/'));
        await loadFeature(link.getAttribute('href'));
        if (revision === viewRevision) openView(view);
      } catch (_) { button.disabled = false; }
    };
    closeSidebar();
    return;
  }
  document
    .querySelectorAll('.nav-item')
    .forEach((nav) => nav.classList.toggle('active', nav.dataset.view === view));
  document.getElementById('breadcrumb').textContent = view;
  document.getElementById('pageTitle').textContent = view;
  dashboardContent.classList.add('is-hidden');
  moduleContent.classList.remove('is-hidden');
  if (typeof renderFeatureView === 'function' && renderFeatureView(view)) {
    closeSidebar();
    return;
  }
  if (view === 'Tổng quan') renderRealHome();
  else if (view === 'Người dùng') renderUsersModule();
  else renderRealModule(view);
  closeSidebar();
}

function buildNavigation(menu) {
  document.querySelector('.nav-list').innerHTML=menu.map(view => '<button class="nav-item" data-view="'+escapeHtml(view)+'">'+escapeHtml(view)+'</button>').join('');
  document.querySelectorAll('.nav-item').forEach(item=>item.addEventListener('click',()=>openView(item.dataset.view)));
 }

document
  .querySelectorAll('[data-toast]')
  .forEach((button) => button.addEventListener('click', () => showToast(button.dataset.toast)));
const sidebar = document.getElementById('sidebar');
const sidebarOverlay = document.getElementById('sidebarOverlay');
function closeSidebar() {
  sidebar.classList.remove('open');
  sidebarOverlay.classList.remove('show');
}
document.getElementById('openSidebar').addEventListener('click', () => {
  sidebar.classList.add('open');
  sidebarOverlay.classList.add('show');
});
document.getElementById('closeSidebar').addEventListener('click', closeSidebar);
sidebarOverlay.addEventListener('click', closeSidebar);
window.addEventListener?.('popstate', () => {
  if (Object.keys(currentUser).length) openView(readViewFromUrl(), true);
});
