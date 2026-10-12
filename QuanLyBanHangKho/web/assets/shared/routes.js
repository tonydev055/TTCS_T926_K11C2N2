function getAvailableMenu() {
 const menu=["Tổng quan"];
 if (hasPermission('admin.users')) menu.push("Người dùng");
 if (hasPermission('admin.roles')) menu.push("Vai trò & quyền");
 if (true) menu.push("Đổi mật khẩu");
 return menu;
}

function renderAccessError(view, message = 'Bạn chưa được cấp quyền sử dụng chức năng này.') {
  dashboardContent.classList.add('is-hidden');
  moduleContent.classList.remove('is-hidden');
  moduleContent.innerHTML = `<section class="access-state"><div class="access-state-icon">!</div><span class="eyebrow">KHÔNG THỂ TRUY CẬP</span><h2>${escapeHtml(view || 'Chức năng')}</h2><p>${escapeHtml(message)}</p><button class="orange-button" id="accessBackHome">Về trang tổng quan</button></section>`;
  document.getElementById('accessBackHome').onclick = () => openView('Tổng quan');
}
function renderFeatureView(view) {
 if(view==="Người dùng") { renderUsersModule(); return true; }
 if(view==="Vai trò & quyền") { renderPermissionMatrix(); return true; }
 if(view==="Đổi mật khẩu") { renderChangePassword(); return true; }
 return false;
}
