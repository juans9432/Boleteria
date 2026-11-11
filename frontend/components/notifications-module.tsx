'use client'

import { useEffect, useState } from 'react'
import { solicitarCatalogo } from '@/lib/catalogos'

type Aviso = { id: number; reserva: number | null; asunto: string | null; mensaje: string; fecha: string; leida: boolean }

export function NotificationsModule({ onBack }: { onBack: () => void }) {
  const [avisos, setAvisos] = useState<Aviso[]>([])
  const [detalle, setDetalle] = useState<Aviso>()
  const [filtro, setFiltro] = useState('todas')
  const [busqueda, setBusqueda] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    const control = new AbortController()
    void solicitarCatalogo('notificaciones', '', 'GET', undefined, control.signal)
      .then(r => r.json()).then(setAvisos)
      .catch(e => { if (!control.signal.aborted) setError(e.message) })
    return () => control.abort()
  }, [])

  async function operar(accion: () => Promise<void>) {
    if (ocupado) return
    setOcupado(true)
    setError('')
    try { await accion() } catch (e) { setError((e as Error).message) }
    finally { setOcupado(false) }
  }

  const mensaje = error && <div role="alert" className="form-error">{error}</div>
  if (detalle) return <>
    <button className="link-button back-link" onClick={() => { setDetalle(undefined); setError('') }}>Volver</button>
    <div className="page-heading"><h1>{detalle.asunto || 'Notificación'}</h1></div>
    <section className="form-card detail-card">
      <p><b>Fecha</b>{fecha(detalle.fecha)}</p>
      {detalle.reserva !== null && <p><b>Reserva</b>#{detalle.reserva}</p>}
      <p><b>Estado</b>{detalle.leida ? 'LEÍDA' : 'NO LEÍDA'}</p>
      <p>{detalle.mensaje}</p>
      {!detalle.leida && <button className="secondary" disabled={ocupado} onClick={() => void operar(async () => {
        const actual: Aviso = await (await solicitarCatalogo('notificaciones', '/' + detalle.id + '/leer', 'POST')).json()
        setDetalle(actual)
        setAvisos(filas => filas.map(a => a.id === actual.id ? actual : a))
      })}>Marcar como leída</button>}
    </section>{mensaje}
  </>

  const texto = busqueda.trim().toLowerCase()
  const filas = avisos.filter(a => (filtro === 'todas' || (filtro === 'leidas') === a.leida)
    && ((a.asunto ?? '') + ' ' + a.mensaje).toLowerCase().includes(texto))
  return <>
    <div className="page-heading"><h1>Notificaciones</h1></div>
    <div className="filters">
      <label className="field"><span>Buscar</span><input value={busqueda} onChange={e => setBusqueda(e.target.value)} /></label>
      <label className="field"><span>Estado</span><select value={filtro} onChange={e => setFiltro(e.target.value)}>
        <option value="todas">Todas</option><option value="leidas">Leídas</option><option value="pendientes">No leídas</option>
      </select></label>
    </div>
    <div className="table-card"><table><thead><tr><th>Asunto</th><th>Fecha</th><th>Estado</th><th>Acciones</th></tr></thead>
      <tbody>{filas.map(a => <tr key={a.id}>
        <td>{a.asunto || 'Notificación'}</td><td>{fecha(a.fecha)}</td>
        <td><span className={`status ${a.leida ? 'leida' : 'no-leida'}`}>{a.leida ? 'LEÍDA' : 'NO LEÍDA'}</span></td>
        <td><button disabled={ocupado} onClick={() => void operar(async () => {
          setDetalle(await (await solicitarCatalogo('notificaciones', '/' + a.id)).json())
        })}>Ver detalle</button></td>
      </tr>)}</tbody></table></div>
    <button className="link-button back-link" onClick={onBack}>Volver</button>{mensaje}
  </>
}

const fecha = (valor: string) => new Date(valor).toLocaleString('es-CO', { timeZone: 'America/Bogota' })

