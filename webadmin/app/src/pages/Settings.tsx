import { useCallback, useEffect, useState } from 'react';
import { getSettings, setSettings } from '../api';
import { errMsg } from '../types';

export default function Settings() {
  const [whatsapp, setWhatsapp] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      const settings = await getSettings();
      setWhatsapp(settings.superuserWhatsapp ?? '');
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const result = await setSettings(whatsapp.trim());
      setMessage(`WhatsApp del superusuario actualizado: ${result.superuserWhatsapp || '—'}.`);
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1 className="page-title">Configuración</h1>
      <p className="page-sub">
        Número de WhatsApp del superusuario que recibe los avisos de nuevos registros.
      </p>
      {error && <div className="error-box">{error}</div>}
      {message && <div className="success-box">{message}</div>}
      {loading ? (
        <div className="empty">Cargando…</div>
      ) : (
        <form className="card" style={{ maxWidth: 480 }} onSubmit={handleSave}>
          <div className="field">
            <label htmlFor="whatsapp">WhatsApp del superusuario</label>
            <input
              id="whatsapp"
              type="text"
              placeholder="+5355555555"
              value={whatsapp}
              onChange={(e) => setWhatsapp(e.target.value)}
            />
          </div>
          <button className="btn" type="submit" disabled={busy}>
            {busy ? 'Guardando…' : 'Guardar'}
          </button>
        </form>
      )}
    </div>
  );
}