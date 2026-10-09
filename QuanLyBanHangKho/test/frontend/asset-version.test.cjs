const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

test('shared assets have content-based versions to prevent mixed frontend releases', () => {
  const result = spawnSync(process.execPath, [path.join(__dirname, '../../scripts/version-frontend.cjs'), '--check'], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
});
