(function () {
  'use strict';

  function request(url, data) {
    const body = new URLSearchParams(data);
    return fetch(url, {
      method: 'POST',
      headers: { 'Accept': 'application/json' },
      body: body
    }).then(function (response) {
      return response.json().then(function (payload) {
        if (!response.ok) throw new Error(payload.message || 'Request failed');
        return payload;
      });
    });
  }

  const body = document.body;
  const pageName = body.getAttribute('data-page') || 'home';
  request('track', { page: pageName, section: 'load' }).catch(function () {});

  const navToggle = document.querySelector('.nav-toggle');
  const navMenu = document.querySelector('.nav-menu');
  if (navToggle && navMenu) {
    navToggle.addEventListener('click', function () {
      const open = navMenu.classList.toggle('is-open');
      navToggle.setAttribute('aria-expanded', String(open));
      navToggle.querySelector('i').className = open ? 'fa-solid fa-xmark' : 'fa-solid fa-bars';
    });
    navMenu.querySelectorAll('a').forEach(function (link) {
      link.addEventListener('click', function () {
        navMenu.classList.remove('is-open');
        navToggle.setAttribute('aria-expanded', 'false');
      });
    });
  }

  const tracked = new Set();
  if ('IntersectionObserver' in window) {
    const sectionObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        const section = entry.target.getAttribute('data-track-section') || entry.target.id;
        if (!section || tracked.has(section)) return;
        tracked.add(section);
        request('track', { page: pageName, section: section }).catch(function () {});
      });
    }, { threshold: 0.25 });
    document.querySelectorAll('[data-track-section], main section[id]').forEach(function (node) {
      sectionObserver.observe(node);
    });

    const revealObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) entry.target.classList.add('is-visible');
      });
    }, { threshold: 0.08 });
    document.querySelectorAll('.interactive-card, .suite-card, .workflow-step, .plan-card, .resource-card').forEach(function (card) {
      card.classList.add('reveal-card');
      revealObserver.observe(card);
    });
  }

  document.querySelectorAll('[data-engage-post]').forEach(function (button) {
    button.addEventListener('click', function () {
      if (button.classList.contains('is-sent')) return;
      const post = button.closest('[data-community-post]');
      const postId = button.getAttribute('data-engage-post');
      button.classList.add('is-sent');
      button.querySelector('i').className = 'fa-solid fa-thumbs-up';
      button.querySelector('span').textContent = 'Liked';
      const summary = post && post.querySelector('[data-like-summary]');
      if (summary) {
        const current = parseInt(summary.textContent.replace(/\D/g, ''), 10) || 0;
        summary.innerHTML = '<i class="fa-solid fa-thumbs-up"></i> ' + (current + 1);
      }
      if (!postId || postId === '0') return;
      request('community-engage', { post_id: postId, action: 'like' }).catch(function () {
        button.classList.remove('is-sent');
        button.querySelector('i').className = 'fa-regular fa-thumbs-up';
        button.querySelector('span').textContent = 'Like';
      });
    });
  });

  document.querySelectorAll('[data-comment-toggle]').forEach(function (button) {
    button.addEventListener('click', function () {
      const form = button.closest('[data-community-post]').querySelector('[data-comment-form]');
      form.classList.toggle('is-open');
      if (form.classList.contains('is-open')) form.querySelector('input[name="comment"]').focus();
    });
  });

  function initials(name) {
    return name.trim().split(/\s+/).slice(0, 2).map(function (part) { return part.charAt(0).toUpperCase(); }).join('') || 'RZ';
  }

  function appendComment(form, comment) {
    const thread = form.closest('[data-community-post]').querySelector('[data-comment-thread]');
    const wrapper = document.createElement('div');
    wrapper.className = 'community-comment just-added';
    const avatar = document.createElement('div');
    avatar.className = 'community-avatar small-avatar';
    avatar.textContent = initials(comment.name);
    const bubble = document.createElement('div');
    const author = document.createElement('b');
    author.textContent = comment.name;
    const text = document.createElement('p');
    text.textContent = comment.comment;
    const time = document.createElement('small');
    time.textContent = 'Just now';
    bubble.append(author, text, time);
    wrapper.append(avatar, bubble);
    thread.appendChild(wrapper);
    const summary = form.closest('[data-community-post]').querySelector('[data-comment-summary]');
    if (summary) {
      const current = parseInt(summary.textContent, 10) || 0;
      summary.textContent = (current + 1) + ' comments';
    }
  }

  document.querySelectorAll('[data-comment-form]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      const message = form.querySelector('[data-comment-message]');
      const name = form.querySelector('input[name="name"]').value.trim();
      const comment = form.querySelector('input[name="comment"]').value.trim();
      const postId = form.querySelector('input[name="post_id"]').value;
      if (!name || !comment) return;
      const submit = form.querySelector('button[type="submit"]');
      submit.disabled = true;
      message.textContent = 'Posting your comment...';

      if (form.getAttribute('data-demo') === 'true') {
        appendComment(form, { name: name, comment: comment });
        form.querySelector('input[name="comment"]').value = '';
        message.textContent = 'Comment added to this preview discussion.';
        submit.disabled = false;
        return;
      }

      request('community-comment', { post_id: postId, name: name, comment: comment }).then(function (payload) {
        appendComment(form, payload.comment);
        form.querySelector('input[name="comment"]').value = '';
        message.textContent = 'Comment posted.';
      }).catch(function (error) {
        message.textContent = error.message || 'Comment could not be posted.';
      }).finally(function () {
        submit.disabled = false;
      });
    });
  });

  document.querySelectorAll('[data-share-post]').forEach(function (button) {
    button.addEventListener('click', function () {
      const text = window.location.href.split('#')[0] + '#community-feed';
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function () {
          button.querySelector('span').textContent = 'Link copied';
          setTimeout(function () { button.querySelector('span').textContent = 'Share'; }, 1800);
        }).catch(function () {});
      }
    });
  });

  const composerExpand = document.querySelector('[data-composer-expand]');
  const composerFields = document.querySelector('[data-composer-fields]');
  if (composerExpand && composerFields) {
    composerExpand.addEventListener('click', function () {
      composerFields.classList.toggle('is-open');
      if (composerFields.classList.contains('is-open')) composerFields.querySelector('textarea').focus();
    });
  }

  const feed = document.querySelector('.community-feed-rich');
  const emptyState = document.querySelector('[data-feed-empty]');
  function filterFeed(value) {
    if (!feed) return;
    let visible = 0;
    feed.querySelectorAll('[data-community-post]').forEach(function (post) {
      const match = value === 'all' || post.getAttribute('data-category') === value || post.getAttribute('data-status') === value;
      post.hidden = !match;
      if (match) visible += 1;
    });
    if (emptyState) emptyState.hidden = visible > 0;
  }
  document.querySelectorAll('[data-feed-filter]').forEach(function (button) {
    button.addEventListener('click', function () {
      document.querySelectorAll('[data-feed-filter]').forEach(function (item) { item.classList.remove('active'); });
      button.classList.add('active');
      filterFeed(button.getAttribute('data-feed-filter'));
    });
  });
  document.querySelectorAll('[data-feed-sort]').forEach(function (button) {
    button.addEventListener('click', function () {
      document.querySelectorAll('[data-feed-sort]').forEach(function (item) { item.classList.remove('active'); });
      button.classList.add('active');
      if (button.getAttribute('data-feed-sort') === 'popular' && feed) {
        Array.from(feed.children).sort(function (a, b) {
          return Number(b.getAttribute('data-likes')) - Number(a.getAttribute('data-likes'));
        }).forEach(function (post) { feed.appendChild(post); });
      }
    });
  });

  document.querySelectorAll('[data-checkout-demo]').forEach(function (demo) {
    const status = demo.querySelector('.checkout-status');
    demo.querySelectorAll('.checkout-methods button').forEach(function (button) {
      button.addEventListener('click', function () {
        demo.querySelectorAll('.checkout-methods button').forEach(function (item) { item.classList.remove('active'); });
        button.classList.add('active');
        status.textContent = button.textContent + ' selected';
      });
    });
    demo.querySelector('.checkout-pay').addEventListener('click', function () {
      const selected = demo.querySelector('.checkout-methods button.active');
      status.textContent = 'Demo payment recorded with ' + selected.textContent + '.';
      status.classList.add('success');
    });
  });

  document.querySelectorAll('[data-release-tab]').forEach(function (button) {
    button.addEventListener('click', function () {
      const platform = button.getAttribute('data-release-tab');
      document.querySelectorAll('[data-release-tab]').forEach(function (tab) {
        const selected = tab === button;
        tab.classList.toggle('active', selected);
        tab.setAttribute('aria-selected', String(selected));
      });
      document.querySelectorAll('[data-release-panel]').forEach(function (panel) {
        const selected = panel.getAttribute('data-release-panel') === platform;
        panel.hidden = !selected;
        panel.classList.toggle('active', selected);
      });
    });
  });

  document.querySelectorAll('[data-copy-checksum]').forEach(function (button) {
    button.addEventListener('click', function () {
      const checksum = button.getAttribute('data-copy-checksum');
      if (!navigator.clipboard || !navigator.clipboard.writeText) return;
      navigator.clipboard.writeText(checksum).then(function () {
        button.classList.add('copied');
        button.innerHTML = '<i class="fa-solid fa-check"></i>';
        setTimeout(function () {
          button.classList.remove('copied');
          button.innerHTML = '<i class="fa-regular fa-copy"></i>';
        }, 1600);
      }).catch(function () {});
    });
  });
})();
