import { useCallback, useEffect, useState } from 'react';
import { listUsers, resetPassword } from '../api';
import { errMsg, formatDate, ROLE_LABEL, type UserRow } from '../types';

export default function Users() {
  const [users, setUsers] = useState<UserRow[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      const result = await listUsers();
      setUsers(result.rows);
    } catch (e) {
      setError(errMsg(e));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleReset = async (user: UserRow) => {
    const password = window.prompt(
      `Nueva contraseña temporal para ${user.email} (mínimo 8 caracteres):`,
    );
    if (password === null) return;
    if (password.length < 8) {
      setMessage('');
      setError('La contraseña debe tener al menos 8 caracteres.');
      return;
    }
    setBusyId(user.id);
    setError('');
    setMessage('');
    try {
      await resetPassword(user.id, password);
      setMessage(`Contraseña restablecida para ${user.email}. Deberá cambiarla al entrar.`);
      await load();
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <h1 className="page-title">Usuarios</h1>
      <p className="page-sub">Usuarios registrados en Appwrite y su estado en la plataforma.</p>
      {error && <div className="error-box">{error}</div>}
      {message && <div className="success-box">{message}</div>}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Correo</th>
              <th>Nombre</th>
              <th>Acceso</th>
              <th>Rol</th>
              <th>Registrado</th>
              <th>Contraseña</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {users.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No hay usuarios registrados.
                </td>
              </tr>
            )}
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.email}</td>
                <td>{u.displayName || '—'}</td>
                <td>
                  <span className={`badge ${String(u.access || '').toLowerCase()}`}>
                    {u.access || '—'}
                  </span>
                </td>
                <td>
                  {u.role ? <span className="badge role">{ROLE_LABEL[u.role] ?? u.role}</span> : '—'}
                </td>
                <td>{formatDate(u.createdAt)}</td>
                <td>
                  {u.mustChangePassword ? (
                    <span className="badge pending">Debe cambiar</span>
                  ) : (
                    <span className="badge approved">Cumplida</span>
                  )}
                </td>
                <td>
                  <button
                    className="btn btn-sm btn-secondary"
                    disabled={busyId !== null}
                    onClick={() => handleReset(u)}
                  >
                    Restablecer contraseña
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}