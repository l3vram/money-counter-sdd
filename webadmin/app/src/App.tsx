import { useEffect, useState } from 'react';
import { clearAuth, logout, whoami } from './api';
import { errMsg, isAccessDenied, type UserInfo } from './types';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Signups from './pages/Signups';
import Users from './pages/Users';
import Organizations from './pages/Organizations';
import Branches from './pages/Branches';
import Settings from './pages/Settings';

type Page = 'loading' | 'login' | 'denied' | 'app';
type View = 'dashboard' | 'signups' | 'users' | 'organizations' | 'branches' | 'settings';

const VIEWS: { id: View; label: string }[] = [
  { id: 'dashboard', label: 'Panel' },
  { id: 'signups', label: 'Solicitudes' },
  { id: 'users', label: 'Usuarios' },
  { id: 'organizations', label: 'Organizaciones' },
  { id: 'branches', label: 'Sucursales' },
  { id: 'settings', label: 'Configuración' },
];

export default function App() {
  const [page, setPage] = useState<Page>('loading');
  const [view, setView] = useState<View>('dashboard');
  const [user, setUser] = useState<UserInfo | null>(null);
  const [deniedMsg, setDeniedMsg] = useState('');

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const me = await whoami();
        if (!active) return;
        setUser(me);
        setPage('app');
      } catch (e) {
        if (!active) return;
        if (isAccessDenied(e)) {
          setDeniedMsg(errMsg(e));
          setPage('denied');
        } else {
          setPage('login');
        }
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  const handleLoggedIn = (me: UserInfo) => {
    setUser(me);
    setView('dashboard');
    setPage('app');
  };

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
      clearAuth();
      setUser(null);
      setPage('login');
    }
  };

  if (page === 'loading') {
    return <div className="app-loading">Cargando…</div>;
  }

  if (page === 'denied') {
    return (
      <div className="auth-screen">
        <div className="brand">Panel Admin — El Luiso</div>
        <div className="card auth-card">
          <h1>Acceso denegado</h1>
          <p className="error-box" style={{ marginBottom: 12 }}>
            {deniedMsg || 'No tienes permisos de superusuario para acceder a este panel.'}
          </p>
          <p style={{ color: 'var(--muted)', fontSize: 14 }}>
            Si crees que es un error, contacta al administrador de la plataforma.
          </p>
          <button className="btn btn-block" onClick={handleLogout}>
            Cerrar sesión
          </button>
        </div>
      </div>
    );
  }

  if (page === 'login') {
    return <Login onLoggedIn={handleLoggedIn} onDenied={setDeniedMsg} />;
  }

  return (
    <div className="layout">
      <header className="topbar">
        <div className="logo">Panel Admin — El Luiso</div>
        <nav className="nav">
          {VIEWS.map((v) => (
            <button
              key={v.id}
              className={view === v.id ? 'active' : ''}
              onClick={() => setView(v.id)}
            >
              {v.label}
            </button>
          ))}
        </nav>
        <div className="spacer" />
        <span className="user">{user?.email ?? ''}</span>
        <button className="btn btn-secondary btn-sm" onClick={handleLogout}>
          Salir
        </button>
      </header>
      <main className="content">
        {view === 'dashboard' && <Dashboard />}
        {view === 'signups' && <Signups />}
        {view === 'users' && <Users />}
        {view === 'organizations' && <Organizations />}
        {view === 'branches' && <Branches />}
        {view === 'settings' && <Settings />}
      </main>
    </div>
  );
}