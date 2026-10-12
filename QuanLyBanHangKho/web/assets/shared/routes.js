function getAvailableMenu() {
 const menu=["Tổng quan"];
 if (true) menu.push("Đổi mật khẩu");
 return menu;
}

function renderAccessError(view) { moduleContent.textContent="Bạn không có quyền truy cập chức năng này."; }
function renderFeatureView(view) {
 if(view==="Đổi mật khẩu") { renderChangePassword(); return true; }
 return false;
}
