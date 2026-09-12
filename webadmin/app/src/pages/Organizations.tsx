import { useCallback, useEffect, useState } from 'react';
import { listOrgs } from '../api';
import { errMsg, formatDate, type OrgRow } from '../types';

export default function Organizations() {
  const [orgs, setOrgs] = useState<OrgRow[]>([]);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      const result = await listOrgs();
      setOrgs(result.rows);
    } catch (e) {
      setError(errMsg(e));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div>
      <h1 className="page-title">Organizaciones</h1>
      <p className="page-sub">Listado de organizaciones de la plataforma (solo lectura).</p>
      {error && <div className="error-box">{error}</div>}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>WhatsApp</th>
              <th>Estado</th>
              <th>Creada</th>
            </tr>
          </thead>
          <tbody>
            {orgs.length === 0 && (
              <tr>
                <td colSpan={4} className="empty">
                  No hay organizaciones.
                </td>
              </tr>
            )}
            {orgs.map((o) => (
              <tr key={o.$id}>
                <td>{o.name}</td>
                <td>{o.whatsappNumber || '—'}</td>
                <td>
                  <span className={`badge ${o.status.toLowerCase()}`}>{o.status}</span>
                </td>
                <td>{formatDate(o.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}