function getAvailableMenu() {
  const roles = currentUser.roles || [activeRole];
  const menu = [...new Set(roles.flatMap((role) => roleConfigs[role]?.menu || []))];
  if (hasPermission('products.read') && !menu.includes('Sản phẩm & bảng giá'))
    menu.splice(1, 0, 'Sản phẩm & bảng giá');
  if (hasPermission('suppliers.read') && !menu.includes('Nhà cung cấp')) menu.push('Nhà cung cấp');
  if (hasPermission('admin.users')) menu.push('Nhập người dùng Excel');
  menu.push('Hồ sơ cá nhân', 'Đổi mật khẩu');
  return menu;
}
function renderFeatureView(view) {
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
  if (view === 'Danh mục dùng chung') {
    renderCatalog('categories');
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
  return false;
}
