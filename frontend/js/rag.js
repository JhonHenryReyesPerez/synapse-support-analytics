/** Herramientas de conocimiento. Toda operación externa pasa por Spring Boot. */
const RAG = {
  profiles: [], searchSeq: 0, adminSeq: 0, gapPage: 0, imported: null,
  labels: { TEXT: 'Textual', DENSE: 'Vectorial', HYBRID: 'Híbrida', RERANK: 'Híbrida con reranking' },
  el(id) { return document.getElementById(id); },
  value(id) { return this.el(id)?.value?.trim() || ''; },
  text(id, message) { const el=this.el(id); if(el) el.textContent=message; },
  async checked(promise) { const res=await promise; if(!res.ok) throw new Error(res.error || 'No se pudo completar la operación.'); return res.data; },
  error(error) { UI.showToast(error.message || 'No se pudo completar la operación.', 'error'); },
  bind(id, type, handler) { this.el(id)?.addEventListener(type, async event => {
    if(type==='submit')event.preventDefault();
    const button=type==='submit'?event.target.querySelector('button[type="submit"]'):event.currentTarget;
    UI.setLoading(button,true);
    try { await handler.call(this,event); } catch(error) { this.error(error); } finally { UI.setLoading(button,false); }
  }); },
  async init() {
    this.bind('rag-search-form','submit',this.search);
    this.bind('rag-profile-form','submit',this.createProfile);
    this.bind('rag-plan','click',()=>this.indexAction('plan'));
    this.bind('rag-health','click',()=>this.indexAction('health'));
    this.bind('rag-index','click',()=>this.indexAction('index'));
    this.bind('rag-repair','click',()=>this.indexAction('repair'));
    this.bind('rag-activate','click',()=>this.indexAction('activate'));
    this.bind('rag-case-form','submit',this.saveCase);
    this.bind('rag-run-evaluation','click',this.evaluate);
    this.bind('rag-load-gaps','click',()=>this.loadGaps(false));
    this.bind('rag-more-gaps','click',()=>this.loadGaps(true));
    this.bind('rag-load-operations','click',this.operations);
    this.bind('rag-import-preview','click',this.previewImport);
    this.bind('rag-import-use','click',this.useImportedText);
    this.bind('rag-find-duplicates','click',this.duplicates);
    this.bind('rag-resolution-draft','click',this.resolutionDraft);
    this.bind('rag-similar','click',this.similarTickets);
    this.bind('rag-clarify','click',this.clarify);
    this.bind('rag-export','click',this.exportResponse);
    try { await this.loadProfiles(); } catch(error) { this.text('rag-search-feedback',error.message); }
    if(AUTH.getUser()?.role==='ADMIN') {
      this.el('rag-studio')?.addEventListener('toggle',()=>{
        if(this.el('rag-studio').open && !this.casesLoaded)this.loadCases().catch(error=>this.error(error));
      });
    }
  },
  async loadProfiles() {
    this.profiles=await this.checked(API.get('/rag/profiles'));
    for(const id of ['rag-profile','rag-admin-profile','assistant-profile','rag-evaluation-profiles']) {
      const select=this.el(id); if(!select)continue;
      const previous=select.value;
      const options=this.profiles.map(p=>`<option value="${UI.escapeHtml(p.id)}">${UI.escapeHtml(p.name)} · ${this.labels[p.strategy]||UI.escapeHtml(p.strategy)}${p.active?' · activo':''}</option>`).join('');
      select.innerHTML=(id==='assistant-profile'?'<option value="">Clásica</option>':'')+options;
      if(previous && this.profiles.some(p=>p.id===previous))select.value=previous;
      else if(id!=='assistant-profile' && id!=='rag-evaluation-profiles')select.value=this.profiles.find(p=>p.active)?.id || 'text';
    }
  },
  renderHits(target, hits) {
    const el=this.el(target);if(!el)return;
    el.innerHTML=hits.length?hits.map(h=>`<article class="rag-result"><h3><button type="button" class="assistant-history-link" onclick="KNOWLEDGE.viewArticle(${Number(h.articleId)})">${UI.escapeHtml(h.title)}</button></h3>
      <div class="rag-result-meta"><span>Artículo #${Number(h.articleId)} · v${Number(h.version)} · ${UI.escapeHtml(h.section)}</span><span>Posición ${Number(h.rank)}</span><span>${h.cosine==null?'Coseno no disponible':`Coseno ${Number(h.cosine).toFixed(4)}`}</span><span>${UI.escapeHtml(h.metric)}: ${Number(h.rankingScore).toFixed(4)}</span></div><p tabindex="0" role="region" aria-label="Evidencia del resultado">${UI.escapeHtml(h.evidence)}</p></article>`).join(''):'<p class="rag-notice">No hay resultados pertinentes en este alcance. Prueba otro término o revisa los filtros.</p>';
  },
  async search() {
    const question=this.value('rag-question');if(!question)return;
    const seq=++this.searchSeq;
    this.text('rag-search-feedback','Consultando documentación publicada…');
    const payload={question,profileId:this.value('rag-profile')||'text',limit:10};
    for(const field of ['platform','application','productVersion','architecture'])if(this.value('rag-'+field))payload[field]=this.value('rag-'+field);
    const res=await API.post('/rag/search',payload);if(seq!==this.searchSeq)return;
    if(!res.ok){this.text('rag-search-feedback',res.error || 'La búsqueda no está disponible.');return;}
    this.renderHits('rag-search-results',res.data.hits || []);
    this.text('rag-search-feedback',`${res.data.hits.length} resultados · ${this.labels[res.data.strategy]||res.data.strategy}. Las puntuaciones expresan relevancia, no certeza.`);
  },
  async createProfile() {
    const payload={};for(const key of ['id','name','model','strategy','representation','rerankModel'])payload[key]=this.value('profile-'+key);
    for(const key of ['dimension','minCosine','chunkSize'])payload[key]=Number(this.value('profile-'+key));
    await this.checked(API.post('/rag/profiles',payload));await this.loadProfiles();this.el('rag-admin-profile').value=payload.id;
    UI.showToast('Perfil candidato creado. Previsualiza e indexa antes de activarlo.','success');
  },
  async indexAction(action) {
    const profile=this.value('rag-admin-profile');if(!profile)throw new Error('Selecciona un perfil.');
    if(action==='index' && !confirm('Se indexará un lote de hasta cinco fragmentos. Los embeddings que no estén en caché pueden tener coste. ¿Continuar?'))return;
    if(action==='repair' && !confirm('Se retirarán puntos obsoletos de la colección de este perfil. Después indexa los ausentes. ¿Continuar?'))return;
    if(action==='activate' && !confirm('Este perfil será el predeterminado del buscador. Las consultas en curso conservan su configuración. ¿Continuar?'))return;
    const seq=++this.adminSeq;
    this.text('rag-index-output','Procesando…');
    const path=`/rag/profiles/${encodeURIComponent(profile)}/${action}`;
    const data=await this.checked(['plan','health'].includes(action)?API.get(path):API.post(path));
    if(seq!==this.adminSeq || this.value('rag-admin-profile')!==profile)return;
    if(action==='plan') this.text('rag-index-output',`${data.articles} artículos · ${data.chunks} fragmentos. ${data.cachedEmbeddings} embeddings reutilizables, ${data.newEmbeddings} nuevos. Entrada estimada: ${data.estimatedInputTokens} tokens. ${data.estimateNotice}`);
    else if(action==='health'||action==='repair') this.text('rag-index-output',data.physicalIndex===false?'El perfil textual no necesita un índice vectorial.':`${data.complete?'Auditoría completa':'Auditoría parcial'} · Esperados ${data.expected}, encontrados ${data.actual}. Ausentes/incompatibles ${data.missing.length}; obsoletos/huérfanos ${data.obsoleteOrOrphan.length}. Retirados ${data.removed}. Indexa por lotes para recuperar los ausentes.`);
    else if(action==='index')this.text('rag-index-output',`${data.indexed} fragmentos indexados. ${data.done?'No quedan fragmentos pendientes.':'Ejecuta el siguiente lote para continuar.'}`);
    else {this.text('rag-index-output','Perfil activado. Puedes volver al anterior desde esta misma lista.');await this.loadProfiles();}
  },
  async loadCases() {
    this.cases=await this.checked(API.get('/rag/evaluation/cases'));this.casesLoaded=true;
    this.el('rag-evaluation-cases').innerHTML=this.cases.length?this.cases.map(c=>`<label><input type="checkbox" data-case-id="${Number(c.id)}"> ${UI.escapeHtml(c.question)} <small>· ${UI.escapeHtml(c.split)} · ${UI.escapeHtml(c.category||'Sin tema')}</small></label>`).join(''):'<p>Añade preguntas revisadas para construir el banco.</p>';
  },
  async saveCase() {
    const raw=this.value('case-ids'),ids=raw?raw.split(',').map(s=>Number(s.trim())):[];
    if(ids.some(id=>!Number.isSafeInteger(id)||id<1))throw new Error('Usa IDs enteros positivos separados por coma.');
    const payload={question:this.value('case-question'),expectedArticleIds:[...new Set(ids)],expectNoResults:this.el('case-abstain').checked,split:this.value('case-split'),category:this.value('case-category'),notes:this.value('case-notes')};
    await this.checked(API.post('/rag/evaluation/cases',payload));await this.loadCases();UI.showToast('Caso guardado para evaluación.','success');
  },
  async evaluate() {
    const caseIds=[...this.el('rag-evaluation-cases').querySelectorAll('input:checked')].map(el=>Number(el.dataset.caseId));
    const profileIds=[...this.el('rag-evaluation-profiles').selectedOptions].map(o=>o.value);
    if(!caseIds.length||!profileIds.length||profileIds.length>3||caseIds.length*profileIds.length>30)throw new Error('Selecciona casos y hasta tres perfiles, con un máximo de 30 búsquedas.');
    if(!confirm(`Se ejecutarán ${caseIds.length*profileIds.length} búsquedas de evaluación. Los perfiles vectoriales pueden consumir el proveedor. ¿Continuar?`))return;
    this.text('rag-evaluation-feedback','Ejecutando casos…');
    const data=await this.checked(API.post('/rag/evaluation/runs',{caseIds,profileIds}));
    this.text('rag-evaluation-feedback',`${data.results.length} resultados. Corpus ${data.corpusHash.slice(0,12)}. ${data.notice}`);
    const metric=v=>v==null?'—':Number(v).toFixed(3);
    this.el('rag-evaluation-results').innerHTML=`<table><thead><tr><th>Caso</th><th>Perfil</th><th>Conjunto</th><th>Recall@5</th><th>MRR@5</th><th>nDCG@5</th><th>Respuesta/abstención</th><th>Estado</th></tr></thead><tbody>${data.results.map(r=>`<tr><td>${Number(r.caseId)}</td><td>${UI.escapeHtml(r.profileId)}</td><td>${UI.escapeHtml(r.split)}</td><td>${metric(r.recallAt5)}</td><td>${metric(r.mrrAt5)}</td><td>${metric(r.ndcgAt5)}</td><td>${r.abstentionCorrect==null?'—':r.abstentionCorrect?'Correcta':'Revisar'}</td><td>${UI.escapeHtml(r.status)}</td></tr>`).join('')}</tbody></table>`;
  },
  async loadGaps(more) {
    const page=more?this.gapPage+1:0;
    const data=await this.checked(API.get('/rag/gaps?page='+page));this.gapPage=page;
    const html=data.map(q=>`<article class="rag-result"><h4>Consulta #${Number(q.id)} · incidente #${Number(q.ticket_id)}</h4><p>${UI.escapeHtml(q.question)}</p><div class="rag-result-meta"><span>${UI.escapeHtml(q.status)}</span><span>${q.score==null?'Sin calificación':`Utilidad: ${Number(q.score)}/5`}</span><span>${UI.escapeHtml(q.diagnosis||'Sin revisar')}</span></div><label class="form-label" for="gap-diagnosis-${Number(q.id)}">Diagnóstico</label><select class="form-select" id="gap-diagnosis-${Number(q.id)}"><option value="MISSING_CONTENT">Falta contenido</option><option value="INCOMPLETE">Contenido incompleto</option><option value="RETRIEVAL">Fallo de recuperación</option><option value="UNFAITHFUL">Respuesta infiel</option><option value="OUT_OF_SCOPE">Fuera de alcance</option><option value="RESOLVED">Corregido y verificado</option></select><label class="form-label" for="gap-notes-${Number(q.id)}">Acción y verificación</label><textarea class="form-control" id="gap-notes-${Number(q.id)}" maxlength="4000">${UI.escapeHtml(q.notes||'')}</textarea><label class="form-label" for="gap-article-${Number(q.id)}">Artículo relacionado (opcional)</label><input class="form-control" id="gap-article-${Number(q.id)}" type="number" min="1" value="${q.article_id==null?'':Number(q.article_id)}"><button type="button" class="btn btn-outline btn-sm" onclick="RAG.reviewGap(${Number(q.id)})">Guardar revisión</button></article>`).join('');
    if(more)this.el('rag-gaps').insertAdjacentHTML('beforeend',html);else this.el('rag-gaps').innerHTML=html||'<p>No hay casos pendientes en esta selección.</p>';
    this.el('rag-more-gaps').hidden=data.length<50;
    data.forEach(q=>{if(q.diagnosis)this.el('gap-diagnosis-'+q.id).value=q.diagnosis;});
  },
  async reviewGap(id) {
    try {
      const raw=this.value('gap-article-'+id),articleId=raw?Number(raw):null;
      if(articleId!==null && (!Number.isSafeInteger(articleId)||articleId<1))throw new Error('El artículo debe tener un ID válido.');
      await this.checked(API.put('/rag/gaps/'+id,{diagnosis:this.value('gap-diagnosis-'+id),notes:this.value('gap-notes-'+id),articleId}));UI.showToast('Revisión editorial guardada.','success');
    }catch(error){this.error(error);}
  },
  async operations() {
    const data=await this.checked(API.get('/rag/operations'));
    this.el('rag-operations').innerHTML=`<p>${UI.escapeHtml(data.window)} · muestra ${Number(data.sampleSize)}</p><div class="rag-status-grid"><div><strong>${data.p50Ms==null?'—':Number(data.p50Ms)+' ms'}</strong>Mediana</div><div><strong>${data.p95Ms==null?'—':Number(data.p95Ms)+' ms'}</strong>Percentil 95</div></div><table><thead><tr><th>Fecha</th><th>Perfil</th><th>Estado</th><th>Total</th><th>Etapas, tokens y coste reportados</th></tr></thead><tbody>${data.runs.map(r=>{
      let metrics;try{metrics=JSON.parse(r.telemetry_json);}catch{metrics={};}
      const flatten=(value,prefix='')=>Object.entries(value).flatMap(([key,v])=>v && typeof v==='object'?flatten(v,prefix+key+'.'):typeof v==='number'?[`${prefix}${key}: ${v}`]:[]);
      return `<tr><td>${UI.escapeHtml(UI.formatDate(r.created_at))}</td><td>${UI.escapeHtml(r.profile_id)}</td><td>${UI.escapeHtml(r.error_code||r.status)}</td><td>${Number(r.duration_ms)} ms</td><td>${UI.escapeHtml(flatten(metrics).join(' · ')||'Sin metadatos del proveedor')}</td></tr>`;
    }).join('')||'<tr><td colspan="5">Todavía no hay búsquedas registradas.</td></tr>'}</tbody></table>`;
  },
  async previewImport() {
    const seq=KNOWLEDGE.editSeq;
    const file=this.el('rag-import-file').files[0];if(!file)throw new Error('Selecciona un PDF con texto.');
    const form=new FormData();form.append('file',file);
    const imported=await this.checked(API.request('/rag/import/preview',{method:'POST',body:form}));
    if(seq!==KNOWLEDGE.editSeq)return;
    this.imported=imported;
    this.el('rag-import-text').value=this.imported.text;
    this.text('rag-import-notice',`${this.imported.filename} · ${this.imported.pages} páginas · SHA-256 ${this.imported.sha256}. ${this.imported.notice}`);
  },
  useImportedText() {
    const preview=this.el('rag-import-text');if(!this.imported)throw new Error('Extrae y revisa el documento primero.');
    const text=preview.selectionEnd>preview.selectionStart?preview.value.slice(preview.selectionStart,preview.selectionEnd):preview.value;
    if(text.length>10000)throw new Error('Selecciona un procedimiento de hasta 10.000 caracteres; no se recortará el manual silenciosamente.');
    this.usedDocument=this.imported;
    this.el('article-solution').value=text;
    if(!this.value('article-title'))this.el('article-title').value=this.imported.filename.replace(/\.pdf$/i,'');
    this.el('article-problem').focus();
  },
  async duplicates() {
    const seq=KNOWLEDGE.editSeq;
    const question=(this.value('article-title')+' '+this.value('article-problem')).trim();if(!question)throw new Error('Escribe título o problema antes de buscar.');
    const result=await this.checked(API.post('/rag/search',{question:question.slice(0,2000),profileId:this.value('rag-profile')||'text',limit:5}));
    if(seq!==KNOWLEDGE.editSeq)return;
    this.renderHits('rag-duplicates',result.hits.filter(h=>h.articleId!==KNOWLEDGE.editingArticleId));
  },
  async resolutionDraft() {
    const id=TICKETS.currentTicket?.id,seq=TICKETS.detailSeq;if(!id)return;
    const modal=UI.modalStack.find(m=>m.id==='modal-ticket-detail');
    const draft=await this.checked(API.get('/rag/tickets/'+id+'/draft'));
    if(TICKETS.activeTicketId!==id||TICKETS.detailSeq!==seq||!UI.modalStack.includes(modal))return;
    if(draft.existingDrafts.length && !confirm('Ya tienes un borrador relacionado. ¿Preparar otro de todas formas?'))return;
    KNOWLEDGE.prepareArticle();KNOWLEDGE.sourceTicketId=id;
    for(const key of ['title','problem','symptoms','cause','solution'])this.el('article-'+key).value=draft[key]||'';
    this.text('rag-import-notice',draft.notice);UI.openModal('modal-create-article');
  },
  async similarTickets() {
    const id=TICKETS.currentTicket?.id,seq=TICKETS.detailSeq;if(!id)return;
    const rows=await this.checked(API.get('/rag/tickets/'+id+'/similar'));
    if(TICKETS.activeTicketId!==id||TICKETS.detailSeq!==seq)return;
    this.el('rag-ticket-related').innerHTML=rows.length?'<p>Coincidencia textual dentro de tu alcance:</p>'+rows.map(r=>`<p><button class="assistant-history-link" type="button" onclick="TICKETS.openTicketDetail(${Number(r.id)})">#${Number(r.id)} · ${UI.escapeHtml(r.title)}</button></p>`).join(''):'<p>No hay incidentes similares dentro de tu alcance.</p>';
  },
  async clarify() {
    const id=TICKETS.currentTicket?.id,seq=TICKETS.detailSeq;
    const question=this.value('assistant-custom-question')||TICKETS.currentTicket?.description||'';
    const facts=this.value('assistant-facts').split('\n').map(s=>s.trim()).filter(Boolean);
    const result=await this.checked(API.post('/rag/clarify',{question,facts}));
    if(TICKETS.activeTicketId!==id||TICKETS.detailSeq!==seq)return;
    if(result.needsClarification)this.text('rag-clarify-feedback',result.prompts.join(' '));
    else {this.el('assistant-custom-question').value=result.question;this.text('rag-clarify-feedback','Pregunta preparada con tus hechos. Revísala antes de consultar.');}
  },
  exportResponse() {
    const q=ASSISTANT.historyById[ASSISTANT.activeQueryId];if(!q)throw new Error('Abre una consulta del historial para exportarla.');
    const text=`# Consulta ${q.id}\n\n${q.question}\n\n${q.answer||q.message}\n\n${(q.recommendedSteps||[]).map((s,i)=>`${i+1}. ${s}`).join('\n')}\n\n## Evidencia\n\n${(q.sources||[]).map(s=>`### ${s.title} · v${s.articleVersion}${s.current===false?' · histórica':''}\n\n${s.evidence}`).join('\n\n')}`;
    const url=URL.createObjectURL(new Blob([text],{type:'text/markdown;charset=utf-8'})),link=document.createElement('a');link.href=url;link.download=`consulta-${Number(q.id)}.md`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  }
};
window.RAG=RAG;
