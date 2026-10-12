function getAvailableMenu() {
 const menu=["Tổng quan"];
 if (hasPermission('admin.users')) menu.push("Người dùng");
 if (hasPermission('admin.roles')) menu.push("Vai trò & quyền");
 if (true) menu.push("Đổi mật khẩu");
 return menu;
}

function renderAccessError(view) { moduleContent.textContent="Bạn không có quyền truy cập chức năng này."; }
function renderFeatureView(view) {
 if(view==="Người dùng") { renderUsersModule(); return true; }
 if(view==="Vai trò & quyền") { renderPermissionMatrix(); return true; }
 if(view==="Đổi mật khẩu") { renderChangePassword(); return true; }
 return false;
}
