/* S4-08: in hoặc xuất PDF đơn hàng (Lưu dưới dạng PDF trong hộp thoại in của trình duyệt).
 * Mẫu in gồm thông tin đại lý, điểm giao, dòng hàng, chiết khấu, tổng tiền, mã đơn và mã vạch Code128
 * để kho quét tra cứu nhanh. Đơn chưa duyệt/đã huỷ in kèm dấu chìm để không bị hiểu nhầm là đơn đã chốt.
 */
(() => {
  // Code128: mẫu độ rộng vạch/khoảng trắng cho giá trị 0–105 (103–105 là ký tự bắt đầu A/B/C) và ký tự kết thúc.
  const PATTERNS = '212222 222122 222221 121223 121322 131222 122213 122312 132212 221213 221312 231212 112232 122132 122231 113222 123122 123221 223211 221132 221231 213212 223112 312131 311222 321122 321221 312212 322112 322211 212123 212321 232121 111323 131123 131321 112313 132113 132311 211313 231113 231311 112133 112331 132131 113123 113321 133121 313121 211331 231131 213113 213311 213131 311123 311321 331121 312113 312311 332111 314111 221411 431111 111224 111422 121124 121421 141122 141221 112214 112412 122114 122411 142112 142211 241211 221114 413111 241112 134111 111242 121142 121241 114212 124112 124211 411212 421112 421211 212141 214121 412121 111143 111341 131141 114113 114311 411113 411311 113141 114131 311141 411131 211412 211214 211232'.split(' ');
  const STOP = '2331112';

  /** Mã hoá chuỗi ASCII 32–126 theo Code128 bộ B; trả về chuỗi độ rộng (bắt đầu bằng vạch đen). */
  function code128(text) {
    const values = [104];
    for (const ch of String(text)) {
      const code = ch.charCodeAt(0);
      if (code < 32 || code > 126) throw new Error('Mã đơn chứa ký tự không in được mã vạch');
      values.push(code - 32);
    }
    const checksum = values.reduce((sum, value, index) => sum + value * (index || 1), 0) % 103;
    return [...values, checksum].map((value) => PATTERNS[value]).join('') + STOP;
  }

  /** SVG mã vạch; mỗi module rộng `unit` px, có vùng trống 10 module hai bên. */
  function barcodeSvg(text, unit = 2, height = 54) {
    const widths = code128(text);
    const quiet = 10 * unit;
    let x = quiet;
    let bars = '';
    [...widths].forEach((digit, index) => {
      const w = Number(digit) * unit;
      if (index % 2 === 0) bars += `<rect x="${x}" y="0" width="${w}" height="${height}"/>`;
      x += w;
    });
    const total = x + quiet;
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${total}" height="${height}" viewBox="0 0 ${total} ${height}" role="img" aria-label="Mã vạch ${escapeHtml(text)}" shape-rendering="crispEdges"><rect width="${total}" height="${height}" fill="#fff"/><g fill="#000">${bars}</g></svg>`;
  }

  const WATERMARK = {
    DRAFT: 'NHÁP · CHƯA DUYỆT', PENDING_CONFIRM: 'CHƯA XÁC NHẬN', PENDING_APPROVAL: 'CHỜ DUYỆT',
    REJECTED: 'ĐÃ TỪ CHỐI', CANCELLED: 'ĐÃ HUỶ'
  };

  function orderPrintHtml(order) {
    const esc = (value) => escapeHtml(value == null ? '' : String(value));
    const money = (value) => `${Number(value || 0).toLocaleString('vi-VN', { maximumFractionDigits: 2 })} đ`;
    const qty = (value) => Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 6 });
    const day = (value) => {
      if (!value) return '—';
      const [y, m, d] = String(value).slice(0, 10).split('-');
      return d ? `${d}/${m}/${y}` : String(value);
    };
    const when = (value) => value ? new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : '—';
    const mark = WATERMARK[order.status];
    const lines = order.lines || [];
    return `<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>Đơn hàng ${esc(order.code)}</title><style>
      @page { size: A4; margin: 12mm; }
      * { box-sizing: border-box; }
      body { margin: 0; color: #111827; font: 12px/1.45 'Be Vietnam Pro', 'Segoe UI', Arial, sans-serif; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
      .sheet { position: relative; }
      .mark { position: fixed; top: 42%; left: 50%; transform: translate(-50%, -50%) rotate(-24deg); color: rgba(220, 38, 38, .13); font-size: 64px; font-weight: 800; letter-spacing: 6px; white-space: nowrap; pointer-events: none; }
      header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; padding-bottom: 10px; border-bottom: 2px solid #111827; }
      .brand { font-size: 13px; font-weight: 700; letter-spacing: .02em; }
      .brand small { display: block; color: #6b7280; font-weight: 400; }
      h1 { margin: 6px 0 2px; font-size: 20px; letter-spacing: .02em; }
      .status { display: inline-block; margin-top: 2px; padding: 2px 8px; border: 1px solid #9ca3af; border-radius: 999px; font-size: 11px; font-weight: 600; }
      .code { text-align: center; }
      .code svg { display: block; height: 54px; width: auto; }
      .code strong { display: block; margin-top: 2px; font: 600 13px/1 ui-monospace, Menlo, Consolas, monospace; letter-spacing: .12em; }
      .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 12px 0; }
      .box { padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; }
      .box h2 { margin: 0 0 6px; color: #374151; font-size: 11px; letter-spacing: .06em; text-transform: uppercase; }
      .box p { margin: 2px 0; }
      .box b { display: inline-block; min-width: 92px; color: #4b5563; font-weight: 500; }
      table { width: 100%; border-collapse: collapse; }
      th, td { padding: 6px 6px; border-bottom: 1px solid #e5e7eb; vertical-align: top; }
      th { background: #f3f4f6; color: #374151; font-size: 11px; text-align: left; }
      .num { text-align: right; white-space: nowrap; }
      tbody tr { break-inside: avoid; }
      td small { display: block; color: #6b7280; }
      .totals { display: flex; justify-content: flex-end; margin-top: 10px; }
      .totals table { width: 300px; }
      .totals td { border: 0; padding: 3px 6px; }
      .totals tr.pay td { padding-top: 6px; border-top: 2px solid #111827; font-size: 14px; font-weight: 700; }
      .note { margin-top: 10px; padding: 8px 10px; border-left: 3px solid #9ca3af; background: #f9fafb; }
      .sign { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-top: 28px; text-align: center; break-inside: avoid; }
      .sign p { margin: 0; font-weight: 600; }
      .sign small { color: #6b7280; }
      .sign div { height: 70px; }
      footer { margin-top: 18px; color: #6b7280; font-size: 10px; text-align: right; }
    </style></head><body><div class="sheet">
      ${mark ? `<div class="mark">${esc(mark)}</div>` : ''}
      <header>
        <div><div class="brand">KhoFlow<small>Phiếu đơn hàng bán</small></div>
          <h1>ĐƠN HÀNG ${esc(order.code)}</h1>
          <span class="status">${esc(order.statusLabel || order.status)}</span></div>
        <div class="code">${barcodeSvg(order.code)}<strong>${esc(order.code)}</strong></div>
      </header>
      <div class="grid">
        <section class="box"><h2>Đại lý</h2>
          <p><b>Tên đại lý</b>${esc(order.customerName || '—')}</p>
          <p><b>Mã đại lý</b>${esc(order.customerCode || '—')}</p>
          <p><b>Mã số thuế</b>${esc(order.customerTaxCode || '—')}</p>
          <p><b>Điện thoại</b>${esc(order.customerPhone || '—')}</p>
          <p><b>Khu vực</b>${esc(order.region || '—')}</p></section>
        <section class="box"><h2>Giao hàng</h2>
          <p><b>Điểm giao</b>${esc(order.deliveryAddress || '—')}</p>
          <p><b>Ngày giao</b>${esc(day(order.desiredDate))}</p>
          <p><b>Ngày lập</b>${esc(when(order.createdAt))}</p>
          <p><b>NV phụ trách</b>${esc(order.salesRepName || '—')}</p></section>
      </div>
      <table><thead><tr><th>#</th><th>Sản phẩm</th><th class="num">Số lượng</th><th class="num">Đơn giá</th><th class="num">Thành tiền</th><th class="num">Chiết khấu</th><th class="num">Phải thu</th></tr></thead>
        <tbody>${lines.map((l, i) => `<tr><td>${i + 1}</td><td>${esc(l.name)}<small>${esc(l.sku)}</small></td>
          <td class="num">${esc(qty(l.quantity))} ${esc(l.unitName)}</td><td class="num">${esc(money(l.unitPrice))}</td>
          <td class="num">${esc(money(l.subtotal))}</td><td class="num">${Number(l.discount) > 0 ? '−' + esc(money(l.discount)) : '—'}</td>
          <td class="num">${esc(money(l.payable))}</td></tr>`).join('')}</tbody></table>
      <div class="totals"><table>
        <tr><td>Tiền hàng</td><td class="num">${esc(money(order.subtotal))}</td></tr>
        <tr><td>Chiết khấu</td><td class="num">${Number(order.discount) > 0 ? '−' : ''}${esc(money(order.discount))}</td></tr>
        <tr class="pay"><td>Tổng phải thu</td><td class="num">${esc(money(order.payable))}</td></tr></table></div>
      ${order.note ? `<p class="note"><b>Ghi chú:</b> ${esc(order.note)}</p>` : ''}
      <div class="sign"><section><p>Đại lý xác nhận</p><small>(Ký, ghi rõ họ tên)</small><div></div></section>
        <section><p>Nhân viên kinh doanh</p><small>(Ký, ghi rõ họ tên)</small><div></div></section></div>
      <footer>In lúc ${esc(new Date().toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }))}${typeof currentUser !== 'undefined' && currentUser.fullName ? ' · ' + esc(currentUser.fullName) : ''} · ${lines.length} dòng hàng</footer>
    </div></body></html>`;
  }

  /** Mở hộp thoại in của trình duyệt cho đơn hàng (chọn "Lưu dưới dạng PDF" để xuất PDF). */
  function printOrder(order) {
    document.getElementById('orderPrintFrame')?.remove();
    const frame = document.createElement('iframe');
    frame.id = 'orderPrintFrame';
    frame.title = `In đơn hàng ${order.code}`;
    frame.setAttribute('aria-hidden', 'true');
    frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden';
    document.body.append(frame);
    const doc = frame.contentDocument;
    doc.open();
    doc.write(orderPrintHtml(order));
    doc.close();
    const run = () => {
      frame.contentWindow.focus();
      frame.contentWindow.print();
    };
    // Chờ font/ảnh trong khung in tải xong để bản in không bị nhảy bố cục.
    if (doc.fonts && doc.fonts.ready) doc.fonts.ready.then(() => setTimeout(run, 50));
    else setTimeout(run, 150);
  }

  window.orderPrint = { code128, barcodeSvg, orderPrintHtml, printOrder };
})();
