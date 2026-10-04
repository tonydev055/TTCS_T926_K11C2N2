// Fetch all features before executing any of them, then restore the session.
window.frontendReady = (async () => {
  const submit = loginForm.querySelector('button[type="submit"]');
  submit.disabled = true;
  try {
    const mailConfig = await fetch('api/auth/mail-config', { cache: 'no-store' });
    if (mailConfig.ok) {
      const config = await mailConfig.json();
      document.getElementById('openDevMailbox').hidden = !config.developmentMailbox;
    }
    const pages = await Promise.all(
      [...document.querySelectorAll('[data-feature]')].map(async (link) => {
        const response = await fetch(link.getAttribute('href'), { cache: 'no-store' });
        if (!response.ok) throw new Error(`Không tải được giao diện (${response.status}).`);
        return { url: link.getAttribute('href'), html: await response.text() };
      })
    );
    for (const page of pages) {
      const fragment = new DOMParser().parseFromString(page.html, 'text/html');
      for (const source of fragment.querySelectorAll('style,script')) {
        const element = document.createElement(source.tagName.toLowerCase());
        element.textContent = source.textContent;
        if (source.tagName === 'SCRIPT') element.textContent += `\n//# sourceURL=${page.url}`;
        document.head.append(element);
      }
    }
    if (
      sessionStorage.getItem('khoFlowSession') &&
      !new URLSearchParams(location.search).get('resetToken')
    ) {
      const response = await fetch('api/auth/me');
      if (response.ok) openApp(await response.json());
      else sessionStorage.removeItem('khoFlowSession');
    }
    submit.disabled = false;
    return true;
  } catch (error) {
    loginError.textContent = 'Không tải được ứng dụng. Vui lòng tải lại trang. ' + error.message;
    return false;
  }
})();
