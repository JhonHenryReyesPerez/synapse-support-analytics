const test = require('node:test');
const assert = require('node:assert/strict');
const { loadFrontend, deferred } = require('./harness');
const ok = data => ({ ok:true,data });
function setup(api={}) {
  const ctx=loadFrontend(['ui.js','knowledge.js','rag.js'],{API:api,AUTH:{getUser:()=>({username:'ana',role:'ADMIN'})}});
  return {ctx,el:id=>ctx.document.getElementById(id)};
}
test('RAG search escapes evidence and discards a late result from an older query',async()=>{
  const first=deferred();let n=0;
  const {ctx,el}=setup({post:()=>++n===1?first.promise:ok({hits:[],strategy:'TEXT'})});
  el('rag-question').value='primera';const pending=ctx.RAG.search();
  el('rag-question').value='segunda';await ctx.RAG.search();
  first.resolve(ok({hits:[{articleId:1,title:'antigua'}],strategy:'TEXT'}));await pending;
  assert(!el('rag-search-results').innerHTML.includes('antigua'));
  ctx.RAG.renderHits('rag-search-results',[{articleId:1,version:1,rank:1,metric:'term_coverage',rankingScore:1,title:'<img onerror=x>',section:'<script>',evidence:'<script>alert(1)</script>'}]);
  assert(!el('rag-search-results').innerHTML.includes('<script>'));
  assert(el('rag-search-results').innerHTML.includes('&lt;script&gt;'));
});
test('failed metadata save preserves the new article identity so retry does PUT',async()=>{
  const calls=[];
  const {ctx,el}=setup({post:async(url)=>{calls.push(['POST',url]);return ok({id:42});},put:async(url)=>{
    calls.push(['PUT',url]);return url.startsWith('/rag/')?{ok:false,error:'unavailable'}:ok({id:42});
  }});
  for(const field of ['title','problem','symptoms','cause','solution'])el('article-'+field).value='Contenido';
  await ctx.KNOWLEDGE.handleCreateArticle();await ctx.KNOWLEDGE.handleCreateArticle();
  assert.equal(ctx.KNOWLEDGE.editingArticleId,42);
  assert.equal(calls.filter(([method])=>method==='POST').length,1);
  assert(calls.some(([method,url])=>method==='PUT'&&url==='/knowledge/articles/42'));
  assert(ctx.toasts.some(t=>t.message.includes('contenido está guardado')));
});
test('duplicate suggestions from a previous editor cannot replace the current editor',async()=>{
  const pending=deferred();const {ctx,el}=setup({post:()=>pending.promise});
  el('article-title').value='VPN';ctx.KNOWLEDGE.editSeq=1;
  const searching=ctx.RAG.duplicates();ctx.KNOWLEDGE.editSeq=2;
  el('rag-duplicates').innerHTML='Editor actual';pending.resolve(ok({hits:[]}));await searching;
  assert.equal(el('rag-duplicates').innerHTML,'Editor actual');
});
test('knowledge pagination requests the next page and enforces boundaries',async()=>{
  const calls=[];const {ctx,el}=setup({get:async url=>{calls.push(url);return ok({content:[],totalPages:3,totalElements:41});}});
  ctx.KNOWLEDGE.totalPages=3;
  await ctx.KNOWLEDGE.changePage(1);
  assert.equal(calls[0],'/knowledge/articles?scope=PUBLISHED&page=1&size=20');
  assert(el('kb-page-label').textContent.includes('Página 2 de 3'));
  await ctx.KNOWLEDGE.changePage(9);assert.equal(calls.length,1);
});
test('a closed incident does not reopen an article draft after a late response',async()=>{
  const pending=deferred();const {ctx}=setup({get:()=>pending.promise});
  ctx.TICKETS={currentTicket:{id:1},activeTicketId:1,detailSeq:1};
  ctx.UI.modalStack=[{id:'modal-ticket-detail'}];
  const opening=ctx.RAG.resolutionDraft();ctx.UI.modalStack=[];
  pending.resolve(ok({existingDrafts:[]}));await opening;
  assert.equal(ctx.UI.modalStack.length,0);
});
