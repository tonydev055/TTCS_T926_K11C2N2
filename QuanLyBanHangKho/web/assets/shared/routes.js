function sprint2Menu() {
  const roles=currentUser.roles || [activeRole];
  const menu=[...new Set(roles.flatMap(role=>roleConfigs[role]?.menu || []))];
  if(s2Can('products.read')&&!menu.includes('Sản phẩm & bảng giá'))menu.splice(1,0,'Sản phẩm & bảng giá');
  if(s2Can('suppliers.read')&&!menu.includes('Nhà cung cấp'))menu.push('Nhà cung cấp');
  if(s2Can('admin.users'))menu.push('Nhập người dùng Excel');
  menu.push('Hồ sơ cá nhân','Đổi mật khẩu');
  return menu;
}
function openSprint2View(view) {
  if(view==='Đổi mật khẩu'){renderChangePassword();return true;}
  if(view==='Sản phẩm & bảng giá'){s2Catalog('products');return true;}
  if(view==='Nhà cung cấp'){s2Catalog('suppliers');return true;}
  if(view==='Danh mục dùng chung'){s2Catalog('categories');return true;}
  if(view==='Hồ sơ cá nhân'){s2Profile();return true;}
  if(view==='Nhật ký hệ thống'){s2Audit();return true;}
  if(view==='Nhập người dùng Excel'){s2Import('users');return true;}
  return false;
}
