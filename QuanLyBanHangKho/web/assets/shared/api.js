function hasPermission(permission) {
  return (currentUser.permissions || []).includes(permission);
}
function expireSession() {
  if (!Object.keys(currentUser).length) return;
  currentUser = {};
  viewRevision++;
  sessionStorage.removeItem?.('khoFlowSession');
  document.querySelectorAll('.form-modal.open').forEach((modal) => {
    if (modal.id === 's2Dialog') modal.remove();
    else { modal.classList.remove('open'); modal.setAttribute('aria-hidden', 'true'); }
  });
  if (document.body) document.body.style.overflow = '';
  appView.classList.add('is-hidden');
  registerView.classList.add('is-hidden');
  loginView.classList.remove('is-hidden');
  passwordInput.value = '';
  loginError.textContent = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  emailInput.focus();
}
async function apiFetch(url, options = {}) {
  const response = await fetch(url, { ...options, signal: options.signal || AbortSignal.timeout(30000) });
  if (response.status === 401 && !url.startsWith('api/auth/login')) expireSession();
  return response;
}
async function apiRequest(path, options = {}) {
  const response = await apiFetch(`api/${path}`, { cache: 'no-store', ...options });
  const data = await response
    .json()
    .catch(() => ({ message: 'Máy chủ trả về dữ liệu không hợp lệ' }));
  if (!response.ok)
    throw new Error(
      response.status === 401
        ? 'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.'
        : data.message || 'Không thể xử lý yêu cầu'
    );
  return data;
}
function jsonRequestOptions(method, data) {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) };
}
function showRequestError(error) {
  showToast(error.message || 'Không thể xử lý yêu cầu');
}
const formatMoney = (value) =>
  value == null
    ? '—'
    : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
