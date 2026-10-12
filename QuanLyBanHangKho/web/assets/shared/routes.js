function getAvailableMenu() {
 const menu=["Tổng quan"];
 if (hasPermission('admin.roles')) menu.push("Vai trò & quyền");
 return menu;
}

function renderAccessError(view) { moduleContent.textContent="Bạn không có quyền truy cập chức năng này."; }
function renderFeatureView(view) {
 if(view==="Vai trò & quyền") { renderPermissionMatrix(); return true; }
 return false;
}
