function openFormDialog(title, body, submitLabel = 'Lưu dữ liệu') {
  document.getElementById('s2Dialog')?.remove();
  const modal = document.createElement('div');
  modal.id = 's2Dialog';
  modal.className = 'form-modal open';
  modal.innerHTML = `
    <div class="form-dialog" role="dialog" aria-modal="true" aria-labelledby="s2DialogTitle">
      <header>
        <div>
          <span>THÔNG TIN</span>
          <h2 id="s2DialogTitle">${escapeHtml(title)}</h2>
          <p>Trường có dấu * là bắt buộc.</p>
        </div>
        <button type="button" id="s2Close" aria-label="Đóng">×</button>
      </header>
      <form id="s2Form">
        <div class="form-fields">${body}</div>
        <p class="s2-form-error" id="s2FormError" role="alert"></p>
        <footer>
          <button type="button" class="outline-button" id="s2Cancel">Đóng</button>
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
  };
  document.getElementById('s2Close').onclick = close;
  document.getElementById('s2Cancel').onclick = close;
  modal.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') close();
    if (event.key === 'Tab') {
      const fields = [...modal.querySelectorAll('button,input,select,textarea')].filter(
        (field) => !field.disabled
      );
      if (event.shiftKey && document.activeElement === fields[0]) {
        event.preventDefault();
        fields.at(-1).focus();
      } else if (!event.shiftKey && document.activeElement === fields.at(-1)) {
        event.preventDefault();
        fields[0].focus();
      }
    }
  });
  modal.querySelector('input,select,button')?.focus();
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
