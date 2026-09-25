/* Presentation only: preserve table nodes, form ownership and existing listeners. */
document.addEventListener('DOMContentLoaded', () => {
  const shell = document.querySelector('.app-shell:has(> .tenant-shell-sidebar)');
  if (!shell) return;
  const branchSelect = shell.querySelector('.shop-branch-switcher select[name="branchId"]');
  branchSelect?.addEventListener('change', () => {
    if (branchSelect.form) branchSelect.form.requestSubmit();
  });
  // Read-only page filters use the same scope choices as the navbar. Never add
  // an all-branches target to stock/cash write forms or branch-restricted users.
  if (branchSelect) shell.querySelectorAll('form[method="get"] select[name="branchId"]').forEach(select => {
    if (!select.querySelector('option[value="0"]')) {
      const all = new Option('All branches', '0');
      select.prepend(all);
      select.value = branchSelect.value;
    }
  });
  shell.querySelectorAll('main table').forEach((table) => {
    const existing = table.closest('.table-scroll, .table-wrap, .table-responsive, .shop-table-scroll');
    const region = existing || document.createElement('div');
    region.classList.add('shop-table-scroll');
    region.tabIndex = 0;
    region.setAttribute('role', 'region');
    const heading = table.closest('section, article')?.querySelector('h2, h3');
    region.setAttribute('aria-label', `${heading?.textContent.trim() || 'Data table'} — scroll horizontally for more columns`);
    if (!existing) {
      table.before(region);
      region.append(table);
    }
  });
});
