const test = require('node:test');
const assert = require('node:assert/strict');
const workflow = require('../Flujo-3-Herramientas-Conocimiento.json');
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
const point = '12345678-1234-1234-1234-123456789012';
const secret = 'test-service-token-0123456789abcdef';
const base = { schemaVersion: 2, requestId: point, operation: 'SEARCH', question: 'error 809',
  profile: { id: 'test', collection_name: 'rag_synapse_test', model: 'test', dimension: 2, strategy: 'HYBRID', min_cosine: .55 },
  limit: 5, allowedPointIds: [point], lexicalCandidates: [{ pointId: point }] };

async function run(name, input, nodes = {}, body = base) {
  nodes = { 'Validar V2': body, ...nodes };
  const code = workflow.nodes.find(n => n.name === name).parameters.jsCode;
  const fn = new AsyncFunction('$json', '$', '$input', '$env', code);
  const result = await fn(input, name => ({ first: () => ({ json: nodes[name] }) }),
    { first: () => ({ json: input }) }, { N8N_SERVICE_TOKEN: secret });
  return result[0].json;
}

test('all Code nodes compile and every connection refers to an existing node', () => {
  const names = new Set(workflow.nodes.map(n => n.name));
  assert.equal(names.size, workflow.nodes.length);
  for (const n of workflow.nodes) if (n.type.endsWith('.code')) new AsyncFunction('$json', '$', '$input', '$env', n.parameters.jsCode);
  for (const n of workflow.nodes) if (n.type.endsWith('.webhook')) assert.equal(n.webhookId, n.parameters.path);
  for (const [from, types] of Object.entries(workflow.connections)) {
    assert(names.has(from));
    for (const outputs of Object.values(types)) for (const links of outputs) for (const link of links) assert(names.has(link.node));
  }
});
test('service authentication and collection boundaries fail closed', async () => {
  assert.equal((await run('Validar V2', { body: base, headers: {} })).httpStatus, 401);
  const valid = await run('Validar V2', { body: base, headers: { 'x-service-token': secret } });
  assert.equal(valid.operation, 'SEARCH');
  assert.equal((await run('Validar V2', { body: { ...base, profile: { ...base.profile, collection_name: 'production' } }, headers: { 'x-service-token': secret } })).httpStatus, 400);
});
test('wrong embedding dimensions and incompatible collections are rejected', async () => {
  assert.equal((await run('Validar vector', { body: { data: [{ embedding: [.1] }] } })).reply.errorCode, 'EMBEDDING_FAILED');
  const collectionValidator = workflow.nodes.find(n => n.parameters.jsCode?.includes('create:true'));
  const missing = await run(collectionValidator.name, { statusCode: 404 });
  assert.deepEqual(missing.collectionBody, { vectors: { size: base.profile.dimension, distance: 'Cosine' } });
  const createCollection = workflow.nodes.find(n => n.parameters.method === 'PUT' && n.parameters.url?.includes('/collections/') && !n.parameters.url.includes('/points?'));
  assert.equal(createCollection.parameters.jsonBody, '={{ JSON.stringify($json.collectionBody) }}');
  const upsertPoints = workflow.nodes.find(n => n.parameters.method === 'PUT' && n.parameters.url?.includes('/points?'));
  assert.equal(upsertPoints.parameters.jsonBody, '={{ JSON.stringify($json.pointsBody) }}');
  assert.equal((await run('Validar colección', { body: { result: { config: { params: { vectors: { size: 3, distance: 'Cosine' } } } } } })).reply.errorCode, 'INDEX_MODEL_MISMATCH');
  assert.equal((await run('Validar colección', { statusCode: 404 })).create, true);
});
test('hybrid RRF retains literal evidence with low cosine and excludes unapproved points', async () => {
  const nodes = { 'Preparar evidencia': { dense: [{ pointId: point, cosine: .1 }, { pointId: 'foreign', cosine: .99 }], searchMs: 1, eligibilityStart: Date.now() },
    'Preparar recuperación': { embeddingMs: 1, embeddingTokens: 10 } };
  const result = await run('Fusionar candidatos', { body: [{ pointId: point, articleId: 1, title: 'VPN', text: 'error 809' }] }, nodes);
  assert.equal(result.hits.length, 1);
  assert.equal(result.hits[0].cosine, .1);
  assert.equal(result.hits[0].rankingScore, 2 / 61);
});
test('reranker cannot refer to another candidate or duplicate an index', async () => {
  const nodes = { 'Fusionar candidatos': { hits: [{ pointId: point }], telemetry: {}, rerankStart: Date.now() } };
  assert.equal((await run('Validar reranking', { body: { results: [{ index: 9, relevance_score: .9 }] } }, nodes)).reply.errorCode, 'INVALID_RERANK');
  assert.equal((await run('Validar reranking', { body: { results: [{ index: 0, relevance_score: .9 }, { index: 0, relevance_score: .8 }] } }, nodes)).reply.errorCode, 'RERANK_FAILED');
});
test('generation validates citations and distinguishes insufficient evidence from technical error', async () => {
  const body = { ...base, operation: 'GENERATE', sources: [{ pointId: point, evidence: 'Procedimiento', title: 'VPN' }] };
  const nodes = { 'Preparar generación': { generationStart: Date.now(), generationBody: { model: 'test' } } };
  const response = content => ({ body: { choices: [{ message: { content: JSON.stringify(content) } }], usage: { cost: .001 } } });
  assert.equal((await run('Respuesta generada', response({ answer: 'Texto', steps: [{ text: 'Paso', citations: ['foreign'] }] }), nodes, body)).reply.errorCode, 'INVALID_CITATION');
  const valid = (await run('Respuesta generada', response({ answer: 'Texto', steps: [{ text: 'Paso', citations: [point] }] }), nodes, body)).reply;
  assert.equal(valid.status, 'OK'); assert.equal(valid.telemetry.reportedCostUsd, .001);
  assert.equal((await run('Respuesta generada', response({ insufficient: true }), nodes, body)).reply.insufficient, true);
  assert.equal((await run('Respuesta generada', { statusCode: 503 }, nodes, body)).reply.errorCode, 'GENERATION_FAILED');
});
test('physical audit treats absent collection as empty and deletes only validated UUIDs', async () => {
  assert.equal((await run('Respuesta auditoría', { statusCode: 404 })).reply.collectionExists, false);
  assert.equal((await run('Preparar retiro', {}, {}, { ...base, operation: 'DELETE', pointIds: ['*'] })).httpStatus, 400);
  assert.deepEqual((await run('Preparar retiro', {}, {}, { ...base, operation: 'DELETE', pointIds: [point] })).deleteBody.points, [point]);
});
