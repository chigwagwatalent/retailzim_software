(() => {
  const admin = !!document.querySelector('.platform-shell');
  const panels = [...document.querySelectorAll('.chat-panel[data-feed-url]')];
  if (!admin && !panels.length) return;
  const base = admin ? '/admin' : '/shop';
  const headers = { 'X-Requested-With': 'XMLHttpRequest' };
  const token = document.querySelector('meta[name="csrf-token"]')?.content;
  const header = document.querySelector('meta[name="csrf-header"]')?.content;
  if (token && header) headers[header] = token;
  const badge = (element, count) => {
    let node = element.querySelector('[data-live-count]');
    if (!node) {
      node = element.querySelector('small.nav-pill') || [...element.querySelectorAll(':scope > span')].find(span => /^\d+$/.test(span.textContent.trim()));
      if (!node || node.textContent.trim().match(/\D/)) {
        node = document.createElement('small'); element.append(node);
      }
      node.dataset.liveCount = ''; node.classList.add('live-count');
    }
    node.textContent = count > 99 ? '99+' : String(count);
    node.hidden = count === 0;
    // Legacy .has-badge span rules also target sidebar label spans.
    if (element.closest('.platform-sidebar')) element.classList.remove('has-badge');
    else element.classList.toggle('has-badge', count > 0);
  };
  const states = panels.map(panel => {
    const feed = panel.querySelector('.chat-feed');
    feed.setAttribute('role', 'log');
    const status = document.createElement('p');
    status.className = 'chat-connection'; status.setAttribute('role', 'status');
    feed.after(status);
    let busy = false, fingerprint = '', acknowledged = 0, pendingText, pendingId;
    const visible = () => {
      const bounds = panel.getBoundingClientRect();
      return !document.hidden && bounds.height > 0 && bounds.bottom > 0 && bounds.top < innerHeight
        && (!panel.classList.contains('support-widget') || panel.classList.contains('open'));
    };
    const refresh = async () => {
      if (busy || !visible()) return;
      busy = true;
      try {
        const response = await fetch(panel.dataset.feedUrl, { headers: { Accept: 'application/json' }, cache: 'no-store' });
        if (!response.ok || response.redirected) throw new Error('Session unavailable. Sign in again if reconnecting does not help.');
        const messages = await response.json();
        const next = JSON.stringify(messages);
        if (next !== fingerprint) {
          const bottom = feed.scrollHeight - feed.scrollTop - feed.clientHeight < 80 || !fingerprint;
          const nodes = messages.map(message => {
            const article = document.createElement('article');
            article.className = `chat-message ${message.senderType === 'PLATFORM' ? 'platform' : 'shop'} ${message.senderType === (admin ? 'PLATFORM' : 'SHOP') ? 'outgoing' : 'incoming'}`;
            const name = document.createElement('strong'); name.textContent = message.senderName;
            const body = document.createElement('p'); body.textContent = message.message;
            const time = document.createElement('small');
            const mine = message.senderType === (admin ? 'PLATFORM' : 'SHOP');
            time.textContent = new Date(message.createdAt).toLocaleString() + (mine ? (admin ? message.readByShop : message.readByPlatform) ? ' · Read' : ' · Sent' : '');
            article.append(name, body, time); return article;
          });
          feed.replaceChildren(...nodes); fingerprint = next;
          if (bottom) feed.scrollTop = feed.scrollHeight;
        }
        const through = Math.max(0, ...messages.map(m => m.id));
        if (visible() && through > acknowledged) {
          const tenant = panel.dataset.feedUrl.match(/\/(?:support|tenants)\/(\d+)\//)?.[1];
          const url = admin ? `/admin/support/${tenant}/read` : '/shop/support/read';
          const ack = await fetch(url, { method: 'POST', headers, body: new URLSearchParams({ through }) });
          if (ack.ok && !ack.redirected) acknowledged = through;
        }
      } catch (error) { status.textContent = 'Connection interrupted. Your unsent text is kept.'; }
      finally { busy = false; }
    };
    panel.querySelectorAll('input[name="message"],textarea[name="message"]').forEach(input => input.maxLength = 4000);
    panel.addEventListener('submit', async event => {
      const form = event.target.closest('[data-async-chat]');
      if (!form) return;
      event.preventDefault();
      const input = form.querySelector('[name="message"]');
      if (!input.value.trim()) { status.textContent = 'Write a message before sending.'; input.focus(); return; }
      if (!form.reportValidity()) return;
      const button = form.querySelector('[type="submit"]');
      if (button.disabled) return;
      button.disabled = true; status.textContent = 'Sending…';
      try {
        const body = new FormData(form);
        if (pendingText !== body.get('message')) { pendingText = body.get('message'); pendingId = crypto.randomUUID(); }
        body.set('requestId', pendingId);
        const tenant = panel.dataset.feedUrl.match(/\/(?:support|tenants)\/(\d+)\//)?.[1];
        const url = admin ? `/admin/support/${tenant}/messages` : '/shop/support/messages';
        const response = await fetch(url, { method: 'POST', headers, body });
        if (!response.ok || response.redirected) throw new Error('Send failed');
        await response.json();
        form.reset(); pendingText = pendingId = undefined; status.textContent = 'Message sent'; await refresh();
      } catch (error) { status.textContent = 'Delivery not confirmed. Text kept; check the conversation before retrying.'; }
      finally { button.disabled = false; }
    });
    panel.querySelectorAll('[data-chat-toggle]').forEach(button => button.addEventListener('click', () => setTimeout(refresh, 0)));
    new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) refresh(); }).observe(panel);
    return { refresh, status };
  });
  let stream, latest, unread, notificationCount, fallback;
  let audioContext, lastSound = 0;
  // Always enabled. A normal user gesture unlocks audio where autoplay is blocked.
  const unlockAudio = () => {
    try { audioContext ||= new (window.AudioContext || window.webkitAudioContext)(); audioContext.resume().catch(() => {}); } catch (_) {}
  };
  document.addEventListener('pointerdown', unlockAudio, { passive:true });
  document.addEventListener('keydown', unlockAudio);
  unlockAudio();
  const playNotification = () => {
    if (audioContext?.state !== 'running' || Date.now() - lastSound < 4000) return;
    lastSound = Date.now();
    const oscillator = audioContext.createOscillator(), gain = audioContext.createGain();
    oscillator.connect(gain); gain.connect(audioContext.destination);
    oscillator.frequency.setValueAtTime(660, audioContext.currentTime);
    oscillator.frequency.setValueAtTime(880, audioContext.currentTime + .12);
    gain.gain.setValueAtTime(.0001, audioContext.currentTime);
    gain.gain.exponentialRampToValueAtTime(.08, audioContext.currentTime + .02);
    gain.gain.exponentialRampToValueAtTime(.0001, audioContext.currentTime + .32);
    oscillator.start(); oscillator.stop(audioContext.currentTime + .34);
    oscillator.onended = () => { oscillator.disconnect(); gain.disconnect(); };
  };
  let inboxBusy = false;
  const refreshInbox = async () => {
    const threads = [...document.querySelectorAll('[data-support-tenant]')];
    if (!threads.length || inboxBusy) return;
    inboxBusy = true;
    try {
      const response = await fetch('/admin/support/inbox-status?ids=' + threads.map(t => t.dataset.supportTenant).join(','), { cache: 'no-store' });
      if (!response.ok || response.redirected) return;
      for (const item of await response.json()) {
        const thread = threads.find(t => t.dataset.supportTenant === String(item.tenant));
        if (!thread) continue;
        const preview = thread.querySelector('small');
        if (preview) preview.textContent = item.message;
        let count = thread.querySelector('em');
        if (!count) { count = document.createElement('em'); thread.append(count); }
        count.textContent = item.unread; count.hidden = !item.unread;
      }
    } finally { inboxBusy = false; }
  };
  const connect = () => {
    if (document.hidden || stream) return;
    stream = new EventSource(`${base}/live/events`);
    stream.addEventListener('support', event => {
      const data = JSON.parse(event.data);
      if (unread !== undefined && (data.unread > unread || data.notifications > notificationCount)) playNotification();
      notificationCount = data.notifications;
      document.querySelectorAll(admin ? 'a[title="Live support"],.platform-sidebar a[href="/admin/support"]' : '.support-fab').forEach(el => badge(el, data.unread));
      document.querySelectorAll('a[title="Notifications"]').forEach(el => badge(el, admin ? data.unread : data.notifications));
      if (latest !== data.latest || unread !== data.unread) { states.forEach(state => state.refresh()); refreshInbox().catch(() => {}); }
      latest = data.latest; unread = data.unread;
      states.forEach(state => { if (!state.status.textContent.startsWith('Delivery')) state.status.textContent = 'Live updates connected'; });
    });
    stream.onerror = () => states.forEach(state => state.status.textContent = 'Reconnecting… Messages remain saved.');
  };
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { stream?.close(); stream = null; }
    else { connect(); states.forEach(state => state.refresh()); }
  });
  // Also refresh read receipts; serial guards prevent overlapping requests.
  fallback = setInterval(() => states.forEach(state => state.refresh()), 15000);
  window.addEventListener('pagehide', () => { stream?.close(); clearInterval(fallback); });
  connect(); states.forEach(state => state.refresh());
})();
