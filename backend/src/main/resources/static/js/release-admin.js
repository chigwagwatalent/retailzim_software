(function () {
  'use strict';

  var activeModal = null;
  var lastModalTrigger = null;

  function closeModal(modal) {
    var target = modal || activeModal;
    if (!target) return;
    target.classList.remove('is-open');
    target.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('release-modal-open');
    activeModal = null;
    if (lastModalTrigger) lastModalTrigger.focus();
  }

  function openModal(id, trigger) {
    var modal = document.getElementById(id);
    if (!modal) return;
    if (activeModal && activeModal !== modal) closeModal(activeModal);
    activeModal = modal;
    lastModalTrigger = trigger || null;
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('release-modal-open');
    window.setTimeout(function () {
      var focusTarget = modal.querySelector('input:not([type="hidden"]), select, textarea, button');
      if (focusTarget) focusTarget.focus();
    }, 50);
  }

  document.querySelectorAll('[data-release-modal-open], [data-release-edit-open]').forEach(function (trigger) {
    trigger.addEventListener('click', function () {
      openModal(trigger.getAttribute('data-modal-target'), trigger);
    });
  });
  document.querySelectorAll('[data-release-modal-close]').forEach(function (button) {
    button.addEventListener('click', function () { closeModal(button.closest('[data-release-modal]')); });
  });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && activeModal) closeModal(activeModal);
  });

  var serverOpenedModal = document.querySelector('[data-release-modal].is-open');
  if (serverOpenedModal) {
    activeModal = serverOpenedModal;
    document.body.classList.add('release-modal-open');
  }

  document.addEventListener('click', function (event) {
    document.querySelectorAll('.release-row-actions[open]').forEach(function (menu) {
      if (!menu.contains(event.target)) menu.removeAttribute('open');
    });
  });

  var form = document.querySelector('[data-release-upload-form]');
  if (form) {
    var fileInput = form.querySelector('[data-release-file]');
    var dropzone = form.querySelector('[data-release-dropzone]');
    var fileName = form.querySelector('[data-release-file-name]');
    var packageType = form.querySelector('[data-release-package-type]');
    var requirements = form.querySelector('[data-release-requirements]');
    var submit = form.querySelector('[data-release-submit]');
    var published = form.querySelector('input[name="published"]');
    var latest = form.querySelector('input[name="latest"]');

    function selectedPlatform() {
      var selected = form.querySelector('input[name="platform"]:checked');
      return selected ? selected.value : 'WINDOWS';
    }

    function configurePlatform() {
      var android = selectedPlatform() === 'ANDROID';
      packageType.innerHTML = android
        ? '<option value="APK">Android package (.apk)</option>'
        : '<option value="INSTALLER">Installer (.exe or .msi)</option><option value="PORTABLE_ZIP">Portable ZIP (.zip)</option>';
      fileInput.accept = android ? '.apk' : '.exe,.msi,.zip';
      requirements.value = android ? 'Android 8.0 or newer, internet connection' : 'Windows 10/11, 4 GB RAM';
      dropzone.classList.toggle('android', android);
      fileInput.value = '';
      showFile();
    }

    function showFile() {
      var file = fileInput.files && fileInput.files[0];
      if (!file) {
        fileName.textContent = 'No file selected';
        dropzone.classList.remove('has-file');
        return;
      }
      var size = file.size >= 1048576
        ? (file.size / 1048576).toFixed(1) + ' MB'
        : Math.max(1, Math.ceil(file.size / 1024)) + ' KB';
      fileName.textContent = file.name + ' · ' + size;
      dropzone.classList.add('has-file');
    }

    form.querySelectorAll('input[name="platform"]').forEach(function (input) {
      input.addEventListener('change', configurePlatform);
    });
    fileInput.addEventListener('change', showFile);
    ['dragenter', 'dragover'].forEach(function (name) {
      dropzone.addEventListener(name, function (event) {
        event.preventDefault();
        dropzone.classList.add('is-dragging');
      });
    });
    ['dragleave', 'drop'].forEach(function (name) {
      dropzone.addEventListener(name, function () { dropzone.classList.remove('is-dragging'); });
    });
    dropzone.addEventListener('drop', function (event) {
      event.preventDefault();
      if (event.dataTransfer.files.length) {
        fileInput.files = event.dataTransfer.files;
        showFile();
      }
    });
    latest.addEventListener('change', function () {
      if (latest.checked) published.checked = true;
    });
    published.addEventListener('change', function () {
      if (!published.checked) latest.checked = false;
    });
    form.addEventListener('submit', function () {
      submit.disabled = true;
      submit.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin"></i>Validating and uploading…';
    });
    configurePlatform();
  }

  var tableBody = document.querySelector('[data-release-table-body]');
  if (!tableBody) return;

  var rows = Array.prototype.slice.call(tableBody.querySelectorAll('[data-release-table-row]'));
  var search = document.querySelector('[data-release-search]');
  var platformFilter = document.querySelector('[data-release-platform-filter]');
  var statusFilter = document.querySelector('[data-release-status-filter]');
  var pageSummary = document.querySelector('[data-release-page-summary]');
  var pageNavigation = document.querySelector('[data-release-pages]');
  var filterEmpty = document.querySelector('[data-release-filter-empty]');
  var pageSize = parseInt(tableBody.getAttribute('data-page-size'), 10) || 8;
  var currentPage = 1;

  function pageButton(label, page, disabled, active) {
    var button = document.createElement('button');
    button.type = 'button';
    button.textContent = label;
    button.disabled = disabled;
    button.classList.toggle('is-active', active);
    if (active) button.setAttribute('aria-current', 'page');
    button.addEventListener('click', function () {
      currentPage = page;
      renderTable();
    });
    return button;
  }

  function filteredRows() {
    var query = search.value.trim().toLowerCase();
    var platform = platformFilter.value;
    var status = statusFilter.value;
    return rows.filter(function (row) {
      return (!query || row.getAttribute('data-search').indexOf(query) !== -1)
        && (!platform || row.getAttribute('data-platform') === platform)
        && (!status || row.getAttribute('data-status') === status);
    });
  }

  function renderTable() {
    var matches = filteredRows();
    var totalPages = Math.max(1, Math.ceil(matches.length / pageSize));
    if (currentPage > totalPages) currentPage = totalPages;
    var start = (currentPage - 1) * pageSize;
    var end = Math.min(start + pageSize, matches.length);
    var visible = new Set(matches.slice(start, end));

    rows.forEach(function (row) { row.hidden = !visible.has(row); });
    filterEmpty.hidden = matches.length !== 0;
    pageSummary.textContent = matches.length
      ? 'Showing ' + (start + 1) + '–' + end + ' of ' + matches.length + ' releases'
      : 'No releases match these filters';

    pageNavigation.innerHTML = '';
    pageNavigation.appendChild(pageButton('Previous', Math.max(1, currentPage - 1), currentPage === 1, false));
    for (var page = 1; page <= totalPages; page += 1) {
      if (totalPages > 7 && page > 2 && page < totalPages - 1 && Math.abs(page - currentPage) > 1) continue;
      pageNavigation.appendChild(pageButton(String(page), page, false, page === currentPage));
    }
    pageNavigation.appendChild(pageButton('Next', Math.min(totalPages, currentPage + 1), currentPage === totalPages, false));
  }

  [search, platformFilter, statusFilter].forEach(function (control) {
    control.addEventListener(control === search ? 'input' : 'change', function () {
      currentPage = 1;
      renderTable();
    });
  });
  renderTable();
})();
