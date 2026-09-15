const { test } = require('node:test');
const assert = require('node:assert');
const { runOperations } = require('./transaction');

// Un tablesDB falso que registra llamadas. Sin red y sin dependencias: el ejecutor se prueba
// entero, incluido el camino de rollback, que es el que nunca se ejercita en producción hasta
// el día que hace falta.
function fakeTablesDB(options = {}) {
  const calls = [];
  const failOn = options.failOn ?? null;
  const rollbackThrows = options.rollbackThrows ?? false;
  let staged = 0;

  return {
    calls,
    createTransaction(params) {
      calls.push({ call: 'createTransaction', params });
      return Promise.resolve({ $id: 'tx-1' });
    },
    updateTransaction(params) {
      calls.push({ call: 'updateTransaction', params });
      if (params.rollback && rollbackThrows) {
        return Promise.reject(new Error('el rollback tambien falló'));
      }
      return Promise.resolve({ $id: 'tx-1' });
    },
    createRow: row('createRow'),
    updateRow: row('updateRow'),
    upsertRow: row('upsertRow'),
    deleteRow: row('deleteRow'),
  };

  function row(name) {
    return (params) => {
      calls.push({ call: name, params });
      staged++;
      if (failOn !== null && staged === failOn) {
        return Promise.reject(new Error('fallo al escribir la fila'));
      }
      return Promise.resolve({ $id: params.rowId });
    };
  }
}

const ops = [
  { action: 'create', tableId: 'orgs', rowId: 'o1', data: { name: 'x' }, permissions: ['p'] },
  { action: 'upsert', tableId: 'members', rowId: 'u1', data: { role: 'OWNER' } },
  { action: 'update', tableId: 'signups', rowId: 'u1', data: { status: 'APPROVED' } },
];

const names = (db) => db.calls.map((c) => c.call);

test('todas las operaciones se etapan con el id de la transacción, y commitea una vez', async () => {
  const db = fakeTablesDB();
  await runOperations(db, 'main', ops);

  assert.deepStrictEqual(names(db), [
    'createTransaction',
    'createRow',
    'upsertRow',
    'updateRow',
    'updateTransaction',
  ]);
  for (const c of db.calls.filter((x) => x.call.endsWith('Row'))) {
    assert.strictEqual(c.params.transactionId, 'tx-1', `${c.call} debe ir en la transacción`);
    assert.strictEqual(c.params.databaseId, 'main');
  }
  const commits = db.calls.filter((c) => c.call === 'updateTransaction');
  assert.strictEqual(commits.length, 1);
  assert.deepStrictEqual(commits[0].params, { transactionId: 'tx-1', commit: true });
});

test('un fallo a mitad revierte y NO commitea', async () => {
  // El caso que motivó el plan: la aprobación moría después de crear la organización.
  const db = fakeTablesDB({ failOn: 2 });

  await assert.rejects(() => runOperations(db, 'main', ops), /fallo al escribir la fila/);

  const tx = db.calls.filter((c) => c.call === 'updateTransaction');
  assert.strictEqual(tx.length, 1);
  assert.deepStrictEqual(tx[0].params, { transactionId: 'tx-1', rollback: true });
  assert.ok(
    !db.calls.some((c) => c.call === 'updateTransaction' && c.params.commit),
    'no debe commitear'
  );
  assert.ok(!names(db).includes('updateRow'), 'no debe seguir etapando después del fallo');
});

test('si el rollback también falla, el error que sale es el ORIGINAL', async () => {
  // Enmascararlo con el error del rollback esconde la causa real, que es justo lo que hizo
  // difícil de leer el bug de las organizaciones duplicadas.
  const db = fakeTablesDB({ failOn: 1, rollbackThrows: true });
  const registrados = [];

  await assert.rejects(
    () => runOperations(db, 'main', ops, (m) => registrados.push(m)),
    /fallo al escribir la fila/
  );

  assert.strictEqual(registrados.length, 1);
  assert.match(registrados[0], /No se pudo revertir la transaccion tx-1/);
});

test('una lista vacía no abre transacción', async () => {
  for (const vacio of [[], null, undefined]) {
    const db = fakeTablesDB();
    await runOperations(db, 'main', vacio);
    assert.deepStrictEqual(db.calls, [], 'no debería llamar a nada');
  }
});

test('no manda permissions ni data cuando no vienen', async () => {
  // Mandar `permissions: undefined` en un update se lee como "sin permisos" y borraría los
  // grants de la fila.
  const db = fakeTablesDB();
  await runOperations(db, 'main', [{ action: 'update', tableId: 'orgs', rowId: 'o1' }]);

  const update = db.calls.find((c) => c.call === 'updateRow');
  assert.ok(!('permissions' in update.params), 'no debe incluir permissions');
  assert.ok(!('data' in update.params), 'no debe incluir data');
});

test('una acción desconocida falla y revierte', async () => {
  const db = fakeTablesDB();
  await assert.rejects(
    () => runOperations(db, 'main', [{ action: 'invent', tableId: 't', rowId: 'r' }]),
    /Operacion desconocida: invent/
  );
  assert.deepStrictEqual(db.calls.at(-1).params, { transactionId: 'tx-1', rollback: true });
});

test('la transacción se abre con un TTL', async () => {
  const db = fakeTablesDB();
  await runOperations(db, 'main', ops);
  const abrir = db.calls[0];
  assert.strictEqual(abrir.call, 'createTransaction');
  assert.ok(abrir.params.ttl > 0, 'debe pasar un ttl');
});
