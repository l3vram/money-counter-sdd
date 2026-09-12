import { useCallback, useEffect, useState } from 'react';
import { approve, listBranches, listOrgs, listSignups, reject } from '../api';
import {
  errMsg,
  formatDate,
  ROLE_LABEL,
  type BranchRow,
  type OrgRow,
  type SignupRow,
  type SignupStatus,
} from '../types';

type Filter = SignupStatus | 'ALL';

export default function Signups() {
  const [filter, setFilter] = useState<Filter>('PENDING');
  const [signups, setSignups] = useState<SignupRow[]>([]);
  const [orgs, setOrgs] = useState<OrgRow[]>([]);
  const [branches, setBranches] = useState<BranchRow[]>([]);
  const [animatedOpenFor, setAnimatedOpenFor] = useState<string | null>(null);
  const [selectedOrg, setSelectedOrg] = useState('');
  const [selectedBranches, setSelectedBranches] = useState<string[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(async (status: Filter) => {
    setError('');
    try {
      const result = await listSignups(status === 'ALL' ? 'PENDING' : status);
      setSignups(result.rows);
    } catch (e) {
      setError(errMsg(e));
    }
  }, []);

  useEffect(() => {
    load(filter);
  }, [filter, load]);

  useEffect(() => {
    (async () => {
      try {
        const [o, b] = await Promise.all([listOrgs(), listBranches()]);
        setOrgs(o.rows);
        setBranches(b.rows);
      } catch (e) {
        setError(errMsg(e));
      }
    })();
  }, []);

  const branchesOf = (orgId: string) => branches.filter((b) => b.orgId === orgId);

  const toggleBranch = (id: string) => {
    setSelectedBranches((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  };

  const handleOrgChange = (orgId: string) => {
    setSelectedOrg(orgId);
    setSelectedBranches([]);
  };

  const doApprove = async (
    signup: SignupRow,
    overrides: { orgId?: string; branchIds?: string[] } = {},
  ) => {
    setMessage('');
    setError('');
    setBusyId(signup.$id);
    try {
      const result = await approve(signup.$id, overrides);
      setMessage(
        `Se aprobó a ${signup.email}. Organización: ${result.orgId}, sucursales: ${result.branchIds.length}.`,
      );
      setAnimatedOpenFor(null);
      setSelectedOrg('');
      setSelectedBranches([]);
      await load(filter);
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setBusyId(null);
    }
  };

  const handleApprove = (signup: SignupRow) => {
    if (signup.role === 'OWNER') {
      const ok = window.confirm(
        `¿Aprobar a ${signup.email} (${ROLE_LABEL[signup.role]})?\n\nSe creará la organización «${signup.businessName ?? '—'}» con sus sucursales.`,
      );
      if (ok) void doApprove(signup);
      return;
    }
    setMessage('');
    setError('');
    setAnimatedOpenFor((prev) => (prev === signup.$id ? null : signup.$id));
  };

  const handleReject = async (signup: SignupRow) => {
    const ok = window.confirm(
      `¿Rechazar la solicitud de ${signup.email}? El correo podrá seguir registrándose y el acceso seguirá en PENDIENTE.`,
    );
    if (!ok) return;
    setMessage('');
    setError('');
    setBusyId(signup.$id);
    try {
      await reject(signup.$id);
      setMessage(`Solicitud de ${signup.email} rechazada.`);
      await load(filter);
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <h1 className="page-title">Solicitudes de registro</h1>
      <p className="page-sub">
        Aprobar un DUEÑO crea su organización y sucursales. ADMIN/VENDEDOR se asignan a una
        organización y sucursales existentes.
      </p>
      {error && <div className="error-box">{error}</div>}
      {message && <div className="success-box">{message}</div>}
      <div className="toolbar">
        {(['PENDING', 'APPROVED', 'REJECTED', 'ALL'] as Filter[]).map((f) => (
          <button
            key={f}
            className={`btn btn-sm ${filter === f ? '' : 'btn-secondary'}`}
            onClick={() => setFilter(f)}
          >
            {f === 'PENDING' && 'Pendientes'}
            {f === 'APPROVED' && 'Aprobadas'}
            {f === 'REJECTED' && 'Rechazadas'}
            {f === 'ALL' && 'Todas'}
          </button>
        ))}
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Correo</th>
              <th>Rol</th>
              <th>Negocio</th>
              <th>Sucursales</th>
              <th>Recibida</th>
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {signups.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No hay solicitudes con este estado.
                </td>
              </tr>
            )}
            {signups.map((s) => (
              <tr key={s.$id}>
                <td>{s.email}</td>
                <td>
                  <span className="badge role">{ROLE_LABEL[s.role]}</span>
                </td>
                <td>{s.businessName || '—'}</td>
                <td>{s.branches && s.branches.length ? s.branches.join(', ') : '—'}</td>
                <td>{formatDate(s.createdAt)}</td>
                <td>
                  <span className={`badge ${s.status.toLowerCase()}`}>{s.status}</span>
                </td>
                <td>
                  <div className="row-actions">
                    {s.status === 'PENDING' && (
                      <>
                        <button
                          className="btn btn-sm"
                          disabled={busyId !== null}
                          onClick={() => handleApprove(s)}
                        >
                          Aprobar
                        </button>
                        <button
                          className="btn btn-sm btn-danger"
                          disabled={busyId !== null}
                          onClick={() => handleReject(s)}
                        >
                          Rechazar
                        </button>
                      </>
                    )}
                  </div>
                  {animatedOpenFor === s.$id && s.status === 'PENDING' && s.role !== 'OWNER' && (
                    <div className="approve-panel">
                      <div className="field">
                        <label htmlFor={`org-${s.$id}`}>Organización</label>
                        <select
                          id={`org-${s.$id}`}
                          value={selectedOrg}
                          onChange={(e) => handleOrgChange(e.target.value)}
                        >
                          <option value="">Selecciona…</option>
                          {orgs
                            .filter((o) => o.status === 'ACTIVE')
                            .map((o) => (
                              <option key={o.$id} value={o.$id}>
                                {o.name}
                              </option>
                            ))}
                        </select>
                      </div>
                      <div className="checks">
                        {selectedOrg ? (
                          branchesOf(selectedOrg).map((b) => (
                            <label key={b.$id}>
                              <input
                                type="checkbox"
                                checked={selectedBranches.includes(b.$id)}
                                onChange={() => toggleBranch(b.$id)}
                              />
                              {b.name}
                            </label>
                          ))
                        ) : (
                          <span className="empty">Primero elige una organización.</span>
                        )}
                      </div>
                      <div className="row-actions">
                        <button
                          className="btn btn-sm"
                          disabled={
                            busyId !== null ||
                            !selectedOrg ||
                            selectedBranches.length === 0
                          }
                          onClick={() =>
                            void doApprove(s, {
                              orgId: selectedOrg,
                              branchIds: selectedBranches,
                            })
                          }
                        >
                          Confirmar aprobación
                        </button>
                        <button
                          className="btn btn-sm btn-secondary"
                          onClick={() => setAnimatedOpenFor(null)}
                        >
                          Cancelar
                        </button>
                      </div>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}