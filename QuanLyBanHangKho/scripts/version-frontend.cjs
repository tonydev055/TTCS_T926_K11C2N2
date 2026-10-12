const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');

const webRoot = path.join(__dirname, '../web');
const indexPath = path.join(webRoot, 'index.html');
const original = fs.readFileSync(indexPath, 'utf8');
const updated = original.replace(/(assets\/shared\/[^"?]+\.(?:js|css))(?:\?v=[^"\s]+)?/g, (_, asset) => {
  const version = createHash('sha256').update(fs.readFileSync(path.join(webRoot, asset))).digest('hex').slice(0, 12);
  return `${asset}?v=${version}`;
});

if (process.argv.includes('--check')) {
  if (updated !== original) {
    process.stderr.write('Frontend asset versions are stale. Run node QuanLyBanHangKho/scripts/version-frontend.cjs\n');
    process.exitCode = 1;
  }
} else if (updated !== original) {
  fs.writeFileSync(indexPath, updated);
}
