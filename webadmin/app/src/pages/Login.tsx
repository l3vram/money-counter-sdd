import { useState } from 'react';
import { login } from '../api';
import { errMsg, isAccessDenied, type UserInfo } from '../types';

interface Props {
  onLoggedIn: (me: UserInfo) => void;
  onDenied: (message: string) => void;
}

export default function Login({ onLoggedIn, onDenied }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError('Ingresa correo y contraseña.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const me = await login(email.trim(), password);
      onLoggedIn(me);
    } catch (err) {
      if (isAccessDenied(err)) {
        onDenied(errMsg(err));
      } else if (String(err).toLowerCase().includes('invalid credentials')) {
        setError('Correo o contraseña incorrectos.');
      } else {
        setError(errMsg(err));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-screen">
      <div className="brand">Panel Admin — El Luiso</div>
      <div className="brand-sub">Acceso restringido a superusuarios</div>
      <form className="card auth-card" onSubmit={handleSubmit}>
        <h1>Iniciar sesión</h1>
        {error && <div className="error-box">{error}</div>}
        <div className="field">
          <label htmlFor="email">Correo electrónico</label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="password">Contraseña</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        <button className="btn btn-block" type="submit" disabled={busy}>
          {busy ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </div>
  );
}