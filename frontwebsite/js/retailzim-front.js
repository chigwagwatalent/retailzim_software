(function () {
  function post(url, data) {
    if (!window.fetch) return;
    const body = new URLSearchParams(data);
    fetch(url, {
      method: 'POST',
      headers: { 'Accept': 'application/json' },
      body
    }).catch(function () {});
  }

  post('track', { page: 'home', section: 'load' });

  const tracked = new Set();
  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        const section = entry.target.getAttribute('data-track-section') || entry.target.id;
        if (!section || tracked.has(section)) return;
        tracked.add(section);
        post('track', { page: 'home', section });
      });
    }, { threshold: 0.35 });

    document.querySelectorAll('[id], [data-track-section]').forEach(function (node) {
      observer.observe(node);
    });
  }

  document.querySelectorAll('[data-engage-post]').forEach(function (button) {
    button.addEventListener('click', function () {
      const postId = button.getAttribute('data-engage-post');
      if (!postId || postId === '0') return;
      post('community-engage', {
        post_id: postId,
        action: button.getAttribute('data-action') || 'like'
      });
      button.classList.add('is-sent');
    });
  });
})();
