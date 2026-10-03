const realSources = {
  'Sản phẩm & bảng giá': [
    ['Sản phẩm', 'products'],
    ['Bảng giá', 'price-lists']
  ],
  'Nhà cung cấp': [['Nhà cung cấp', 'suppliers']]
};
const fieldLabels = {
  id: 'ID',
  code: 'Mã',
  sku: 'SKU',
  name: 'Tên',
  product_name: 'Sản phẩm',
  customer_name: 'Khách hàng',
  category_id: 'ID nhóm hàng',
  category_name: 'Nhóm hàng',
  unit: 'Đơn vị',
  price: 'Giá bán',
  selling_price: 'Giá bán',
  min_stock: 'Tồn tối thiểu',
  active: 'Hoạt động',
  status: 'Trạng thái',
  description: 'Mô tả',
  created_at: 'Ngày tạo',
  updated_at: 'Cập nhật',
  order_date: 'Ngày đặt',
  order_number: 'Mã đơn',
  order_code: 'Mã đơn',
  customer_id: 'ID khách hàng',
  warehouse_id: 'ID kho',
  product_id: 'ID sản phẩm',
  quantity: 'Số lượng',
  reserved_quantity: 'Giữ chỗ',
  available_quantity: 'Khả dụng',
  total_amount: 'Tổng tiền',
  total: 'Tổng tiền',
  amount: 'Số tiền',
  paid_amount: 'Đã thanh toán',
  due_date: 'Hạn thanh toán',
  invoice_number: 'Số hóa đơn',
  invoice_id: 'ID hóa đơn',
  payment_date: 'Ngày thanh toán',
  payment_method: 'Hình thức thanh toán',
  phone: 'Điện thoại',
  email: 'Email',
  address: 'Địa chỉ',
  credit_limit: 'Hạn mức',
  contact_name: 'Người liên hệ',
  tax_code: 'Mã số thuế',
  note: 'Ghi chú',
  reason: 'Lý do'
};

function dataError(response, payload) {
  if (response.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (response.status === 403) return 'Tài khoản hiện tại chưa được cấp quyền đọc dữ liệu này.';
  if (/relation .* does not exist/i.test(payload.message || ''))
    return 'Chưa có nguồn dữ liệu: bảng dữ liệu này chưa được khởi tạo trong hệ thống.';
  return 'Không thể tải dữ liệu từ máy chủ. Vui lòng thử lại.';
}

async function renderRealModule(view, sourceIndex = 0) {
  const revision = viewRevision;
  const sources = realSources[view];
  if (!sources) {
    moduleContent.innerHTML = `<section class="inventory-panel empty-module"><span>▦</span><h2>${escapeHtml(view)}</h2><p>Chức năng này chưa có nguồn dữ liệu được kết nối.</p></section>`;
    return;
  }
  const [label, endpoint] = sources[sourceIndex];
  moduleContent.innerHTML = `<section class="module-head"><div><h2>${escapeHtml(view)}</h2><p>Dữ liệu từ hệ thống · ${escapeHtml(label)}</p></div><button class="outline-button" id="reloadRecords">↻ Tải lại</button></section><section class="inventory-panel"><div class="inventory-toolbar"><select id="recordSource" class="filter-select">${sources.map(([name], index) => `<option value="${index}" ${index === sourceIndex ? 'selected' : ''}>${escapeHtml(name)}</option>`).join('')}</select><label class="search-control"><span>⌕</span><input id="recordSearch" type="search" placeholder="Tìm kiếm dữ liệu..." disabled></label><span id="recordCount" class="filter-result" aria-live="polite">Đang tải...</span></div><div id="recordTable" class="table-scroll" aria-live="polite"><p style="padding:24px">Đang tải dữ liệu...</p></div><div class="inventory-footer"><span id="recordFooter"></span><div id="recordPages" class="pagination"></div></div></section>`;
  const table = document.getElementById('recordTable');
  const count = document.getElementById('recordCount');
  const search = document.getElementById('recordSearch');
  const footer = document.getElementById('recordFooter');
  const pages = document.getElementById('recordPages');
  const isCurrent = () => revision === viewRevision && table.isConnected;
  document
    .getElementById('reloadRecords')
    .addEventListener('click', () => renderRealModule(view, sourceIndex));
  document
    .getElementById('recordSource')
    .addEventListener('change', (event) => renderRealModule(view, Number(event.target.value)));
  try {
    const response = await fetch(`api/${endpoint}/`, { cache: 'no-store' });
    const payload = await response.json();
    if (!isCurrent()) return;
    if (!response.ok) throw new Error(dataError(response, payload));
    if (
      !Array.isArray(payload) ||
      payload.some((row) => !row || typeof row !== 'object' || Array.isArray(row))
    )
      throw new Error('Dữ liệu trả về không hợp lệ.');
    // Render actual fields only; unknown fields keep their original names.
    const columns = [...new Set(payload.flatMap((row) => Object.keys(row)))];
    let page = 1;
    const size = 20;
    const valueText = (value) =>
      value == null ? '—' : typeof value === 'boolean' ? (value ? 'Có' : 'Không') : String(value);
    const draw = () => {
      const query = search.value.trim().toLocaleLowerCase('vi');
      const rows = payload.filter((row) =>
        columns.some((key) => valueText(row[key]).toLocaleLowerCase('vi').includes(query))
      );
      const totalPages = Math.max(1, Math.ceil(rows.length / size));
      page = Math.min(page, totalPages);
      count.textContent = `${number(rows.length)} kết quả / ${number(payload.length)} bản ghi`;
      footer.textContent = `Trang ${page} / ${totalPages}`;
      table.innerHTML = rows.length
        ? `<table class="feature-table"><thead><tr>${columns.map((key) => `<th>${escapeHtml(fieldLabels[key] || key)}</th>`).join('')}</tr></thead><tbody>${rows
            .slice((page - 1) * size, page * size)
            .map(
              (row) =>
                `<tr>${columns.map((key) => `<td>${escapeHtml(valueText(row[key]))}</td>`).join('')}</tr>`
            )
            .join('')}</tbody></table>`
        : `<p style="padding:32px;text-align:center">${payload.length ? 'Không tìm thấy dữ liệu phù hợp.' : 'Chưa có dữ liệu được lưu trong hệ thống.'}</p>`;
      pages.innerHTML = `<button id="recordsPrevious" ${page === 1 ? 'disabled' : ''}>‹</button><button disabled>${page}</button><button id="recordsNext" ${page === totalPages ? 'disabled' : ''}>›</button>`;
      document.getElementById('recordsPrevious').onclick = () => {
        page--;
        draw();
      };
      document.getElementById('recordsNext').onclick = () => {
        page++;
        draw();
      };
    };
    search.disabled = false;
    search.addEventListener('input', () => {
      page = 1;
      draw();
    });
    draw();
  } catch (error) {
    if (!isCurrent()) return;
    count.textContent = 'Chưa tải được dữ liệu';
    table.innerHTML = `<p role="alert" style="padding:32px;text-align:center">${escapeHtml(error.message === 'Failed to fetch' ? 'Không kết nối được máy chủ. Vui lòng thử lại.' : error.message)}</p>`;
    footer.textContent = 'Nhấn Tải lại để thử lại.';
  }
}
