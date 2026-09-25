/* Keep shared workspace footers visible, with measured space for wrapped text. */
(() => {
  const footer = document.querySelector('.platform-shell .platform-footer, .app-shell .tenant-footer');
  const main = footer?.closest('main');
  if (!footer || !main) return;
  footer.classList.add('workspace-fixed-footer');
  main.classList.add('workspace-footer-main');
  document.body.classList.add('has-workspace-footer');
  // Fixed positioning must not inherit a transformed/scrolling page container.
  document.body.append(footer);
  let frame;
  const measure = () => {
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      const left = Math.max(0, Math.min(main.getBoundingClientRect().left, document.documentElement.clientWidth - 1));
      footer.style.setProperty('--footer-left', `${left}px`);
      const height = footer.getBoundingClientRect().height;
      document.documentElement.style.setProperty('--workspace-footer-height', `${height}px`);
    });
  };
  const observer = new ResizeObserver(measure);
  observer.observe(main); observer.observe(footer);
  window.addEventListener('resize', measure);
  window.visualViewport?.addEventListener('resize', measure);
  measure();
})();
