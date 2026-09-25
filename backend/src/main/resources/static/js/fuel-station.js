(() => {
  'use strict';
  const root = document.querySelector('.fuel-shell');
  if (!root) return;
  const toolbar = document.getElementById('fuel-action-toolbar');
  const managed = root.dataset.fuelManage === 'true';
  const page = root.dataset.fuelPage;
  const branchForm=root.querySelector('.shop-branch-switcher');
  if(branchForm){branchForm.action=location.pathname;branchForm.method='get';branchForm.querySelector('[name="returnTo"]')?.remove();branchForm.querySelector('[name="_csrf"]')?.remove();}
  const make = (tag, text, className) => { const el = document.createElement(tag); if (text) el.textContent = text; if (className) el.className = className; return el; };
  function modal(title, content) {
    const dialog = make('dialog', null, 'fuel-dialog');
    const header = make('div', null, 'fuel-dialog-head');
    header.append(make('h2', title));
    const close = make('button', '×', 'btn btn-light'); close.type = 'button'; close.setAttribute('aria-label','Close dialog');
    close.onclick = () => dialog.close(); header.append(close);
    dialog.append(header, content); root.append(dialog);
    dialog.addEventListener('click', e => { if(e.target === dialog) { const r=dialog.getBoundingClientRect(); if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom) dialog.close(); } });
    return dialog;
  }
  const dialogs = new Map();
  root.querySelectorAll('.fuel-form').forEach(form => {
    const article = form.closest('article');
    if(!managed) { article?.remove(); return; }
    const title = article.querySelector('h2')?.textContent || 'Fuel operation';
    const dialog = modal(title, form);
    const cancel = make('button','Cancel','btn btn-light'); cancel.type='button'; cancel.onclick=()=>dialog.close(); form.append(cancel);
    const key = new URL(form.action).pathname.split('/').pop(); dialogs.set(key,{dialog,form});
    const open = make('button',title,'btn btn-primary');open.type='button';open.onclick=()=>dialog.showModal();toolbar.append(open);
    form.addEventListener('submit', () => { form.querySelector('button:not([type="button"])')?.setAttribute('disabled','disabled'); });
    article.remove();
  });
  document.getElementById('fuel-extra-forms')?.remove();
  root.querySelectorAll('.fuel-section-grid .fuel-table-wrap').forEach(wrap=>{const article=wrap.closest('article');if(!wrap.closest('#fuel-record-table')&&page!=='setup'&&!article?.querySelector('h2')?.textContent.includes('Month-to-date'))article?.remove();});
  if(page==='pricing') root.querySelectorAll('.fuel-empty').forEach(el=>el.closest('article')?.remove());
  root.querySelectorAll('.fuel-module-tabs a').forEach(a=>{if(new URL(a.href).pathname===location.pathname)a.classList.add('active');});
  const detailBody=make('dl',null,'fuel-record-details');const detail=modal('Record details',detailBody);
  function showDetails(entries,title) {detail.querySelector('h2').textContent=title;detailBody.replaceChildren();entries.forEach(([key,value])=>{const row=make('div');row.append(make('dt',key),make('dd',value));detailBody.append(row);});detail.showModal();}
  root.querySelectorAll('.fuel-view-record').forEach(btn=>{btn.onclick=()=>{const table=btn.closest('table');const columns=[...table.querySelectorAll('th')].map(x=>x.textContent);const cells=[...btn.closest('tr').querySelectorAll('td')].slice(0,-1);showDetails(cells.map((cell,i)=>[columns[i],cell.textContent]),'Record details');};});
  root.querySelectorAll('.fuel-tank').forEach(tank=>{
    const button=make('button','View tank','btn btn-light');button.type='button';button.onclick=()=>showDetails([...tank.querySelectorAll('dl div')].map(el=>[el.querySelector('dt').textContent,el.querySelector('dd').textContent]),tank.querySelector('strong').textContent);tank.append(button);
    const dip=dialogs.get('dips');if(dip){const record=make('button','Record dip','btn btn-light');record.type='button';record.onclick=()=>{dip.form.elements.tankId.value=tank.dataset.tankId;dip.dialog.showModal();};tank.append(record);}
  });
  root.querySelectorAll('.fuel-nozzle').forEach(nozzle=>{const icon=make('i',null,'fa-solid fa-gas-pump fuel-pump-icon');icon.setAttribute('aria-hidden','true');nozzle.prepend(icon);const btn=make('button','Details','btn btn-light');btn.type='button';btn.onclick=()=>showDetails([['Fuel',nozzle.querySelector('strong').textContent],['Meter',nozzle.querySelector('small').textContent],['Availability',nozzle.querySelector('em').textContent]],nozzle.querySelector('b').textContent);nozzle.append(btn);});
  root.querySelectorAll('.fuel-section-grid table').forEach(table=>{if(table.closest('#fuel-record-table'))return;const th=make('th','Actions');table.querySelector('thead tr')?.append(th);table.querySelectorAll('tbody tr').forEach(row=>{const values=[...row.cells].map(cell=>cell.textContent);const td=make('td');const btn=make('button','View details','btn btn-light');btn.type='button';btn.onclick=()=>showDetails(values.map((v,i)=>[[...table.querySelectorAll('th')][i].textContent,v]),'Asset details');td.append(btn);row.append(td);});});
  const exportButton=document.getElementById('fuel-export');if(exportButton)exportButton.onclick=()=>{const table=document.querySelector('#fuel-record-table table');const rows=[...table.rows].map(row=>[...row.cells].filter(cell=>!cell.querySelector('button')&&cell.textContent!=='Actions').map(cell=>{let value=cell.textContent.trim();if(/^[=+\-@]/.test(value))value="'"+value;return '"'+value.replaceAll('"','""')+'"';}).join(','));const blob=new Blob(['\uFEFF'+rows.join('\r\n')],{type:'text/csv;charset=utf-8'});const url=URL.createObjectURL(blob);const a=make('a');a.href=url;a.download='fuel-'+page+'-page.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
})();
