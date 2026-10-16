const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '../..');
const page = fs.readFileSync(path.join(root, 'web/hoa-don/index.html'), 'utf8');
const index = fs.readFileSync(path.join(root, 'web/index.html'), 'utf8');
const service = fs.readFileSync(path.join(root, 'src/java/service/HoaDonService.java'), 'utf8');
const servlet = fs.readFileSync(path.join(root, 'src/java/controller/HoaDonServlet.java'), 'utf8');
const migration = fs.readFileSync(path.join(root, 'database/sprint7-sales-invoices.sql'), 'utf8');

test('S7-01 is loaded and restricted to invoice permission', () => {
  assert.match(index, /hoa-don\/index\.html\?v=s7-01/);
  assert.match(page, /permission:'invoices\.write'/);
  assert.match(servlet, /@WebServlet\("\/api\/invoices\/\*"\)/);
});

test('invoice uses actual delivered quantity and only successful deliveries', () => {
  assert.match(service, /dc\.outcome='SUCCESS'/);
  assert.match(service, /ol\.delivered_base_quantity\/ol\.conversion_factor/);
  assert.match(service, /Chỉ được gộp phiếu giao của cùng một đại lý/);
  assert.match(page, /số lượng thực giao/i);
});

test('issued invoices are immutable and numbers roll back with the transaction', () => {
  assert.match(migration, /invoice_number_counters/);
  assert.match(service, /UPDATE invoice_number_counters SET last_number=last_number\+1/);
  assert.match(service, /c\.rollback\(\)/);
  assert.match(migration, /BEFORE UPDATE OR DELETE ON invoices/);
  assert.doesNotMatch(servlet, /doDelete/);
});

test('adjustment and print/PDF flows are available', () => {
  assert.match(page, /Lập chứng từ điều chỉnh/);
  assert.match(page, /In \/ Lưu PDF/);
  assert.match(page, /window\.print\(\)/);
  assert.match(service, /INSERT INTO invoice_adjustments/);
});

