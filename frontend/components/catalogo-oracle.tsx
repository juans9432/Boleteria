'use client'

import { useEffect, useState, type ReactNode } from 'react'
import { solicitarCatalogo } from '@/lib/catalogos'
import type { MunicipioApi } from '@/lib/municipios'

type Fila = { id: string; name: string; department?: string; discount?: number; requiresAdult?: boolean }
type Listado = {
  items: Fila[]
  readOnly: boolean
  onNew: () => void
  onEdit: (fila: Fila) => void
  onDelete: (fila: Fila) => void
}
type Formulario = {
  items: Fila[]
  editing: Fila | null
  onSave: (fila: Fila) => Promise<void>
  onCancel: () => void
}
type TipoPrestadorApi = { idTipoPrestador: number; nombre: string }
type TipoEntradaApi = { idTipoEntrada: number; nombre: string; porcentajeDescuento: number; requiereAdulto: 'S' | 'N' }
type Recurso = 'municipios' | 'tipos-prestador' | 'tipos-entrada'

function fila(registro: MunicipioApi | TipoPrestadorApi | TipoEntradaApi): Fila {
  if ('idTipoEntrada' in registro) {
    return { id: String(registro.idTipoEntrada), name: registro.nombre,
      discount: registro.porcentajeDescuento, requiresAdult: registro.requiereAdulto === 'S' }
  }
  return 'idMunicipio' in registro
    ? { id: String(registro.idMunicipio), name: registro.nombre, department: registro.departamento }
    : { id: String(registro.idTipoPrestador), name: registro.nombre }
}

export function CatalogoOracle({ recurso, listado, formulario }: {
  recurso: Recurso
  listado: (props: Listado) => ReactNode
  formulario: (props: Formulario) => ReactNode
}) {
  const [items, setItems] = useState<Fila[]>([])
  const [cargando, setCargando] = useState(true)
  const [actualizacion, setActualizacion] = useState(0)
  const [puedeEditar, setPuedeEditar] = useState(false)
  const [edicion, setEdicion] = useState<Fila | null | undefined>()
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    const control = new AbortController()
    fetch('/api/auth/sesion', { cache: 'no-store', signal: control.signal })
      .then(async respuesta => {
        if (respuesta.ok && !control.signal.aborted) {
          setPuedeEditar((await respuesta.json()).rol === 'ADMINISTRADOR')
        }
      }).catch(() => {})
    return () => control.abort()
  }, [])

  useEffect(() => {
    const control = new AbortController()
    solicitarCatalogo(recurso, '', 'GET', undefined, control.signal)
      .then(respuesta => respuesta.json())
      .then((datos: (MunicipioApi | TipoPrestadorApi | TipoEntradaApi)[]) => {
        if (!control.signal.aborted) setItems(datos.map(fila))
      }).catch(error => {
        if (!control.signal.aborted) setError(error.message)
      }).finally(() => {
        if (!control.signal.aborted) setCargando(false)
      })
    return () => control.abort()
  }, [actualizacion, recurso])

  function recargar() {
    setError('')
    setCargando(true)
    setActualizacion(valor => valor + 1)
  }

  async function editar(registro: Fila) {
    if (ocupado) return
    setError('')
    setAviso('')
    setOcupado(true)
    try {
      const respuesta = await solicitarCatalogo(recurso, '/' + registro.id)
      setEdicion(fila(await respuesta.json()))
    } catch (error) {
      setError((error as Error).message)
    } finally {
      setOcupado(false)
    }
  }

  async function guardar(registro: Fila) {
    await solicitarCatalogo(recurso, edicion ? '/' + edicion.id : '', edicion ? 'PUT' : 'POST', {
      nombre: registro.name,
      ...(recurso === 'municipios' ? { departamento: registro.department ?? '' } : {}),
      ...(recurso === 'tipos-entrada' ? {
        porcentajeDescuento: registro.discount ?? 0, requiereAdulto: registro.requiresAdult ? 'S' : 'N',
      } : {}),
    })
    setAviso(edicion ? 'Registro actualizado.' : 'Registro creado.')
    setEdicion(undefined)
    recargar()
  }

  async function eliminar(registro: Fila) {
    if (ocupado || !window.confirm(`¿Eliminar el registro ${registro.name}?`)) return
    setError('')
    setAviso('')
    setOcupado(true)
    try {
      await solicitarCatalogo(recurso, '/' + registro.id, 'DELETE')
      setAviso('Registro eliminado.')
      recargar()
    } catch (error) {
      setError((error as Error).message)
    } finally {
      setOcupado(false)
    }
  }

  if (edicion !== undefined) {
    return formulario({ items, editing: edicion, onSave: guardar, onCancel: () => setEdicion(undefined) })
  }

  return <>
    {aviso && <p role="status">{aviso}</p>}
    {error && <div role="alert" className="form-error">{error}{' '}
      <button type="button" className="secondary" onClick={recargar}>Recargar</button>
    </div>}
    {cargando && <p role="status">Consultando catálogo en Oracle…</p>}
    {listado({
      items,
      readOnly: !puedeEditar || ocupado || cargando,
      onNew: () => { setAviso(''); setEdicion(null) },
      onEdit: registro => void editar(registro),
      onDelete: registro => void eliminar(registro),
    })}
  </>
}

