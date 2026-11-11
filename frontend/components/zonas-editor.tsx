'use client'

import { useEffect, useState } from 'react'
import { Plus } from 'lucide-react'
import { solicitarCatalogo } from '@/lib/catalogos'

type ZonaApi = { idZona: number; idFuncion: number; nombre: string; aforo: number; precio: number }
type Borrador = { idZona?: number; nombre: string; aforo: string; precio: string }

export function ZonasEditor({ idFuncion, editable }: { idFuncion: string; editable: boolean }) {
  const [zonas, setZonas] = useState<ZonaApi[]>()
  const [borrador, setBorrador] = useState<Borrador | null>(null)
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    const control = new AbortController()
    if (!idFuncion) return
    solicitarCatalogo('zonas', '?idFuncion=' + encodeURIComponent(idFuncion), 'GET', undefined, control.signal)
      .then(r => r.json()).then((filas: ZonaApi[]) => {
        if (!control.signal.aborted) setZonas(filas)
      }).catch(e => { if (!control.signal.aborted) setError(e.message) })
    return () => control.abort()
  }, [idFuncion])

  async function guardar() {
    if (!borrador || ocupado) return
    const aforo = Number(borrador.aforo)
    const precio = Number(borrador.precio)
    if (!borrador.nombre.trim() || !Number.isInteger(aforo) || aforo < 1 || !Number.isFinite(precio) || precio <= 0) {
      setError('Indica un nombre, un aforo entero positivo y un precio positivo.')
      return
    }
    setError('')
    setOcupado(true)
    try {
      const respuesta = await solicitarCatalogo('zonas',
        borrador.idZona ? '/' + borrador.idZona : '', borrador.idZona ? 'PUT' : 'POST', {
          nombre: borrador.nombre, aforo, precio,
          ...(borrador.idZona ? {} : { idFuncion: Number(idFuncion) }),
        })
      const guardada: ZonaApi = await respuesta.json()
      setZonas(actual => [...(actual ?? []).filter(z => z.idZona !== guardada.idZona), guardada]
        .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')))
      setBorrador(null)
    } catch (error) {
      setError((error as Error).message)
    } finally {
      setOcupado(false)
    }
  }

  async function eliminar(zona: ZonaApi) {
    if (ocupado || !window.confirm(`¿Eliminar la zona ${zona.nombre}?`)) return
    setError('')
    setOcupado(true)
    try {
      await solicitarCatalogo('zonas', '/' + zona.idZona, 'DELETE')
      setZonas(actual => actual?.filter(z => z.idZona !== zona.idZona))
    } catch (error) {
      setError((error as Error).message)
    } finally {
      setOcupado(false)
    }
  }

  return <section className="form-card">
    <div className="section-title"><h2>Zonas de la función</h2>
      <p>{!idFuncion ? 'Guarda primero la función para agregar sus zonas.'
        : editable ? 'Cada zona tiene su propio aforo y precio.' : 'Las zonas de esta función son de solo lectura.'}</p></div>
    {zonas && <>
      <div className="zone-head"><span>Nombre</span><span>Aforo</span><span>Precio (COP)</span><span>Acción</span></div>
      {zonas.map(z => <div className="zone-row" key={z.idZona}>
        <span>{z.nombre}</span><span>{z.aforo}</span><span>{z.precio}</span>
        {editable && <div className="actions">
          <button type="button" disabled={ocupado || !!borrador} onClick={() => {
            setError('')
            setBorrador({ idZona: z.idZona, nombre: z.nombre, aforo: String(z.aforo), precio: String(z.precio) })
          }}>Editar zona</button>
          <button type="button" className="danger-text" disabled={ocupado || !!borrador}
            onClick={() => void eliminar(z)}>Quitar</button>
        </div>}
      </div>)}
      {zonas.length === 0 && <p>Esta función aún no tiene zonas.</p>}
      {editable && !borrador && <button type="button" className="secondary add-zone" disabled={ocupado}
        onClick={() => { setError(''); setBorrador({ nombre: '', aforo: '', precio: '' }) }}>
        <Plus size={16} /> Agregar zona
      </button>}
      {borrador && <div className="zone-row">
        <input aria-label="Nombre de zona" value={borrador.nombre} onChange={e => setBorrador({ ...borrador, nombre: e.target.value })} />
        <input aria-label="Aforo de zona" type="number" min="1" step="1" value={borrador.aforo}
          onChange={e => setBorrador({ ...borrador, aforo: e.target.value })} />
        <input aria-label="Precio de zona" type="number" min="0.01" step="0.01" value={borrador.precio}
          onChange={e => setBorrador({ ...borrador, precio: e.target.value })} />
        <div className="actions">
          <button type="button" className="primary" disabled={ocupado} onClick={() => void guardar()}>Guardar zona</button>
          <button type="button" disabled={ocupado} onClick={() => { setBorrador(null); setError('') }}>Cancelar zona</button>
        </div>
      </div>}
    </>}
    {idFuncion && !zonas && !error && <p role="status">Consultando zonas en Oracle…</p>}
    {error && <div role="alert" className="form-error">{error}</div>}
  </section>
}
