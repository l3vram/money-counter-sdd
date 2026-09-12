import { useCallback, useEffect, useState } from 'react';
import { listBranches, listOrgs } from '../api';
import { errMsg, formatDate, type BranchRow } from '../types';

export default function Branches() {
  const [branches, setBranches] = useState<BranchRow[]>([]);
  const [orgNames, setOrgNames] = useState<Record<string, string>>({});
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      const [b, o] = await Promise.all([listBranches(), listOrgs()]);
      setBranches(b.rows);
      setOrgNames(Object.fromEntries(o.rows.map((org) => [org.$id, org.name])));
    } catch (e) {
      setError(errMsg(e));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div>
      <h1 className="page-title">Sucursales</h1>
      <p className="page-sub">Listado de sucursales de la plataforma (solo lectura).</p>
      {error && <div className="error-box">{error}</div>}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Organización</th>
              <th>Estado</th>
              <th>Creada</th>
            </tr>
          </thead>
          <tbody>
            {branches.length === 0 && (
              <tr>
                <td colSpan={4} className="empty">
                  No hay sucursales.
                </td>
              </tr>
            )}
            {branches.map((b) => (
              <tr key={b.$id}>
                <td>{b.name}</td>
                <td>{orgNames[b.orgId] ?? b.orgId}</td>
                <td>
                  <span className={`badge ${b.status.toLowerCase()}`}>{b.status}</span>
                </td>
                <td>{formatDate(b.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}