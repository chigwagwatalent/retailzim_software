document.addEventListener('click', event => {
  const opener = event.target.closest('[data-settings-open]');
  if (opener) {
    const dialog = document.getElementById(opener.dataset.settingsOpen);
    if (dialog instanceof HTMLDialogElement && !dialog.open) dialog.showModal();
  }
  const closer = event.target.closest('[data-settings-close]');
  if (closer) closer.closest('dialog')?.close();
});
