const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function setup(failAuth = false) {
  const submit = {}, retry = {}, error = {};
  const links = ['xac-thuc/index.html', 'danh-muc/index.html'].map(url => ({ getAttribute: () => url }));
  const context = vm.createContext({
    window: {}, loginForm: { querySelector: () => submit }, loginError: error,
    document: { getElementById: id => id === 'retryFrontend' ? retry : {}, querySelectorAll: () => links,
      head: { append() {} }, createElement: () => ({}) },
    DOMParser: class { parseFromString() { return { querySelectorAll: () => [] }; } },
    sessionStorage: { getItem: () => null }, AbortSignal, URLSearchParams,
    fetch: async url => ({ ok: url !== 'danh-muc/index.html' && !(failAuth && url.startsWith('xac-thuc/')),
      status: 503, text: async () => '', json: async () => ({}) })
  });
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../../web/assets/shared/feature-loader.js'), 'utf8'), context);
  return { context, submit, retry, error, recover() { failAuth = false; } };
}
test('optional module failure does not block login', async () => {
  const app = setup();
  assert.equal(await app.context.window.frontendReady, true);
  assert.equal(app.submit.disabled, false);
  assert.equal(vm.runInContext("failedFeatures.has('danh-muc')", app.context), true);
});
test('authentication load can be retried after failure', async () => {
  const app = setup(true);
  assert.equal(await app.context.window.frontendReady, false);
  assert.equal(app.retry.hidden, false);
  app.recover();
  app.retry.onclick();
  assert.equal(await app.context.window.frontendReady, true);
  assert.equal(app.submit.disabled, false);
});
