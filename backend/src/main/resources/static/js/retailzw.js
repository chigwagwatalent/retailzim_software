document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && location.hash.startsWith('#modal')) {
    history.pushState('', document.title, location.pathname + location.search);
  }
});

document.addEventListener('click', (event) => {
  const button = event.target.closest('.theme-toggle');
  if (!button) return;
  const current = document.documentElement.dataset.theme || 'light';
  const next = current === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  localStorage.setItem('retailzw-theme', next);
  button.querySelector('i')?.classList.toggle('fa-sun', next === 'dark');
  button.querySelector('i')?.classList.toggle('fa-moon', next !== 'dark');
});

document.addEventListener('click', (event) => {
  const openMenus = document.querySelectorAll('details.quick-links[open], details.account-menu[open]');
  openMenus.forEach((menu) => {
    if (!menu.contains(event.target)) {
      menu.removeAttribute('open');
    }
  });
});

document.addEventListener('toggle', (event) => {
  const menu = event.target;
  if (!menu.matches('details.quick-links, details.account-menu') || !menu.open) return;
  document.querySelectorAll('details.quick-links[open], details.account-menu[open]').forEach((other) => {
    if (other !== menu) {
      other.removeAttribute('open');
    }
  });
}, true);

document.querySelectorAll('.theme-toggle i').forEach((icon) => {
  const dark = document.documentElement.dataset.theme === 'dark';
  icon.classList.toggle('fa-sun', dark);
  icon.classList.toggle('fa-moon', !dark);
});

document.addEventListener('DOMContentLoaded', () => {
  setupBillingRenewalToast();
  setupSignupWizard();
  setupShiftCloseForms();
  setupGasTankWeightForms();
  setupGasReconciliationForms();
  openRequestedModal();
  enhanceForms();
});

function setupBillingRenewalToast() {
  const toast = document.querySelector('[data-billing-renewal-toast]');
  if (!toast) return;

  let dismissTimer;
  const dismiss = () => {
    window.clearTimeout(dismissTimer);
    toast.classList.remove('is-visible');
    window.setTimeout(() => toast.remove(), 260);
  };
  const pause = () => {
    window.clearTimeout(dismissTimer);
    toast.classList.add('is-paused');
  };
  const resume = () => {
    toast.classList.remove('is-paused');
    window.clearTimeout(dismissTimer);
    dismissTimer = window.setTimeout(dismiss, 18000);
  };

  toast.querySelector('[data-billing-toast-dismiss]')
    ?.addEventListener('click', dismiss);
  toast.addEventListener('mouseenter', pause);
  toast.addEventListener('mouseleave', resume);
  toast.addEventListener('focusin', pause);
  toast.addEventListener('focusout', (event) => {
    if (!toast.contains(event.relatedTarget)) resume();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && toast.isConnected) dismiss();
  });

  window.requestAnimationFrame(() => {
    toast.classList.add('is-visible');
    resume();
  });
}

function setupGasTankWeightForms() {
  document.querySelectorAll('.gas-tank-weight-form').forEach((form) => {
    const tare = form.querySelector('[data-gas-tare]');
    const fullGross = form.querySelector('[data-gas-full-gross]');
    const currentGross = form.querySelector('[data-gas-current-gross]');
    const reorder = form.querySelector('[data-gas-reorder]');
    const capacityOutput = form.querySelector('[data-gas-capacity]');
    const currentNetOutput = form.querySelector('[data-gas-current-net]');
    if (!tare || !fullGross || !currentGross) return;

    const update = () => {
      const tareKg = Number.parseFloat(tare.value);
      const fullGrossKg = Number.parseFloat(fullGross.value);
      const currentGrossKg = Number.parseFloat(currentGross.value);
      const capacityKg = Number.isFinite(tareKg) && Number.isFinite(fullGrossKg)
        ? Math.max(0, fullGrossKg - tareKg)
        : 0;
      const currentNetKg = Number.isFinite(tareKg) && Number.isFinite(currentGrossKg)
        ? Math.max(0, currentGrossKg - tareKg)
        : 0;

      if (capacityOutput) capacityOutput.textContent = `${capacityKg.toFixed(3)} kg`;
      if (currentNetOutput) currentNetOutput.textContent = `${currentNetKg.toFixed(3)} kg`;
      if (Number.isFinite(tareKg)) {
        fullGross.min = (tareKg + 0.001).toFixed(3);
        currentGross.min = tareKg.toFixed(3);
      }
      if (Number.isFinite(fullGrossKg)) currentGross.max = fullGrossKg.toFixed(3);
      if (reorder) reorder.max = capacityKg.toFixed(3);
    };

    [tare, fullGross, currentGross].forEach((field) => field.addEventListener('input', update));
    update();
  });
}

function setupGasReconciliationForms() {
  document.querySelectorAll('.gas-reconcile-weight-form').forEach((form) => {
    const tare = form.querySelector('[data-gas-reconcile-tare]');
    const gross = form.querySelector('[data-gas-reconcile-gross]');
    const net = form.querySelector('[data-gas-reconcile-net]');
    if (!tare || !gross || !net) return;
    const update = () => {
      const tareKg = Number.parseFloat(tare.value);
      const grossKg = Number.parseFloat(gross.value);
      net.value = Number.isFinite(tareKg) && Number.isFinite(grossKg)
        ? `${Math.max(0, grossKg - tareKg).toFixed(3)} kg`
        : 'Enter the gross weight';
    };
    gross.addEventListener('input', update);
    update();
  });
}

window.addEventListener('load', () => {
  window.setTimeout(() => {
    document.querySelectorAll('[data-auth-preloader]').forEach((preloader) => {
      preloader.classList.add('is-hidden');
    });
  }, 420);
});

document.addEventListener('click', (event) => {
  const toggle = event.target.closest('[data-password-toggle]');
  if (!toggle) return;
  const input = document.getElementById(toggle.dataset.passwordToggle);
  if (!input) return;
  const visible = input.type === 'text';
  input.type = visible ? 'password' : 'text';
  toggle.setAttribute('aria-label', visible ? 'Show password' : 'Hide password');
  const icon = toggle.querySelector('i');
  icon?.classList.toggle('fa-eye', visible);
  icon?.classList.toggle('fa-eye-slash', !visible);
});

const supportWidget = document.getElementById('support-chat');
if (supportWidget && supportWidget.parentElement !== document.body) {
  document.body.appendChild(supportWidget);
}

document.addEventListener('click', (event) => {
  const toggle = event.target.closest('[data-chat-toggle]');
  if (!toggle) return;
  const widget = toggle.closest('.support-widget');
  if (!widget) return;
  widget.classList.toggle('open');
});

document.querySelectorAll('.chat-panel[data-feed-url]').forEach((panel) => {
  const feed = panel.querySelector('.chat-feed');
  const url = panel.dataset.feedUrl;
  if (!feed || !url) return;

  const render = (messages) => {
    feed.innerHTML = messages.map((message) => {
      const side = message.senderType === 'PLATFORM' ? 'platform' : 'shop';
      const when = message.createdAt ? new Date(message.createdAt).toLocaleString([], { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '';
      return `<article class="chat-message ${side}">
        <strong>${escapeHtml(message.senderName || 'User')}</strong>
        <p>${escapeHtml(message.message || '')}</p>
        <small>${escapeHtml(when)}</small>
      </article>`;
    }).join('');
    feed.scrollTop = feed.scrollHeight;
  };

  const refresh = async () => {
    try {
      const response = await fetch(url, { headers: { Accept: 'application/json' } });
      if (response.ok) render(await response.json());
    } catch (error) {
      console.debug('Chat refresh failed', error);
    }
  };

  panel.addEventListener('submit', async (event) => {
    const form = event.target.closest('form[data-async-chat]');
    if (!form) return;
    event.preventDefault();
    const button = form.querySelector('button[type="submit"]');
    button?.setAttribute('disabled', 'disabled');
    try {
      const response = await fetch(form.action, {
        method: 'POST',
        body: new FormData(form),
        headers: { 'X-Requested-With': 'XMLHttpRequest' }
      });
      if (response.ok || response.redirected) {
        form.reset();
        await refresh();
      }
    } catch (error) {
      console.debug('Chat send failed', error);
    } finally {
      button?.removeAttribute('disabled');
    }
  });

  refresh();
  window.setInterval(refresh, 3500);
});

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function openRequestedModal() {
  const trigger = document.querySelector('[data-auto-open-modal]');
  if (!trigger?.dataset.autoOpenModal) return;
  if (document.querySelector(trigger.dataset.autoOpenModal)) {
    window.location.hash = trigger.dataset.autoOpenModal;
  }
}

function setupShiftCloseForms() {
  document.querySelectorAll('[data-shift-close-form]').forEach((form) => {
    if (form.dataset.shiftCloseReady === 'true') return;
    form.dataset.shiftCloseReady = 'true';

    const expectedUsd = parseMoney(form.dataset.expectedUsd);
    const expectedZwg = parseMoney(form.dataset.expectedZwg);
    const actualUsd = form.querySelector('input[name="actualUsd"]');
    const actualZwg = form.querySelector('input[name="actualZwg"]');
    const varianceUsd = form.querySelector('[data-variance-usd]');
    const varianceZwg = form.querySelector('[data-variance-zwg]');
    const reason = form.querySelector('[data-variance-reason]');
    const note = form.querySelector('[data-variance-note]');
    const varianceCards = Array.from(form.querySelectorAll('[data-variance-summary]'));

    const refresh = () => {
      const usd = parseMoney(actualUsd?.value) - expectedUsd;
      const zwg = parseMoney(actualZwg?.value) - expectedZwg;
      const hasVariance = Math.abs(usd) > 0.009 || Math.abs(zwg) > 0.009;

      if (varianceUsd) varianceUsd.textContent = formatMoney('USD', usd);
      if (varianceZwg) varianceZwg.textContent = formatMoney('ZWG', zwg);
      varianceCards.forEach((card) => {
        const value = card.contains(varianceUsd) ? usd : zwg;
        card.classList.toggle('is-positive', value > 0.009);
        card.classList.toggle('is-negative', value < -0.009);
      });
      if (reason) {
        reason.required = hasVariance;
        reason.setAttribute('aria-required', String(hasVariance));
      }
      if (note) note.hidden = !hasVariance;
    };

    actualUsd?.addEventListener('input', refresh);
    actualZwg?.addEventListener('input', refresh);
    form.addEventListener('submit', (event) => {
      refresh();
      const needsReason = reason?.required;
      if (needsReason && !reason.value.trim()) {
        event.preventDefault();
        event.stopPropagation();
        reason.focus();
      }
    });
    refresh();
  });
}

function parseMoney(value) {
  const parsed = Number.parseFloat(String(value ?? '0').replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatMoney(currency, value) {
  return `${currency} ${value.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`;
}

function enhanceForms() {
  document.querySelectorAll('form').forEach((form) => {
    if (form.dataset.validationReady === 'true') return;
    form.dataset.validationReady = 'true';
    form.noValidate = true;

    form.querySelectorAll('input, select, textarea').forEach((field) => {
      if (field.required) {
        field.setAttribute('aria-required', 'true');
        markRequiredLabel(field);
      }

      field.addEventListener('input', () => clearFieldError(field));
      field.addEventListener('change', () => clearFieldError(field));
      field.addEventListener('blur', () => {
        if (field.required || field.value) validateField(field, form);
      });
    });

    form.addEventListener('submit', (event) => {
      if (form.dataset.asyncChat !== undefined) return;
      if (validateForm(form)) {
        form.classList.add('is-loading');
        return;
      }
      form.classList.remove('is-loading');
      event.preventDefault();
      event.stopPropagation();
    });
  });
}

function setupSignupWizard() {
  document.querySelectorAll('[data-signup-wizard]').forEach((form) => {
    if (form.dataset.wizardReady === 'true') return;
    form.dataset.wizardReady = 'true';

    const panels = Array.from(form.querySelectorAll('[data-wizard-panel]'));
    const steps = Array.from(form.querySelectorAll('[data-wizard-jump]'));
    const progress = form.querySelector('[data-wizard-progress]');
    const prev = form.querySelector('[data-wizard-prev]');
    const next = form.querySelector('[data-wizard-next]');
    const submit = form.querySelector('[data-wizard-submit]');
    const login = form.querySelector('[data-wizard-login]');
    const moduleInputs = Array.from(form.querySelectorAll('input[name="modules"]'));
    const planInputs = Array.from(form.querySelectorAll('input[name="planId"]'));
    const businessMode = form.querySelector('[name="businessMode"]');
    let current = 0;

    const syncModuleSelection = (changedModule) => {
      const planCard = form.querySelector('input[name="planId"]:checked')?.closest('.signup-plan-card');
      const allowed = new Set((planCard?.dataset.allowedModules || 'SHOP_MODULE')
        .split(',').map((value) => value.trim()).filter(Boolean));
      const allowsMixed = planCard?.dataset.allowMixed === 'true' && allowed.size > 1;
      const mixedOption = businessMode?.querySelector('option[value="MIXED_MODULE"]');

      if (mixedOption) mixedOption.disabled = !allowsMixed;
      if (!allowsMixed && businessMode?.value === 'MIXED_MODULE') businessMode.value = 'SINGLE_MODULE';

      moduleInputs.forEach((input) => {
        const available = allowed.has(input.value);
        input.disabled = !available;
        input.closest('.signup-module-card')?.classList.toggle('is-unavailable', !available);
        if (!available) input.checked = false;
      });

      if (businessMode?.value === 'SINGLE_MODULE') {
        const checked = moduleInputs.filter((input) => input.checked && !input.disabled);
        const keep = changedModule?.checked && !changedModule.disabled ? changedModule : checked[0];
        checked.forEach((input) => { input.checked = input === keep; });
      }

      if (!moduleInputs.some((input) => input.checked && !input.disabled)) {
        const fallback = moduleInputs.find((input) => input.value === 'SHOP_MODULE' && !input.disabled)
          || moduleInputs.find((input) => !input.disabled);
        if (fallback) fallback.checked = true;
      }
      updateSignupReview(form);
    };

    planInputs.forEach((input) => input.addEventListener('change', () => syncModuleSelection()));
    businessMode?.addEventListener('change', () => syncModuleSelection());
    moduleInputs.forEach((input) => input.addEventListener('change', () => syncModuleSelection(input)));

    const setStep = (index) => {
      current = Math.max(0, Math.min(index, panels.length - 1));
      panels.forEach((panel, panelIndex) => {
        const active = panelIndex === current;
        panel.hidden = !active;
        panel.classList.toggle('is-active', active);
      });
      steps.forEach((step, stepIndex) => {
        step.classList.toggle('is-active', stepIndex === current);
        step.classList.toggle('is-complete', stepIndex < current);
      });
      if (progress) {
        progress.style.width = `${((current + 1) / panels.length) * 100}%`;
      }
      if (prev) prev.hidden = current === 0;
      if (login) login.hidden = current !== 0;
      if (next) next.hidden = current === panels.length - 1;
      if (submit) submit.hidden = current !== panels.length - 1;
      updateSignupReview(form);
      form.classList.remove('is-loading');
      panels[current]?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    };

    const validatePanel = () => {
      clearFormAlert(form);
      if (panels[current].querySelector('input[name="modules"]')
          && !moduleInputs.some((input) => input.checked && !input.disabled)) {
        showFormAlert(form, 'Select at least one business module.');
        moduleInputs.find((input) => !input.disabled)?.focus({ preventScroll: true });
        return false;
      }
      const fields = Array.from(panels[current].querySelectorAll('input, select, textarea'));
      const radioGroups = new Set();
      let firstInvalid = null;

      fields.forEach((field) => {
        if (shouldSkipValidation(field)) return;
        if (field.type === 'radio') {
          if (!field.name || radioGroups.has(field.name)) return;
          radioGroups.add(field.name);
        }
        const valid = validateField(field, form);
        if (!valid && !firstInvalid) {
          firstInvalid = field.type === 'radio' ? form.querySelector(`input[type="radio"][name="${cssName(field.name)}"]`) : field;
        }
      });

      if (!firstInvalid) return true;
      showFormAlert(form, 'Complete this step before continuing.');
      firstInvalid.focus({ preventScroll: true });
      firstInvalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return false;
    };

    next?.addEventListener('click', () => {
      if (validatePanel()) setStep(current + 1);
    });

    prev?.addEventListener('click', () => setStep(current - 1));

    steps.forEach((step) => {
      step.addEventListener('click', () => {
        const target = Number(step.dataset.wizardJump || 0);
        if (target <= current) {
          setStep(target);
          return;
        }
        if (validatePanel()) setStep(Math.min(target, current + 1));
      });
    });

    form.addEventListener('input', () => updateSignupReview(form));
    form.addEventListener('change', () => updateSignupReview(form));
    form.addEventListener('submit', (event) => {
      if (current < panels.length - 1) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (validatePanel()) setStep(current + 1);
        return;
      }
      if (!validatePanel()) {
        event.preventDefault();
        event.stopImmediatePropagation();
      }
    }, true);

    syncModuleSelection();
    setStep(0);
  });
}

function updateSignupReview(form) {
  const set = (name, value) => {
    const target = form.querySelector(`[data-review="${name}"]`);
    if (target) target.textContent = value || target.dataset.placeholder || '-';
  };

  const selectedPlan = form.querySelector('input[name="planId"]:checked')?.closest('.signup-plan-card');
  const moduleLabels = Array.from(form.querySelectorAll('input[name="modules"]:checked'))
    .map((input) => input.closest('label')?.querySelector('strong, span:last-child')?.textContent?.trim())
    .filter(Boolean);
  const mode = form.querySelector('[name="businessMode"]');
  const selectedMode = mode?.selectedOptions?.[0]?.textContent?.trim();
  const company = form.querySelector('[name="companyName"]')?.value?.trim();
  const email = form.querySelector('[name="email"]')?.value?.trim();
  const phone = form.querySelector('[name="phone"]')?.value?.trim();
  const username = form.querySelector('[name="adminUsername"]')?.value?.trim();
  const adminEmail = form.querySelector('[name="adminEmail"]')?.value?.trim() || email;

  set('package', selectedPlan?.querySelector('strong')?.textContent?.trim() || 'Selected package');
  set('price', selectedPlan?.querySelector('em')?.textContent?.trim() || 'Payment will follow signup');
  set('modules', moduleLabels.join(', ') || 'Choose modules');
  set('mode', selectedMode || 'Single module');
  set('company', company || 'Company name');
  set('contact', [email, phone].filter(Boolean).join(' - ') || 'Email and phone');
  set('admin', username || 'Admin user');
  set('adminEmail', adminEmail || 'Admin email');
}

function validateForm(form) {
  clearFormAlert(form);
  const fields = Array.from(form.querySelectorAll('input, select, textarea'));
  const validatedRadioGroups = new Set();
  let firstInvalid = null;

  fields.forEach((field) => {
    if (shouldSkipValidation(field)) return;
    if (field.type === 'radio') {
      if (!field.name || validatedRadioGroups.has(field.name)) return;
      validatedRadioGroups.add(field.name);
    }
    const valid = validateField(field, form);
    if (!valid && !firstInvalid) {
      firstInvalid = field.type === 'radio' ? form.querySelector(`input[type="radio"][name="${cssName(field.name)}"]`) : field;
    }
  });

  if (!firstInvalid) {
    return true;
  }

  showFormAlert(form, 'Please complete the highlighted required fields before saving.');
  firstInvalid.focus({ preventScroll: true });
  firstInvalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
  return false;
}

function validateField(field, form) {
  if (shouldSkipValidation(field)) return true;
  clearFieldError(field);

  const message = fieldValidationMessage(field, form);
  if (!message) return true;

  field.classList.add('field-invalid');
  field.setAttribute('aria-invalid', 'true');
  showFieldError(field, message);
  return false;
}

function shouldSkipValidation(field) {
  const type = (field.getAttribute('type') || '').toLowerCase();
  const ignored = ['hidden', 'submit', 'button', 'reset', 'image'];
  return field.disabled || ignored.includes(type);
}

function fieldValidationMessage(field, form) {
  const label = readableFieldName(field);
  const type = (field.getAttribute('type') || '').toLowerCase();

  if (type === 'radio' && field.required) {
    const checked = form.querySelector(`input[type="radio"][name="${cssName(field.name)}"]:checked`);
    return checked ? '' : `${label} is required.`;
  }

  if (type === 'checkbox' && field.required && !field.checked) {
    return `${label} is required.`;
  }

  if (field.required) {
    if (field.tagName === 'SELECT' && field.multiple) {
      const selected = Array.from(field.options).some((option) => option.selected && option.value !== '');
      if (!selected) return `${label} is required.`;
    } else if (type === 'file') {
      if (!field.files || field.files.length === 0) return `${label} is required.`;
    } else if (!String(field.value || '').trim()) {
      return `${label} is required.`;
    }
  }

  if (!String(field.value || '').trim()) return '';
  if (field.validity.typeMismatch && type === 'email') return `Enter a valid email address.`;
  if (field.validity.typeMismatch && type === 'url') return `Enter a valid website URL.`;
  if (field.validity.badInput) return `${label} must be a valid number.`;
  if (field.validity.rangeUnderflow) return `${label} must be at least ${field.min}.`;
  if (field.validity.rangeOverflow) return `${label} must be at most ${field.max}.`;
  if (field.validity.stepMismatch) return `${label} has an invalid increment.`;
  if (field.validity.tooShort) return `${label} is too short.`;
  if (field.validity.tooLong) return `${label} is too long.`;
  if (field.validity.patternMismatch) return field.title || `${label} has an invalid format.`;
  return '';
}

function markRequiredLabel(field) {
  const label = field.closest('label') || labelByFor(field);
  if (!label || label.dataset.requiredMarked === 'true') return;
  label.dataset.requiredMarked = 'true';
  label.classList.add('required-field');

  const star = document.createElement('span');
  star.className = 'required-star';
  star.setAttribute('aria-hidden', 'true');
  star.textContent = '*';

  const firstElement = Array.from(label.childNodes).find((node) => node.nodeType === Node.ELEMENT_NODE);
  if (firstElement && firstElement !== field && firstElement.tagName !== 'INPUT' && firstElement.tagName !== 'SELECT' && firstElement.tagName !== 'TEXTAREA') {
    firstElement.insertAdjacentElement('afterend', star);
    return;
  }
  label.insertBefore(star, field);
}

function labelByFor(field) {
  if (!field.id) return null;
  return document.querySelector(`label[for="${cssName(field.id)}"]`);
}

function readableFieldName(field) {
  const label = field.closest('label') || labelByFor(field);
  if (label) {
    const clone = label.cloneNode(true);
    clone.querySelectorAll('input, select, textarea, .required-star, .field-error').forEach((node) => node.remove());
    const text = clone.textContent.replace('*', '').trim();
    if (text) return text;
  }
  return field.placeholder || field.name || 'This field';
}

function showFieldError(field, message) {
  const error = document.createElement('small');
  error.className = 'field-error';
  error.textContent = message;
  const target = field.closest('.auth-input-shell') || field;
  target.insertAdjacentElement('afterend', error);
}

function clearFieldError(field) {
  field.classList.remove('field-invalid');
  field.removeAttribute('aria-invalid');
  const target = field.closest('.auth-input-shell') || field;
  const next = target.nextElementSibling;
  if (next?.classList.contains('field-error')) {
    next.remove();
  }
}

function showFormAlert(form, message) {
  const alert = document.createElement('div');
  alert.className = 'form-alert alert-error';
  alert.setAttribute('role', 'alert');
  alert.textContent = message;
  form.append(alert);
}

function clearFormAlert(form) {
  form.querySelectorAll(':scope > .form-alert').forEach((alert) => alert.remove());
}

function cssName(value) {
  if (window.CSS && CSS.escape) return CSS.escape(value || '');
  return String(value || '').replace(/["\\]/g, '\\$&');
}

document.addEventListener('click', (event) => {
  const trigger = event.target.closest('[data-export-table]');
  if (!trigger) return;
  const table = document.querySelector(trigger.dataset.exportTable);
  if (!table) return;

  const csv = Array.from(table.rows).map((row) => Array.from(row.cells).map((cell) => {
    const value = cell.innerText.replace(/\s+/g, ' ').trim().replace(/"/g, '""');
    return `"${value}"`;
  }).join(',')).join('\r\n');
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = trigger.dataset.exportName || 'retailzw-report.csv';
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
});
