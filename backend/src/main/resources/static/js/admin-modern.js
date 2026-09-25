document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.platform-shell main table').forEach(table => {
    const existing = table.closest('.table-scroll,.table-wrap,.table-responsive,.admin-table-scroll');
    const region = existing || document.createElement('div');
    region.classList.add('admin-table-scroll');
    region.tabIndex = 0;
    region.setAttribute('role','region');
    region.setAttribute('aria-label', 'Data table — scroll horizontally for more columns');
    if (!existing) { table.before(region); region.append(table); }
  });
});
