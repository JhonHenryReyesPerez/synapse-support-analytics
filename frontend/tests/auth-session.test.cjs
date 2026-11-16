const test = require('node:test');
const assert = require('node:assert/strict');
const { loadFrontend } = require('./harness.js');

function session(profileResponse, mustChangePassword = false) {
  return loadFrontend(['auth.js'], {
    API: {
      post: async () => ({ ok: true, data: { token: 'new-token', mustChangePassword } }),
      get: async () => profileResponse
    }
  });
}

test('a failed profile request clears provisional token and stale user', async () => {
  const ctx = session({ ok: false, status: 503, error: 'Servicio no disponible' });
  ctx.localStorage.setItem('user', JSON.stringify({ username: 'old-admin', role: 'ADMIN' }));
  const result = await ctx.AUTH.login('admin', 'password');
  assert.equal(result.ok, false);
  assert.equal(result.status, 503);
  assert.equal(result.error, 'Servicio no disponible');
  assert.equal(ctx.AUTH.getToken(), null);
  assert.equal(ctx.AUTH.getUser(), null);
});

test('a successful HTTP response with an incomplete profile does not establish a session', async () => {
  for (const data of [null, {}, { username: 'admin' }, { role: 'ADMIN' }]) {
    const ctx = session({ ok: true, status: 200, data });
    assert.equal((await ctx.AUTH.login('admin', 'password')).ok, false);
    assert.equal(ctx.AUTH.isAuthenticated(), false);
    assert.equal(ctx.AUTH.getUser(), null);
  }
});

test('a verified profile retains role and mandatory password change', async () => {
  for (const mustChangePassword of [false, true]) {
    const ctx = session({ ok: true, data: { username: 'admin', role: 'ADMIN' } }, mustChangePassword);
    const result = await ctx.AUTH.login('admin', 'password');
    assert.equal(result.ok, true);
    assert.equal(result.mustChangePassword, mustChangePassword);
    assert.equal(ctx.AUTH.getToken(), 'new-token');
    assert.equal(ctx.AUTH.getUser().role, 'ADMIN');
    assert.equal(ctx.AUTH.getUser().mustChangePassword, mustChangePassword);
  }
});
