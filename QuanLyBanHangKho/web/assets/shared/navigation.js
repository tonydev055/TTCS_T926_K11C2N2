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
  openView('Tổng quan');
}

function openView(view) {
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
  const accountItems = new Set(['Người dùng', 'Vai trò & quyền', 'Nhật ký hệ thống', 'Nhập người dùng Excel']);
  const personalItems = new Set(['Hồ sơ cá nhân', 'Đổi mật khẩu']);
  const main = menu.filter((item) => !accountItems.has(item) && !personalItems.has(item));
  const control = menu.filter((item) => accountItems.has(item));
  const personal = menu.filter((item) => personalItems.has(item));
  const iconMap = {
    'Tổng quan': '▦',
    'Tồn kho': '▤',
    'Nhập kho': '↙',
    'Xuất kho': '↗',
    'Chuyển kho': '⇄',
    'Lô & hạn dùng': '◷',
    'Kiểm kê': '✓',
    'Nhà cung cấp': '◇',
    'Báo cáo kho': '▥',
    'Sản phẩm & bảng giá': '▤',
    'Đơn hàng': '▣',
    'Giao hàng': '↗',
    'Hóa đơn & công nợ': '₫',
    'Trả hàng': '↩',
    'Đại lý phụ trách': '◇',
    'Thu tiền theo tuyến': '₫',
    'Chỉ tiêu cá nhân': '◎',
    'Đại lý & hạn mức': '◇',
    'Duyệt ngoại lệ': '✓',
    'Doanh số & chỉ tiêu': '◎',
    'Báo cáo kinh doanh': '▥',
    'Hóa đơn': '▤',
    'Thanh toán': '₫',
    'Sổ công nợ': '▥',
    'Đối chiếu công nợ': '✓',
    'Báo cáo công nợ': '▥',
    'Người dùng': '♙',
    'Vai trò & quyền': '⌘',
    'Danh mục dùng chung': '▤',
    'Nhật ký hệ thống': '◷',
    'Cấu hình': '⚙'
    ,'Hồ sơ cá nhân': '♙'
    ,'Đổi mật khẩu': '⌁'
    ,'Nhập người dùng Excel': '⇧'
  };
  const items = (values) =>
    values
      .map(
        (view) =>
          `<button class="nav-item ${view === 'Tổng quan' ? 'active' : ''}" data-view="${view}"><span>${iconMap[view] || '◇'}</span>${view}</button>`
      )
      .join('');
  document.querySelector('.nav-list').innerHTML =
    `<p class="nav-label">NGHIỆP VỤ</p>${items(main)}${control.length ? `<p class="nav-label">QUẢN TRỊ & KIỂM SOÁT</p>${items(control)}` : ''}${personal.length ? `<p class="nav-label">TÀI KHOẢN</p>${items(personal)}` : ''}`;
  document
    .querySelectorAll('.nav-item')
    .forEach((item) => item.addEventListener('click', () => openView(item.dataset.view)));
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
