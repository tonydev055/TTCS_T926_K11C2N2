const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '../..');
const page = fs.readFileSync(path.join(root, 'web/giao-hang/index.html'), 'utf8');
const index = fs.readFileSync(path.join(root, 'web/index.html'), 'utf8');
const service = fs.readFileSync(path.join(root, 'src/java/service/XacNhanGiaoHangService.java'), 'utf8');
const servlet = fs.readFileSync(path.join(root, 'src/java/controller/XacNhanGiaoHangServlet.java'), 'utf8');
const permissions = fs.readFileSync(path.join(root, 'src/java/security/PhanQuyen.java'), 'utf8');
const migration = fs.readFileSync(path.join(root, 'database/sprint6-delivery-confirmation.sql'), 'utf8');

test('S6-08 module is loaded and protected by warehouse delivery permission', () => {
  assert.match(index, /giao-hang\/index\.html\?v=s6-08/);
  assert.match(page, /sprint5Features\['Xác nhận giao hàng'\]/);
  assert.match(page, /permission:'shipments\.write'/);
  assert.match(permissions, /under\(path, "\/api\/delivery-confirmations"\)\) return "shipments\.write"/);
});

test('successful delivery requires recipient, time and signed photo upload', () => {
  assert.match(page, /type="file" accept="image\/jpeg,image\/png"/);
  assert.match(page, /fd\.set\('proofImage',proof\.files\[0\]\)/);
  assert.match(service, /Vui lòng tải ảnh chứng từ giao hàng đã ký/);
  assert.match(service, /UPDATE orders SET status='DELIVERED'/);
  assert.match(servlet, /@MultipartConfig/);
});

test('failed delivery records reason and posts a return movement transactionally', () => {
  assert.match(page, /Giao thất bại/);
  assert.match(service, /failure_reason/);
  assert.match(service, /returnGoods\(c, orderId/);
  assert.match(service, /"DELIVERY_RETURN"/);
  assert.match(service, /c\.commit\(\)/);
  assert.match(migration, /returned_to_warehouse BOOLEAN NOT NULL DEFAULT FALSE/);
  assert.doesNotMatch(service, /INSERT INTO invoices|\/api\/invoices/);
});
