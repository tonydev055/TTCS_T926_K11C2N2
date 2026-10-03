function showToast(message) {
  const toast = document.getElementById('toast');
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 2600);
}

function number(value) {
  return new Intl.NumberFormat('vi-VN').format(value);
}
function escapeHtml(value) {
  const element = document.createElement('div');
  element.textContent = value ?? '';
  return element.innerHTML.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
