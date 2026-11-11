'use client'
import { useEffect, useState } from 'react'
import { solicitarCatalogo } from '@/lib/catalogos'

export type ReservationStatus = 'PENDIENTE' | 'CONFIRMADA' | 'EXPIRADA' | 'CANCELADA'
export type TicketStatus = 'VENDIDA' | 'USADA' | 'ANULADA'
export type ComboSelection = { attractionId: string; functionId: string; zoneName: string; ticketTypeId: string }
export type ReservationLine = { id: string; functionId: string; zoneId?: string; zoneName?: string; ticketTypeId: string; comboId?: string; comboSelections?: ComboSelection[]; price: number; discount: number }
export const functionForTicket = (ticket: Ticket, functions: EventFunction[]) => functions.find(f => f.zones.some(z => (z.id || `zone-${f.id}-${z.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`) === ticket.zoneId))
export type Reservation = { id: string; number: string; clientId: string; planId?: string; createdAt: string; status: ReservationStatus; lines: ReservationLine[]; subtotal?: number; discounts?: number; total?: number }
export type Ticket = { id: string; code: string; reservationId: string; lineId: string; zoneId?: string; functionId?: string; zoneName?: string; ticketTypeId: string; comboId?: string; price: number; status: TicketStatus }
export type Client = { id: string; document: string; name: string; phone: string; userId: string }
export type Plan = { id: string; name: string; description: string; status: 'ACTIVO' | 'INACTIVO'; providerId: string }
export type Attraction = { id: string; name: string; description: string; providerId: string }
export type EventFunction = { id: string; attractionId: string; date: string; status: 'PROGRAMADA' | 'CANCELADA' | 'REALIZADA'; opening: string; closing: string; zones: { id?: string; name: string; capacity: number; price: number }[] }
export type CatalogRecord = { id: string; name: string; discount?: number; requiresAdult?: boolean }
export type Combo = { id: string; name: string; price: number; validDays: number; status: 'ACTIVO' | 'INACTIVO'; attractions: { attractionId: string; percentage: number }[] }

const zoneIdFor = (functionId: string, zoneName: string) => `zone-${functionId}-${zoneName.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`
const normalizeTicket = (ticket: Ticket, functions: EventFunction[]): Ticket => { if (ticket.zoneId) return ticket; const fn = functions.find(f => f.id === ticket.functionId); const zone = fn?.zones.find(z => z.name === ticket.zoneName); return zone ? { ...ticket, zoneId: zone.id || zoneIdFor(fn!.id, zone.name) } : ticket }
export const reservationTotal = (reservation: Reservation, tickets: Ticket[]) => tickets.filter(t => t.reservationId === reservation.id && t.status !== 'ANULADA').reduce((sum, t) => sum + t.price, 0)
export function migrateSalesData(reservations: Reservation[], tickets: Ticket[], functions: EventFunction[]) { const migratedTickets = tickets.map(ticket => normalizeTicket(ticket, functions)); const migratedReservations = reservations.map(reservation => ({ ...reservation, lines: reservation.lines.map(line => { if (line.zoneId) return line; const fn = functions.find(f => f.id === line.functionId); const zone = fn?.zones.find(z => z.name === line.zoneName); return zone ? { ...line, zoneId: zone.id || zoneIdFor(fn!.id, zone.name) } : line }), subtotal: undefined, discounts: undefined, total: undefined })); return { reservations: migratedReservations, tickets: migratedTickets } }
type BoletaApi = {
  id: number; codigo: string; estado: TicketStatus; precioPagado: number; reserva: number
  zona: number; nombreZona: string; tipo: number; nombreTipo: string; lineaReserva: number | null
  combo: number | null; grupoCombo: number | null
  funcion: number; atraccion: string; fecha: string; apertura: string; cierre: string; estadoFuncion: string
}

export function BoletasOracle() {
  const [boletas, setBoletas] = useState<BoletaApi[]>([])
  const [seleccion, setSeleccion] = useState<BoletaApi>()
  const [estado, setEstado] = useState('TODOS')
  const [funcion, setFuncion] = useState('TODOS')
  const [codigo, setCodigo] = useState('')
  const [permiteUso, setPermiteUso] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    const control = new AbortController()
    void solicitarCatalogo('boletas', '', 'GET', undefined, control.signal).then(r => r.json()).then(setBoletas)
      .catch(e => { if (!control.signal.aborted) setError(e.message) })
    void fetch('/api/auth/sesion', { cache: 'no-store', signal: control.signal })
      .then(r => r.ok ? r.json() : null).then(c => setPermiteUso(
        ['ADMINISTRADOR', 'OPERADOR', 'ENCARGADO_PRESTADOR'].includes(c?.rol)))
      .catch(() => {})
    return () => control.abort()
  }, [])

  async function operar(accion: () => Promise<void>) {
    if (ocupado) return
    setOcupado(true)
    setError('')
    try { await accion() } catch (e) { setError((e as Error).message) }
    finally { setOcupado(false) }
  }
  async function consultar(ruta: string) {
    const r = await solicitarCatalogo('boletas', ruta)
    setSeleccion(await r.json())
  }
  const mensaje = error && <div className="form-error" role="alert">{error}</div>
  if (seleccion) return <>
    <button className="link-button back-link" onClick={() => { setSeleccion(undefined); setError('') }}>Volver</button>
    <div className="page-heading"><div><p className="eyebrow">DETALLE DE BOLETA</p><h1>{seleccion.codigo}</h1></div>
      {permiteUso && seleccion.estado === 'VENDIDA' && seleccion.estadoFuncion === 'PROGRAMADA' &&
        <button className="secondary" disabled={ocupado} onClick={() => void operar(async () => {
          const actual: BoletaApi = await (await solicitarCatalogo('boletas', '/' + seleccion.id + '/usar', 'POST')).json()
          setSeleccion(actual)
          setBoletas(todas => todas.map(b => b.id === actual.id ? actual : b))
        })}>Marcar usada</button>}
      {permiteUso && seleccion.estado === 'VENDIDA' && (seleccion.combo === null || seleccion.grupoCombo !== null) &&
        <button className="danger-text" disabled={ocupado} onClick={() => {
          const combo = seleccion.combo !== null
          if (!window.confirm(combo ? '¿Anular las boletas vendidas de esta unidad de combo?' : '¿Anular esta boleta?')) return
          void operar(async () => {
            await solicitarCatalogo('boletas', '/' + seleccion.id + (combo ? '/anular-combo' : '/anular'), 'POST')
            await consultar('/' + seleccion.id)
            setBoletas(await (await solicitarCatalogo('boletas')).json())
          })
        }}>{seleccion.combo === null ? 'Anular boleta' : 'Anular unidad de combo'}</button>}
    </div>
    <section className="form-card detail-card">
      <p><b>Reserva</b>#{seleccion.reserva}</p>
      <p><b>Función</b>{seleccion.atraccion} · {seleccion.fecha} · {seleccion.apertura}–{seleccion.cierre}</p>
      <p><b>Zona</b>{seleccion.nombreZona}</p><p><b>Tipo de entrada</b>{seleccion.nombreTipo}</p>
      <p><b>Precio pagado</b>{moneda(seleccion.precioPagado)}</p>
      <p><b>Estado</b><span className={`status ${seleccion.estado.toLowerCase()}`}>{seleccion.estado}</span></p>
    </section>{mensaje}
  </>
  const funciones = [...new Map(boletas.map(b => [b.funcion, b.atraccion + ' · ' + b.fecha])).entries()]
  const filas = boletas.filter(b => (estado === 'TODOS' || estado === b.estado)
    && (funcion === 'TODOS' || Number(funcion) === b.funcion)
    && b.codigo.toLowerCase().includes(codigo.toLowerCase()))
  return <>
    <div className="page-heading"><div><p className="eyebrow">VENTAS</p><h1>Boletas</h1></div></div>
    <div className="filters three">
      <label className="field"><span>Estado</span><select value={estado} onChange={e => setEstado(e.target.value)}>
        {['TODOS','VENDIDA','USADA','ANULADA'].map(v => <option key={v}>{v}</option>)}
      </select></label>
      <label className="field"><span>Función</span><select value={funcion} onChange={e => setFuncion(e.target.value)}>
        <option value="TODOS">Todas</option>{funciones.map(([id, texto]) => <option key={id} value={id}>{texto}</option>)}
      </select></label>
      <label className="field"><span>Buscar por código</span><input value={codigo} onChange={e => setCodigo(e.target.value)} placeholder="Código de boleta" /></label>
    </div>
    <button className="secondary" disabled={ocupado || !codigo.trim()} onClick={() =>
      void operar(() => consultar('/codigo?valor=' + encodeURIComponent(codigo.trim())))}>Consultar código</button>
    <div className="table-card"><table><thead><tr>
      <th>Código</th><th>Reserva</th><th>Función</th><th>Zona</th><th>Tipo</th><th>Precio pagado</th><th>Estado</th><th>Acciones</th>
    </tr></thead><tbody>{filas.map(b => <tr key={b.id}>
      <td>{b.codigo}</td><td>#{b.reserva}</td><td>{b.atraccion}</td><td>{b.nombreZona}</td><td>{b.nombreTipo}</td>
      <td>{moneda(b.precioPagado)}</td><td><span className={`status ${b.estado.toLowerCase()}`}>{b.estado}</span></td>
      <td><button disabled={ocupado} onClick={() => void operar(() => consultar('/' + b.id))}>Ver detalle</button></td>
    </tr>)}</tbody></table></div>{mensaje}
  </>
}
const moneda = (valor: number) => valor.toLocaleString('es-CO', { style: 'currency', currency: 'COP' })


