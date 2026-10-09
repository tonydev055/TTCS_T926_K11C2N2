function getAvailableMenu() {
  const menu = ['Tổng quan'];
  if (hasPermission('admin.users')) menu.push('Người dùng');
  if (hasPermission('admin.roles')) menu.push('Vai trò & quyền');
  if (hasPermission('products.read')) menu.push('Sản phẩm & bảng giá');
  if (hasPermission('prices.write')) menu.push('Chính sách chiết khấu');
  if (hasPermission('prices.write')) menu.push('Lịch sử thay đổi giá');
  if (hasPermission('customers.read') || hasPermission('customers.assigned')) menu.push('Đại lý');
  if (hasPermission('suppliers.read')) menu.push('Nhà cung cấp');
  if (hasPermission('admin.audit')) menu.push('Nhật ký hệ thống');
  if (hasPermission('admin.users')) menu.push('Nhập người dùng Excel');
  if (hasPermission('customers.assigned')) menu.push('Điểm giao hàng');
  if (hasPermission('orders.own')) menu.push('Tạo đơn hàng');
  menu.push('Hồ sơ cá nhân', 'Đổi mật khẩu');
  return [...new Set(menu)];
}

function renderAccessError(view, message = 'Bạn chưa được cấp quyền sử dụng chức năng này.') {
  dashboardContent.classList.add('is-hidden');
  moduleContent.classList.remove('is-hidden');
  moduleContent.innerHTML = `<section class="access-state"><div class="access-state-icon">!</div><span class="eyebrow">KHÔNG THỂ TRUY CẬP</span><h2>${escapeHtml(view || 'Chức năng')}</h2><p>${escapeHtml(message)}</p><button class="orange-button" id="accessBackHome">Về trang tổng quan</button></section>`;
  document.getElementById('accessBackHome').onclick = () => openView('Tổng quan');
}
function renderFeatureView(view) {
  if (view === 'Chính sách chiết khấu') {
    discountFeature.render();
    return true;
  }
  if (view === 'Đại lý') {
    agentFeature.render();
    return true;
  }
  if (view === 'Lịch sử thay đổi giá') {
    priceHistoryFeature.render();
    return true;
  }
  if (view === 'Đổi mật khẩu') {
    renderChangePassword();
    return true;
  }
  if (view === 'Sản phẩm & bảng giá') {
    renderCatalog('products');
    return true;
  }
  if (view === 'Nhà cung cấp') {
    renderCatalog('suppliers');
    return true;
  }
  if (view === 'Hồ sơ cá nhân') {
    renderProfile();
    return true;
  }
  if (view === 'Nhật ký hệ thống') {
    renderAuditLog();
    return true;
  }
  if (view === 'Nhập người dùng Excel') {
    renderExcelImport('users');
    return true;
  }
  if (view === 'Vai trò & quyền') {
    renderPermissionMatrix();
    return true;
  }
  if (view === 'Điểm giao hàng') { 
    deliveryPointFeature.render(); 
    return true; }
  if (view === 'Tạo đơn hàng') { 
    salesOrderFeature.render(); 
    return true; }
  return false;
}
