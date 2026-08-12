(() => {
  'use strict';

  const shell = document.querySelector('.community-command-shell');
  if (!shell) return;

  const rows = Array.from(shell.querySelectorAll('[data-community-thread]'));
  const panels = Array.from(shell.querySelectorAll('[data-community-panel]'));

  function selectConversation(id, updateUrl = true) {
    const selectedRow = rows.find((row) => row.dataset.communityThread === id);
    const selectedPanel = panels.find((panel) => panel.dataset.communityPanel === id);
    if (!selectedRow || !selectedPanel) return;

    rows.forEach((row) => {
      const active = row === selectedRow;
      row.classList.toggle('active', active);
      row.setAttribute('aria-selected', String(active));
    });
    panels.forEach((panel) => {
      panel.hidden = panel !== selectedPanel;
    });

    if (updateUrl && window.history?.replaceState) {
      window.history.replaceState(null, '', `#community-thread-${id}`);
    }
  }

  rows.forEach((row) => {
    row.addEventListener('click', () => selectConversation(row.dataset.communityThread));
  });

  const hashMatch = window.location.hash.match(/^#community-thread-(\d+)$/);
  if (hashMatch) selectConversation(hashMatch[1], false);

  shell.querySelectorAll('.community-reply-composer').forEach((form) => {
    form.addEventListener('submit', () => {
      const button = form.querySelector('button[type="submit"]');
      if (!button || button.disabled) return;
      button.disabled = true;
      button.classList.add('is-loading');
      button.querySelector('span').textContent = 'Sending...';
    });
  });

  shell.querySelectorAll('time').forEach((element) => {
    const raw = element.textContent.trim();
    if (!raw) return;
    const parsed = new Date(raw.replace(' ', 'T'));
    if (Number.isNaN(parsed.getTime())) return;
    element.dateTime = parsed.toISOString();
    element.textContent = parsed.toLocaleString(undefined, {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit'
    });
  });

  const chart = shell.querySelector('[data-community-chart]');
  if (chart) {
    const values = Array.from(chart.querySelectorAll('.community-chart-data span'))
      .map((point) => ({
        label: point.dataset.label || '',
        value: Number(point.dataset.value || 0)
      }))
      .filter((point) => Number.isFinite(point.value));

    if (values.length) {
      const xStart = 24;
      const xEnd = 700;
      const yTop = 24;
      const yBottom = 162;
      const max = Math.max(...values.map((point) => point.value), 1);
      const step = values.length === 1 ? 0 : (xEnd - xStart) / (values.length - 1);
      const points = values.map((point, index) => ({
        ...point,
        x: values.length === 1 ? (xStart + xEnd) / 2 : xStart + (step * index),
        y: yBottom - ((point.value / max) * (yBottom - yTop))
      }));

      const pointString = points.map((point) => `${point.x},${point.y}`).join(' ');
      const line = chart.querySelector('.community-chart-line');
      const area = chart.querySelector('.community-chart-area');
      const pointGroup = chart.querySelector('.community-chart-points');
      line?.setAttribute('points', pointString);
      area?.setAttribute('d', `M ${points[0].x} ${yBottom} L ${pointString.replaceAll(',', ' ')} L ${points.at(-1).x} ${yBottom} Z`);

      if (pointGroup) {
        points.forEach((point) => {
          const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
          circle.setAttribute('cx', point.x);
          circle.setAttribute('cy', point.y);
          circle.setAttribute('r', '4');
          circle.setAttribute('aria-label', `${point.label}: ${point.value} visits`);
          pointGroup.append(circle);
        });
      }

      const labels = chart.querySelector('.community-chart-labels');
      if (labels) {
        const labelCount = Math.min(values.length, 6);
        const indexes = new Set();
        for (let index = 0; index < labelCount; index += 1) {
          indexes.add(Math.round((index * (values.length - 1)) / Math.max(labelCount - 1, 1)));
        }
        indexes.forEach((index) => {
          const label = document.createElement('span');
          const parsed = new Date(`${values[index].label}T00:00:00`);
          label.textContent = Number.isNaN(parsed.getTime())
            ? values[index].label
            : parsed.toLocaleDateString(undefined, { day: '2-digit', month: 'short' });
          labels.append(label);
        });
      }

      chart.querySelector('.community-chart-empty')?.remove();
    }
  }

  const topicRows = Array.from(shell.querySelectorAll('[data-topic-value]'));
  const topicTotal = topicRows.reduce((total, row) => total + Number(row.dataset.topicValue || 0), 0);
  topicRows.forEach((row) => {
    const value = Number(row.dataset.topicValue || 0);
    const share = topicTotal > 0 ? Math.round((value / topicTotal) * 100) : 0;
    row.style.setProperty('--community-topic-share', `${share}%`);
    const output = row.querySelector('strong');
    if (output) {
      output.textContent = `${share}%`;
      output.title = `${value.toLocaleString()} visits`;
    }
  });
})();
