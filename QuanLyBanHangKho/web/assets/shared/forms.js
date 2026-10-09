function manageDialogFocus(modal, close) {
  const previous = document.activeElement;
  const onKey = (event) => {
    if (event.key === 'Escape') { event.preventDefault(); close(); return; }
    if (event.key !== 'Tab') return;
    const fields = [...modal.querySelectorAll('button,input,select,textarea,a[href]')]
      .filter((field) => !field.disabled && field.type !== 'hidden' && field.getClientRects().length);
    if (!fields.length) return;
    const first = fields[0], last = fields.at(-1);
    if (!modal.contains(document.activeElement) || (event.shiftKey && document.activeElement === first)) {
      event.preventDefault(); (event.shiftKey ? last : first).focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault(); first.focus();
    }
  };
  modal.addEventListener('keydown', onKey);
  const focusFirst = () => {
    if (modal.isConnected && modal.classList.contains('open') && !modal.contains(document.activeElement)) {
      const field = modal.querySelector('input:not([type="hidden"]),select,textarea') || modal.querySelector('button');
      field?.focus();
    }
  };
  // Wait for visibility styles to apply before moving keyboard focus.
  requestAnimationFrame(focusFirst);
  const focusTimer = setTimeout(focusFirst, 220);
  return () => {
    clearTimeout(focusTimer);
    modal.removeEventListener('keydown', onKey);
    if (previous?.isConnected) previous.focus();
  };
}
function openFormDialog(title, body, submitLabel = 'Lưu thay đổi') {
  document.getElementById('s2Dialog')?.remove();
  const modal = document.createElement('div');
  modal.id = 's2Dialog';
  modal.className = 'form-modal open';
  modal.innerHTML = `
    <div class="form-dialog" role="dialog" aria-modal="true" aria-labelledby="s2DialogTitle">
      <header>
        <div>
          <h2 id="s2DialogTitle">${escapeHtml(title)}</h2>
          <p>Trường có dấu * là bắt buộc.</p>
        </div>
        <button type="button" id="s2Close" aria-label="Đóng">×</button>
      </header>
      <form id="s2Form">
        <div class="form-fields">${body}</div>
        <p class="s2-form-error" id="s2FormError" role="alert"></p>
        <footer>
          <button type="button" class="outline-button" id="s2Cancel">${submitLabel ? 'Hủy' : 'Đóng'}</button>
          ${submitLabel ? `<button type="submit" class="orange-button">${submitLabel}</button>` : ''}
        </footer>
      </form>
    </div>
  `;
  document.body.append(modal);
  document.body.style.overflow = 'hidden';
  const close = () => {
    modal.remove();
    document.body.style.overflow = '';
    restoreFocus();
  };
  document.getElementById('s2Close').onclick = close;
  document.getElementById('s2Cancel').onclick = close;
  const form = document.getElementById('s2Form');
  form.addEventListener('invalid', (event) => {
    event.preventDefault();
    const field = event.target;
    const firstInvalid = form.querySelector('[aria-invalid="true"]');
    field.setAttribute('aria-invalid', 'true');
    if (!firstInvalid || firstInvalid === field) field.focus();
    const group = field.closest('.form-group');
    if (!group) return;
    group.classList.add('invalid');
    let message = group.querySelector('.field-error');
    if (!message) {
      message = document.createElement('small');
      message.className = 'field-error';
      message.id = `error-${field.name}`;
      field.setAttribute('aria-describedby', message.id);
      group.append(message);
    }
    message.textContent = field.validity.valueMissing ? 'Vui lòng điền trường này.' : 'Giá trị chưa hợp lệ. Vui lòng kiểm tra lại.';
  }, true);
  form.addEventListener('input', (event) => {
    if (event.target.validity?.valid) {
      event.target.removeAttribute('aria-invalid');
      event.target.closest('.form-group')?.classList.remove('invalid');
    }
  });
  const restoreFocus = manageDialogFocus(modal, close);
  return { modal, form: document.getElementById('s2Form'), close };
}
function renderInputField(
  name,
  label,
  value = '',
  type = 'text',
  required = true,
  options = null,
  readonly = false
) {
  let control;
  if (options)
    control = `<select name="${name}" ${required ? 'required' : ''} ${readonly ? 'disabled' : ''}><option value="">Chọn...</option>${options.map(([id, text]) => `<option value="${escapeHtml(id)}" ${String(id) === String(value) ? 'selected' : ''}>${escapeHtml(text)}</option>`).join('')}</select>`;
  else if (type === 'textarea')
    control = `<textarea name="${name}" ${required ? 'required' : ''}>${escapeHtml(value)}</textarea>`;
  else
    control = `<input name="${name}" type="${type}" value="${type === 'file' ? '' : escapeHtml(value)}" ${required ? 'required' : ''} ${readonly ? 'readonly' : ''} ${type === 'number' ? 'min="0" step="any"' : ''} ${type === 'file' ? 'accept="image/png,image/jpeg"' : ''}>`;
  return `<div class="form-group ${type === 'textarea' ? 'full' : ''}"><label>${label}${required ? ' *' : ''}${control}</label></div>`;
}
