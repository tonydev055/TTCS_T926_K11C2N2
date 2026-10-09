const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');

test('S3-02 has a dedicated read-only price-history screen', () => {
  const page = read('web/lich-su-gia/index.html');
  const routes = read('web/assets/shared/routes.js');
  const navigation = read('web/assets/shared/navigation.js');
  const index = read('web/index.html');

  assert.match(routes, /menu\.push\('Lịch sử thay đổi giá'\)/);
  assert.match(routes, /priceHistoryFeature\.render\(\)/);
  assert.match(navigation, /'Lịch sử thay đổi giá': 'lich-su-gia'/);
  assert.match(index, /href="lich-su-gia\/index\.html\?v=/);
  for (const label of ['Sản phẩm', 'Bảng giá', 'Giá cũ', 'Giá mới', 'Thay đổi', 'Người cập nhật', 'Hiệu lực từ'])
    assert.match(page, new RegExp(label));
  assert.match(page, /Chỉ đọc/);
  assert.match(page, /Chưa có giá<br>trước đó/);
  assert.doesNotMatch(page, /apiRequest\(`price-history[^`]*`,\s*\{\s*method:\s*['"](?:POST|PUT|DELETE)/);
});

test('product history action opens the dedicated screen with a selected product', () => {
  const catalog = read('web/danh-muc/index.html');
  assert.match(catalog, /priceHistorySelectedProductId\s*=\s*Number/);
  assert.match(catalog, /openView\('Lịch sử thay đổi giá'\)/);
});
