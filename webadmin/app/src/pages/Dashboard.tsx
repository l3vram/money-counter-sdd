import { useEffect, useState } from 'react';
import { listBranches, listOrgs, listSignups, listUsers } from '../api';
import { errMsg } from '../types';

export default function Dashboard() {
  const [counts, setCounts] = useState({
    pendingSignups: 0,
    orgs: 0,
    branches: 0,
    users: 0,
  });
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    (async () => {
      setError('');
      try {
        const [signups, orgs, branches, users] = await Promise.all([
          listSignups('PENDING'),
          listOrgs(),
          listBranches(),
          listUsers(),
        ]);
        if (!active) return;
        setCounts({
          pendingSignups: signups.total,
          orgs: orgs.total,
          branches: branches.total,
          users: users.total,
        });
      } catch (e) {
        if (active) setError(errMsg(e));
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  return (
    <div>
      <h1 className="page-title">Panel general</h1>
      <p className="page-sub">Resumen de la plataforma El Luiso</p>
      {error && <div className="error-box">{error}</div>}
      <div className="count-cards">
        <div className="count-card">
          <div className="num">{counts.pendingSignups}</div>
          <div className="label">Solicitudes pendientes</div>
        </div>
        <div className="count-card">
          <div className="num">{counts.orgs}</div>
          <div className="label">Organizaciones</div>
        </div>
        <div className="count-card">
          <div className="num">{counts.branches}</div>
          <div className="label">Sucursales</div>
        </div>
        <div className="count-card">
          <div className="num">{counts.users}</div>
          <div className="label">Usuarios registrados</div>
        </div>
      </div>
      <div className="info-box">
        Usa «Solicitudes» para aprobar o rechazar los registros pendientes. La autorización
        se valida en el servidor (la función solo permite operaciones a superusuarios).
      </div>
    </div>
  );
}