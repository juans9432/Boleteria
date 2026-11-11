'use client'

import { useCallback, useEffect, useState } from 'react'
import { solicitarCatalogo } from '@/lib/catalogos'

type Opcion = { id: number; nombre: string }
type Catalogos = Record<'perfiles' | 'planes' | 'zonas' | 'tipos' | 'combos', Opcion[]>
type Seleccion = { zona: number; tipo: number; combo: number | null; grupo: number | null }
type Reserva = {
  id: number; perfil: number; plan: number | null; creada: string; vence: string
  estado: 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA' | 'EXPIRADA'
  lineas: { seleccion: Seleccion; precio: number; emitida: boolean; codigo: string | null; estadoBoleta: string | null }[]
}
type Cotizacion = { revision: string; total: number; lineas: { numero: number; precio: number }[] }
type Formulario = { perfil: number; plan: number | null; selecciones: Seleccion[] }
const nuevaLinea = (): Seleccion => ({ zona: 0, tipo: 0, combo: null, grupo: null })
const fecha = (v: string) => new Date(v).toLocaleString('es-CO', { timeZone: 'America/Bogota' })
const dinero = (v: number) => v.toLocaleString('es-CO', { style: 'currency', currency: 'COP' })

function Selector({ titulo, valor, opciones, vacio, cambiar }: {
  titulo: string; valor: number | null; opciones: Opcion[]; vacio: string; cambiar: (valor: number) => void
}) {
  return <label className="field"><span>{titulo}</span><select value={valor ?? 0} onChange={e => cambiar(Number(e.target.value))}>
    <option value={0}>{vacio}</option>
    {valor && !opciones.some(x => x.id === valor) ? <option value={valor}>Referencia #{valor}</option> : null}
    {opciones.map(x => <option key={x.id} value={x.id}>{x.nombre}</option>)}
  </select></label>
}

export function ReservasOracle() {
  const [catalogos, setCatalogos] = useState<Catalogos>()
  const [reservas, setReservas] = useState<Reserva[]>([])
  const [cotizacion, setCotizacion] = useState<Cotizacion>()
  const [detalle, setDetalle] = useState<Reserva>()
  const [formulario, setFormulario] = useState<Formulario>()
  const [edicion, setEdicion] = useState<number>()
  const [permiteCancelar, setPermiteCancelar] = useState(false)
  const [estado, setEstado] = useState('')
  const [perfilFiltro, setPerfilFiltro] = useState(0)
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(false)

  const consultar = useCallback(async (id?: number): Promise<Reserva | Reserva[]> => {
    const response = await solicitarCatalogo('reservas', id ? '/' + id : '')
    return response.json()
  }, [])
  const listar = useCallback(async () => setReservas(await consultar() as Reserva[]), [consultar])
  useEffect(() => {
    const carga = new AbortController()
    void fetch('/api/auth/sesion', { cache: 'no-store', signal: carga.signal })
      .then(r => r.ok ? r.json() : null)
      .then(c => setPermiteCancelar(['ADMINISTRADOR', 'OPERADOR', 'ENCARGADO_PRESTADOR'].includes(c?.rol)))
      .catch(() => {})
    void solicitarCatalogo('reservas', '/catalogos', 'GET', undefined, carga.signal)
      .then(r => r.json()).then(setCatalogos)
      .catch(e => { if (!carga.signal.aborted) setError(e.message) })
    void listar().catch(e => { if (!carga.signal.aborted) setError(e.message) })
    const timer = window.setInterval(() => void listar().catch(e => setError(e.message)), 15000)
    return () => { carga.abort(); window.clearInterval(timer) }
  }, [listar])
  useEffect(() => {
    if (!detalle || detalle.estado !== 'PENDIENTE') return
    const nueva = reservas.find(r => r.id === detalle.id)
    if (nueva && nueva.estado !== detalle.estado) setDetalle(nueva)
  }, [reservas, detalle])

  async function operar(accion: () => Promise<void>) {
    if (ocupado) return
    setOcupado(true)
    setError('')
    try { await accion() } catch (e) { setError((e as Error).message) } finally { setOcupado(false) }
  }
  function editar(r?: Reserva) {
    setCotizacion(undefined)
    setError('')
    setEdicion(r?.id)
    setFormulario(r ? { perfil: r.perfil, plan: r.plan, selecciones: r.lineas.map(x => x.seleccion) }
      : { perfil: catalogos?.perfiles.length === 1 ? catalogos.perfiles[0].id : 0, plan: null, selecciones: [nuevaLinea()] })
  }
  function modificar(i: number, cambio: Partial<Seleccion>) {
    setFormulario(f => f && ({ ...f, selecciones: f.selecciones.map((x, j) => j === i ? { ...x, ...cambio } : x) }))
  }
  const aviso = <p>Guardar una pendiente conserva la selección por 15 minutos. El cupo y el precio final se verifican al confirmar la compra.</p>
  const mensaje = error && <div role="alert" className="form-error">{error}</div>
  if (formulario && catalogos) return <form onSubmit={e => {
    e.preventDefault()
    void operar(async () => {
      const r = await solicitarCatalogo('reservas', edicion ? '/' + edicion : '', edicion ? 'PUT' : 'POST', formulario)
      setDetalle(await r.json())
      setFormulario(undefined)
      await listar()
    })
  }}>
    <div className="page-heading"><h1>{edicion ? 'Editar reserva' : 'Nueva reserva'}</h1></div>
    <section className="form-card"><h2>Información de la reserva</h2>{aviso}<div className="form-grid two">
      <Selector titulo="Cliente" valor={formulario.perfil} opciones={catalogos.perfiles} vacio="Seleccionar cliente" cambiar={perfil => setFormulario({ ...formulario, perfil })} />
      <Selector titulo="Plan (opcional)" valor={formulario.plan} opciones={catalogos.planes} vacio="Sin plan" cambiar={plan => setFormulario({ ...formulario, plan: plan || null })} />
    </div></section>
    <section className="form-card"><h2>Selección de entradas</h2><p>Asigna el mismo número de unidad a las líneas de un combo. Usa otro número para una segunda unidad.</p>
      <div className="ticket-lines">{formulario.selecciones.map((x, i) => <div className="ticket-line" key={i}>
        <Selector titulo="Función y zona" valor={x.zona} opciones={catalogos.zonas} vacio="Seleccionar" cambiar={zona => modificar(i, { zona })} />
        <Selector titulo="Tipo de entrada" valor={x.tipo} opciones={catalogos.tipos} vacio="Seleccionar" cambiar={tipo => modificar(i, { tipo })} />
        <Selector titulo="Combo opcional" valor={x.combo} opciones={catalogos.combos} vacio="Sin combo" cambiar={combo => modificar(i, { combo: combo || null, grupo: combo ? x.grupo ?? 1 : null })} />
        {x.combo && <label className="field"><span>Unidad del combo</span><input type="number" min={1} max={99999999} required value={x.grupo ?? ''} onChange={e => modificar(i, { grupo: Number(e.target.value) || null })} /></label>}
        <button type="button" className="danger-text" disabled={formulario.selecciones.length === 1} onClick={() => setFormulario({ ...formulario, selecciones: formulario.selecciones.filter((_, j) => i !== j) })}>Quitar</button>
      </div>)}</div>
      <button type="button" className="secondary" onClick={() => setFormulario({ ...formulario, selecciones: [...formulario.selecciones, nuevaLinea()] })}>Agregar entrada</button>
    </section>{mensaje}
    <div className="form-actions"><button type="submit" className="primary" disabled={ocupado}>Guardar pendiente</button>
      <button type="button" className="secondary" onClick={() => { setFormulario(undefined); setError('') }}>Volver</button></div>
  </form>

  const nombre = (campo: keyof Catalogos, id: number) => catalogos?.[campo].find(x => x.id === id)?.nombre ?? '#' + id
  const editable = detalle?.estado === 'PENDIENTE' && !detalle.lineas.some(l => l.emitida)
  if (detalle) return <>
    <button className="link-button back-link" onClick={() => { setDetalle(undefined); setCotizacion(undefined); setError('') }}>Volver</button>
    <div className="page-heading"><h1>Reserva #{detalle.id}</h1>{editable && <button className="primary" onClick={() => editar(detalle)}>Editar</button>}</div>
    <section className="detail-grid"><div className="form-card"><h2>Información</h2>
      <p>Cliente {nombre('perfiles', detalle.perfil)}</p><p>Plan {detalle.plan ? nombre('planes', detalle.plan) : 'Sin plan'}</p>
      <p>Creada {fecha(detalle.creada)}</p><p>Estado {detalle.estado}</p>
      {detalle.estado === 'CONFIRMADA' && !detalle.lineas.some(l => l.emitida && l.estadoBoleta !== 'ANULADA') &&
        <p>Reserva confirmada sin boletas vigentes.</p>}
      {detalle.estado === 'CONFIRMADA' && permiteCancelar && !detalle.lineas.some(l => l.estadoBoleta === 'USADA') &&
        <button className="danger-text" disabled={ocupado} onClick={() => {
          if (!window.confirm('¿Cancelar la reserva confirmada y anular todas sus boletas vendidas?')) return
          void operar(async () => {
            await solicitarCatalogo('reservas', '/' + detalle.id + '/cancelar-confirmada', 'POST')
            setDetalle(await consultar(detalle.id) as Reserva)
            await listar()
          })
        }}>Cancelar reserva confirmada</button>}
      {detalle.estado === 'PENDIENTE' && <p>Vence {fecha(detalle.vence)}</p>}
      {editable && <button className="danger-text" disabled={ocupado} onClick={() => {
        if (!window.confirm('¿Cancelar esta reserva pendiente?')) return
        void operar(async () => { setDetalle(await (await solicitarCatalogo('reservas', '/' + detalle.id + '/cancelar', 'POST')).json()); await listar() })
      }}>Cancelar reserva</button>}
    </div><div className="summary-card"><h2>{detalle.lineas.some(l => l.emitida) ? 'Importe registrado de las boletas' : 'Cotización orientativa'}</h2>
      <p>{dinero(detalle.lineas.reduce((s, l) => s + l.precio, 0))}</p>{editable && aviso}
      {editable && <button className="secondary" disabled={ocupado} onClick={() => void operar(async () => {
        setCotizacion(await (await solicitarCatalogo('reservas', '/' + detalle.id + '/cotizacion')).json())
      })}>Revisar precio</button>}
      {editable && cotizacion && <div>
        <p>Total para confirmar <strong>{dinero(cotizacion.total)}</strong></p>
        <ul>{cotizacion.lineas.map(l => <li key={l.numero}>Entrada {l.numero} · {dinero(l.precio)}</li>)}</ul>
        <button className="primary" disabled={ocupado} onClick={() => void operar(async () => {
          try {
            await solicitarCatalogo('reservas', '/' + detalle.id + '/confirmar', 'POST', { revision: cotizacion.revision })
          } catch (error) {
            const nueva = (error as { detalle?: { cotizacion?: Cotizacion } }).detalle?.cotizacion
            if (nueva) setCotizacion(nueva)
            throw error
          }
          setCotizacion(undefined)
          setDetalle(await consultar(detalle.id) as Reserva)
          await listar()
        })}>Aceptar precio y confirmar</button>
      </div>}
    </div></section>
    <div className="table-card"><table><thead><tr><th>Función y zona</th><th>Tipo</th><th>Combo</th><th>Precio</th><th>Boleta</th></tr></thead>
      <tbody>{detalle.lineas.map((l, i) => <tr key={i}><td>{nombre('zonas', l.seleccion.zona)}</td><td>{nombre('tipos', l.seleccion.tipo)}</td>
        <td>{l.seleccion.combo ? nombre('combos', l.seleccion.combo) : '—'}
          {l.seleccion.grupo !== null && detalle.lineas.some(x => x.seleccion.grupo === l.seleccion.grupo && x.estadoBoleta === 'ANULADA')
            && detalle.lineas.some(x => x.seleccion.grupo === l.seleccion.grupo && x.emitida && x.estadoBoleta !== 'ANULADA')
            && <small> · Unidad parcialmente anulada</small>}</td><td>{dinero(l.precio)}</td><td>{l.codigo ? l.codigo + " · " + l.estadoBoleta : "—"}</td></tr>)}</tbody>
    </table></div>{mensaje}
  </>
  const filas = reservas.filter(r => {
    const dia = new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(r.creada))
    return (!estado || r.estado === estado) && (!perfilFiltro || perfilFiltro === r.perfil) && (!desde || dia >= desde) && (!hasta || dia <= hasta)
  })
  return <><div className="page-heading"><h1>Reservas</h1><button className="primary" disabled={!catalogos?.perfiles.length} onClick={() => editar()}>Nueva reserva</button></div>
    {aviso}<div className="filters four">
      <Selector titulo="Cliente" valor={perfilFiltro} opciones={catalogos?.perfiles ?? []} vacio="Todos" cambiar={setPerfilFiltro} />
      <label className="field"><span>Estado</span><select value={estado} onChange={e => setEstado(e.target.value)}><option value="">Todos</option>
        {['PENDIENTE', 'CONFIRMADA', 'EXPIRADA', 'CANCELADA'].map(x => <option key={x}>{x}</option>)}</select></label>
      <label className="field"><span>Fecha desde</span><input type="date" value={desde} onChange={e => setDesde(e.target.value)} /></label>
      <label className="field"><span>Fecha hasta</span><input type="date" value={hasta} onChange={e => setHasta(e.target.value)} /></label>
    </div><div className="table-card"><table><thead><tr><th>Número</th><th>Cliente</th><th>Fecha</th><th>Estado</th><th>Entradas</th><th>Acciones</th></tr></thead>
      <tbody>{filas.map(r => <tr key={r.id}><td>#{r.id}</td><td>{nombre('perfiles', r.perfil)}</td><td>{fecha(r.creada)}</td>
        <td><span className={`status ${r.estado.toLowerCase()}`}>{r.estado}</span></td><td>{r.lineas.length}</td><td>
          <button disabled={ocupado} onClick={() => void operar(async () => setDetalle(await consultar(r.id) as Reserva))}>Ver</button>
        </td></tr>)}</tbody></table></div>{mensaje}</>
}




