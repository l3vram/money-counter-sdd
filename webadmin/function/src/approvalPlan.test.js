const { test } = require('node:test');
const assert = require('node:assert');
const { approvalPlan, withGrant } = require('./approvalPlan');

// Los primeros tests que tiene esta Function. Es el código más sensible del proyecto -la
// guardia del SUPERUSER y el alta de cuentas- y hasta el plan 035 no tenía ninguno; los seis
// bugs del 14/09 salieron todos de acá.

const readGrant = (uid) => `read("user:${uid}")`;
const updateGrant = (uid) => `update("user:${uid}")`;

function idSequence() {
  let n = 0;
  return () => `id-${++n}`;
}

function base(overrides = {}) {
  return {
    signupId: 'u1',
    signup: {
      email: 'juan@gmail.com',
      role: 'OWNER',
      businessName: 'Las Pepas',
      branches: ['Calle Marti'],
      status: 'PENDING',
      createdAt: 1000,
    },
    params: {},
    existingMember: null,
    org: null,
    orgBranches: [],
    now: 2000,
    newId: idSequence(),
    readGrant,
    updateGrant,
    ...overrides,
  };
}

const opsFor = (plan, tableId) => plan.operations.filter((o) => o.tableId === tableId);
const opFor = (plan, tableId) => opsFor(plan, tableId)[0];

// ---- Rechazos ----

test('sin fila de signup, 404', () => {
  const plan = approvalPlan(base({ signup: null }));
  assert.strictEqual(plan.ok, false);
  assert.strictEqual(plan.error.statusCode, 404);
  assert.match(plan.error.message, /Solicitud no encontrada/);
});

test('una solicitud ya procesada se rechaza con 400', () => {
  for (const status of ['APPROVED', 'REJECTED']) {
    const plan = approvalPlan(base({ signup: { ...base().signup, status } }));
    assert.strictEqual(plan.ok, false);
    assert.strictEqual(plan.error.statusCode, 400);
    assert.match(plan.error.message, new RegExp(status));
  }
});

test('un status PENDING o ausente sí se aprueba', () => {
  for (const status of ['PENDING', undefined, '']) {
    const plan = approvalPlan(base({ signup: { ...base().signup, status } }));
    assert.strictEqual(plan.ok, true, `status=${status} debería aprobarse`);
  }
});

// ---- DUEÑO ----

test('DUEÑO: crea organización, una sucursal por nombre, y las cuatro filas', () => {
  const plan = approvalPlan(base());
  assert.strictEqual(plan.ok, true);
  assert.strictEqual(plan.orgId, 'id-1');
  assert.deepStrictEqual(plan.branchIds, ['id-2']);
  assert.strictEqual(plan.resumed, false);

  const org = opFor(plan, 'orgs');
  assert.strictEqual(org.action, 'create');
  assert.strictEqual(org.data.name, 'Las Pepas');
  assert.strictEqual(org.data.status, 'ACTIVE');
  assert.deepStrictEqual(org.permissions, ['read("user:u1")']);

  const branch = opFor(plan, 'branches');
  assert.strictEqual(branch.action, 'create');
  assert.strictEqual(branch.data.name, 'Calle Marti');
  assert.strictEqual(branch.data.orgId, 'id-1');

  const member = opFor(plan, 'members');
  assert.strictEqual(member.action, 'upsert');
  assert.deepStrictEqual(member.data, { orgId: 'id-1', role: 'OWNER', branchIds: ['id-2'] });

  const user = opFor(plan, 'users');
  assert.strictEqual(user.action, 'upsert');
  assert.strictEqual(user.data.access, 'APPROVED');
  assert.strictEqual(user.data.email, 'juan@gmail.com');
  assert.deepStrictEqual(user.permissions, ['read("user:u1")', 'update("user:u1")']);

  const signup = opFor(plan, 'signups');
  assert.strictEqual(signup.action, 'update');
  assert.strictEqual(signup.data.status, 'APPROVED');
  assert.strictEqual(signup.data.approvedAt, 2000);
});

test('DUEÑO: varias sucursales, una operación cada una', () => {
  const plan = approvalPlan(
    base({ signup: { ...base().signup, branches: ['Centro', 'Marti', 'Playa'] } })
  );
  const branches = opsFor(plan, 'branches');
  assert.strictEqual(branches.length, 3);
  assert.deepStrictEqual(
    branches.map((b) => b.data.name),
    ['Centro', 'Marti', 'Playa']
  );
  assert.strictEqual(plan.branchIds.length, 3);
});

test('DUEÑO: sin nombre de negocio usa el nombre por defecto', () => {
  for (const businessName of [undefined, null, '', '   ']) {
    const plan = approvalPlan(base({ signup: { ...base().signup, businessName } }));
    assert.strictEqual(opFor(plan, 'orgs').data.name, 'Negocio sin nombre');
  }
});

test('DUEÑO: sin sucursales crea una principal', () => {
  for (const branches of [undefined, null, []]) {
    const plan = approvalPlan(base({ signup: { ...base().signup, branches } }));
    const created = opsFor(plan, 'branches');
    assert.strictEqual(created.length, 1);
    assert.strictEqual(created[0].data.name, 'Sucursal principal');
  }
});

test('DUEÑO que ya tenía organización: la reanuda, no crea otra', () => {
  // Es el bug que originó este plan: tres organizaciones salieron de tres intentos.
  const plan = approvalPlan(
    base({ existingMember: { orgId: 'org-vieja', branchIds: ['br-vieja'] } })
  );
  assert.strictEqual(plan.ok, true);
  assert.strictEqual(plan.resumed, true);
  assert.strictEqual(plan.orgId, 'org-vieja');
  assert.deepStrictEqual(plan.branchIds, ['br-vieja']);
  assert.strictEqual(opsFor(plan, 'orgs').length, 0, 'no debe crear ni tocar organizaciones');
  assert.strictEqual(opsFor(plan, 'branches').length, 0);
});

test('DUEÑO reanudado con branchIds corruptas no explota', () => {
  const plan = approvalPlan(base({ existingMember: { orgId: 'org-vieja', branchIds: null } }));
  assert.strictEqual(plan.ok, true);
  assert.deepStrictEqual(plan.branchIds, []);
});

// ---- VENDEDOR / ADMINISTRADOR ----

function seller(overrides = {}) {
  return base({
    signup: { ...base().signup, role: 'SELLER', businessName: null, branches: [] },
    ...overrides,
  });
}

test('SELLER sin orgId se rechaza', () => {
  const plan = approvalPlan(seller({ params: { branchIds: ['br1'] } }));
  assert.strictEqual(plan.ok, false);
  assert.strictEqual(plan.error.statusCode, 400);
  assert.match(plan.error.message, /Se requiere orgId/);
});

test('SELLER sin sucursales se rechaza', () => {
  for (const branchIds of [undefined, [], null]) {
    const plan = approvalPlan(seller({ params: { orgId: 'org1', branchIds } }));
    assert.strictEqual(plan.ok, false);
    assert.match(plan.error.message, /al menos una branchId/);
  }
});

test('SELLER con organización inexistente se rechaza', () => {
  const plan = approvalPlan(seller({ params: { orgId: 'org1', branchIds: ['br1'] }, org: null }));
  assert.strictEqual(plan.ok, false);
  assert.match(plan.error.message, /Organización no encontrada: org1/);
});

test('SELLER con una sucursal de otra organización se rechaza, nombrando las culpables', () => {
  const plan = approvalPlan(
    seller({
      params: { orgId: 'org1', branchIds: ['br1', 'ajena', 'otra-ajena'] },
      org: { $id: 'org1', $permissions: [] },
      orgBranches: [{ $id: 'br1', $permissions: [] }],
    })
  );
  assert.strictEqual(plan.ok, false);
  assert.match(plan.error.message, /ajena, otra-ajena/);
});

test('SELLER válido: no crea nada, sólo da lectura y escribe las tres filas', () => {
  const plan = approvalPlan(
    seller({
      params: { orgId: 'org1', branchIds: ['br1'] },
      org: { $id: 'org1', $permissions: ['read("user:otro")'] },
      orgBranches: [{ $id: 'br1', $permissions: ['read("user:otro")'] }],
    })
  );
  assert.strictEqual(plan.ok, true);

  const org = opFor(plan, 'orgs');
  assert.strictEqual(org.action, 'update', 'no debe crear la organización');
  assert.deepStrictEqual(
    org.permissions,
    ['read("user:otro")', 'read("user:u1")'],
    'debe preservar el permiso del otro miembro'
  );

  assert.strictEqual(opFor(plan, 'members').data.role, 'SELLER');
  assert.strictEqual(opFor(plan, 'users').data.access, 'APPROVED');
});

test('aprobar dos veces no duplica el permiso de lectura', () => {
  const plan = approvalPlan(
    seller({
      params: { orgId: 'org1', branchIds: ['br1'] },
      org: { $id: 'org1', $permissions: ['read("user:u1")'] },
      orgBranches: [{ $id: 'br1', $permissions: ['read("user:u1")'] }],
    })
  );
  assert.deepStrictEqual(opFor(plan, 'orgs').permissions, ['read("user:u1")']);
});

// ---- La regresión que motivó el plan ----

test('sin fila en users el plan sigue completo', () => {
  // El 14/09 esto era imposible de aprobar: el updateRow sobre `users` daba 404 y mataba la
  // aprobación DESPUÉS de crear la organización. El upsert es lo que lo arregla, y este test
  // lo fija.
  const plan = approvalPlan(base());
  const user = opFor(plan, 'users');
  assert.strictEqual(user.action, 'upsert', 'debe ser upsert, no update');
  assert.strictEqual(user.data.createdAt, 1000, 'conserva el createdAt del signup');
});

test('sin createdAt en el signup usa la hora actual', () => {
  const plan = approvalPlan(base({ signup: { ...base().signup, createdAt: undefined } }));
  assert.strictEqual(opFor(plan, 'users').data.createdAt, 2000);
});

// ---- withGrant ----

test('withGrant agrega, preserva e es idempotente', () => {
  assert.deepStrictEqual(withGrant([], 'g'), ['g']);
  assert.deepStrictEqual(withGrant(['a'], 'g'), ['a', 'g']);
  assert.deepStrictEqual(withGrant(['g'], 'g'), ['g']);
  assert.deepStrictEqual(withGrant(undefined, 'g'), ['g']);
  assert.deepStrictEqual(withGrant(null, 'g'), ['g']);
});
