const failedFeatures = new Set();
const loadedFeatures = new Set();
async function loadFeature(url) {
  if (loadedFeatures.has(url)) return;
  const response = await fetch(url, { cache: 'no-store', signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(`Không tải được giao diện (${response.status}).`);
  const fragment = new DOMParser().parseFromString(await response.text(), 'text/html');
  for (const source of fragment.querySelectorAll('style,script')) {
    const element = document.createElement(source.tagName.toLowerCase());
    element.textContent = source.textContent;
    if (source.tagName === 'SCRIPT') element.textContent += `\n//# sourceURL=${url}`;
    document.head.append(element);
  }
  loadedFeatures.add(url);
  failedFeatures.delete(url.split('/')[0]);
}
async function startFrontend() {
  const submit = loginForm.querySelector('button[type="submit"]');
  submit.disabled = true;
  const retry = document.getElementById('retryFrontend');
  retry.hidden = true;
  loginError.textContent = '';
  try {
    const links = [...document.querySelectorAll('[data-feature]')];
    const auth = links.find((link) => link.getAttribute('href').startsWith('xac-thuc/'));
    await loadFeature(auth.getAttribute('href'));
    await Promise.allSettled(links.filter((link) => link !== auth).map(async (link) => {
      const url = link.getAttribute('href');
      try { await loadFeature(url); }
      catch (error) { failedFeatures.add(url.split('/')[0]); throw error; }
    }));
    if (
      sessionStorage.getItem('khoFlowSession') &&
      !new URLSearchParams(location.search).get('resetToken')
    ) {
      const response = await fetch('api/auth/me', { signal: AbortSignal.timeout(15000) });
      if (response.ok) openApp(await response.json());
      else sessionStorage.removeItem('khoFlowSession');
    }
    submit.disabled = false;
    return true;
  } catch (error) {
    loginError.textContent = 'Không tải được ứng dụng. Kiểm tra kết nối rồi thử lại.';
    retry.hidden = false;
    return false;
  }
}
document.getElementById('retryFrontend').onclick = () => { window.frontendReady = startFrontend(); };
window.frontendReady = startFrontend();
fetch('api/auth/mail-config', { signal: AbortSignal.timeout(10000) }).then(async (response) => {
  if (response.ok) document.getElementById('openDevMailbox').hidden = !(await response.json()).developmentMailbox;
}).catch(() => {});
