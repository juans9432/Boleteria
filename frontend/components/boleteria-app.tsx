'use client'

import { ReservasOracle } from './reservas-oracle'

import { ZonasEditor } from './zonas-editor'
import { solicitarCatalogo } from '@/lib/catalogos'
import { cerrarSesion, iniciarSesion } from '@/lib/sesion'
import { CatalogoOracle } from './catalogo-oracle'
import { useEffect, useMemo, useState } from 'react'
import { CalendarDays, ChevronDown, FileDown, Menu, Pencil, Plus, Search, Trash2, X } from 'lucide-react'
import { BoletasOracle, migrateSalesData, type Reservation, type Ticket } from './reservations-module'
import { NotificationsModule } from './notifications-module'
import { OccupancyReport } from './occupancy-report'
import { ReportsDashboard } from './reports-dashboard'

type Status = 'PROGRAMADA' | 'CANCELADA' | 'REALIZADA'
type View = 'login' | 'functions' | 'form' | 'reports' | 'municipios' | 'prestadorTypes' | 'ticketTypes' | 'roles' | 'prestadores' | 'planes' | 'atracciones' | 'combos' | 'usuarios' | 'clientes' | 'reservas' | 'boletas' | 'notificaciones' | 'catalogForm'
type CatalogKind = 'municipios' | 'prestadorTypes' | 'ticketTypes' | 'roles'
type CatalogRecord = { id: string; name: string; department?: string; discount?: number; requiresAdult?: boolean }
type Provider = { id: string; nit: string; name: string; email: string; municipalityId: string; typeId: string }
type Plan = { id: string; name: string; description: string; status: 'ACTIVO' | 'INACTIVO'; providerId: string }
type Attraction = { id: string; name: string; description: string; providerId: string }
type ComboItem = { attractionId: string; percentage: number }
type Combo = { id: string; name: string; price: number; validDays: number; status: 'ACTIVO' | 'INACTIVO'; attractions: ComboItem[] }
type Zone = { id?: string; name: string; capacity: number; price: number }
type EventFunction = { id: string; attractionId: string; date: string; status: Status; opening: string; closing: string; zones: Zone[] }
type SystemUser = { id: string; email: string; idPrestador?: string; status: 'ACTIVO' | 'INACTIVO'; roleId: string }
type Client = { id: string; document: string; name: string; phone: string; userId: string; correoCuenta?: string }

const initialCatalogs: Record<CatalogKind, CatalogRecord[]> = { municipios: ['Armenia', 'Montenegro', 'Calarcá', 'Salento', 'Filandia', 'Quimbaya'].map((name, i) => ({ id: `municipio-${i + 1}`, name, department: 'Quindío' })), prestadorTypes: ['Parque temático', 'Operador turístico', 'Parque natural', 'Organizador de eventos'].map((name, i) => ({ id: `prestador-${i + 1}`, name })), ticketTypes: ['General', 'Niño', 'Adulto mayor', 'Estudiante'].map((name, i) => ({ id: `ticket-${i + 1}`, name, discount: name === 'Niño' ? 20 : name === 'Adulto mayor' ? 15 : name === 'Estudiante' ? 10 : 0, requiresAdult: name === 'Niño' })), roles: ['Administrador', 'Operador'].map((name, i) => ({ id: `role-${i + 1}`, name })) }
const initialFunctions: EventFunction[] = [{ id: '1', attractionId: 'legacy-parque', date: '2026-11-07', status: 'PROGRAMADA', opening: '09:00', closing: '18:00', zones: [{ name: 'General', capacity: 300, price: 25000 }, { name: 'Preferencial', capacity: 100, price: 45000 }, { name: 'VIP', capacity: 40, price: 90000 }] }, { id: '2', attractionId: 'legacy-parque', date: '2026-11-08', status: 'PROGRAMADA', opening: '09:00', closing: '18:00', zones: [{ name: 'General', capacity: 300, price: 25000 }] }, { id: '3', attractionId: 'legacy-jardin', date: '2026-11-14', status: 'PROGRAMADA', opening: '08:00', closing: '16:00', zones: [{ name: 'General', capacity: 150, price: 20000 }] }]
const initialProviders: Provider[] = [{ id: 'provider-1', nit: '890001234-1', name: 'Parque del Café', email: 'contacto@parquedelcafe.co', municipalityId: 'municipio-1', typeId: 'prestador-1' }, { id: 'provider-2', nit: '890009876-2', name: 'Jardín Botánico del Quindío', email: 'info@jardinbotanicoquindio.org', municipalityId: 'municipio-2', typeId: 'prestador-3' }]
const initialAttractions: Attraction[] = [{ id: 'legacy-parque', name: 'Parque del Café', description: 'Parque temático y cultural del Quindío.', providerId: 'provider-1' }, { id: 'legacy-jardin', name: 'Jardín Botánico del Quindío', description: 'Naturaleza, senderos y conservación.', providerId: 'provider-2' }, { id: 'legacy-festival', name: 'Festival de la Cosecha', description: 'Evento cultural regional.', providerId: 'provider-1' }]
const initialPlans: Plan[] = [{ id: 'plan-1', name: 'Plan familiar', description: 'Entrada para grupos familiares.', status: 'ACTIVO', providerId: 'provider-1' }]
const initialCombos: Combo[] = [{ id: 'combo-1', name: 'Aventura Quindiana', price: 95000, validDays: 30, status: 'ACTIVO', attractions: [{ attractionId: 'legacy-parque', percentage: 60 }, { attractionId: 'legacy-jardin', percentage: 40 }] }]
const initialUsers: SystemUser[] = [{ id: 'user-1', email: 'admin@boleteria.co', status: 'ACTIVO', roleId: 'role-1' }, { id: 'user-2', email: 'operador@boleteria.co', status: 'ACTIVO', roleId: 'role-2' }]
const initialClients: Client[] = [{ id: 'client-1', document: '1094912345', name: 'María Fernanda López', phone: '3105551234', userId: 'user-2' }]
const labels: Record<CatalogKind, string> = { municipios: 'Municipios', prestadorTypes: 'Tipos de prestador', ticketTypes: 'Tipos de entrada', roles: 'Roles' }
const dateLabel = (v: string) => new Intl.DateTimeFormat('es-CO', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(`${v}T12:00:00`)).replace('.', '')
const money = (v: number) => new Intl.NumberFormat('es-CO').format(v)
const normalizeFunctions = (items: EventFunction[]) => items.map(fn => ({ ...fn, zones: fn.zones.map(zone => ({ ...zone, id: zone.id || `zone-${fn.id}-${zone.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}` })) }))

function Field({ label, children }: { label: string; children: React.ReactNode }) { return <label className="field"><span>{label}</span>{children}</label> }
function Select({ value, onChange, children }: { value: string; onChange: (v: string) => void; children: React.ReactNode }) { return <div className="select-wrap"><select value={value} onChange={e => onChange(e.target.value)}>{children}</select><ChevronDown size={17} /></div> }

function Sidebar({ view, onNavigate }: { view: View; onNavigate: (view: View) => void }) { const groups: [string, [View, string][]][] = [['CATÁLOGOS', [['municipios', 'Municipios'], ['prestadorTypes', 'Tipos de prestador'], ['ticketTypes', 'Tipos de entrada'], ['roles', 'Roles']]], ['OFERTA', [['prestadores', 'Prestadores'], ['planes', 'Planes'], ['atracciones', 'Atracciones'], ['functions', 'Funciones'], ['combos', 'Combos']]], ['VENTAS', [['reservas', 'Reservas'], ['boletas', 'Boletas'], ['reports', 'Reportes']]], ['USUARIOS', [['usuarios', 'Usuarios del sistema'], ['clientes', 'Clientes']]], ['SISTEMA', [['notificaciones', 'Notificaciones']]]]; return <aside className="sidebar"><div className="sidebar-header"><div className="brand">Boletería</div></div><nav className="sidebar-nav" aria-label="Navegación principal">{groups.map(([title, links]) => <div className="nav-group" key={title}><div className="nav-label">{title}</div>{links.map(([key, text]) => <button key={key} className={view === key || (view === 'form' && key === 'functions') || view === 'catalogForm' && ['municipios', 'prestadorTypes', 'ticketTypes', 'roles'].includes(key) ? 'active' : ''} onClick={() => onNavigate(key)}>{text}</button>)}</div>)}</nav><footer className="sidebar-footer"><div className="role">Rol: Administrador</div><button className="logout-button" onClick={() => onNavigate('login')}>Cerrar sesión</button></footer></aside> }
function Login({ onLogin }: { onLogin: () => void }) { const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [error, setError] = useState(''); const [enviando, setEnviando] = useState(false); return <main className="login-page"><div className="login-brand">Boletería</div><form className="login-card" onSubmit={async e => {
  e.preventDefault()
  if (enviando) return
  setError('')
  setEnviando(true)
  try {
    await iniciarSesion(email, password)
    setPassword('')
    onLogin()
  } catch (error) {
    setError(error instanceof Error ? error.message : 'No fue posible iniciar sesión.')
  } finally {
    setEnviando(false)
  }
}}><h1>Iniciar sesión</h1><p>Ingrese con su correo y contraseña.</p><Field label="Correo"><input type="email" placeholder="correo@ejemplo.com" value={email} onChange={e => setEmail(e.target.value)} /></Field><Field label="Contraseña"><input type="password" placeholder="Contraseña" value={password} onChange={e => setPassword(e.target.value)} /></Field>{error && <div className="form-error">{error}</div>}<button className="primary full" disabled={enviando}>{enviando ? 'Ingresando…' : 'Ingresar'}</button><button type="button" className="link-button">¿Olvidó su contraseña?</button></form></main> }

function ConfirmDelete({ title, detail, blocked, onCancel, onConfirm }: { title: string; detail: string; blocked?: boolean; onCancel: () => void; onConfirm: () => void }) { return <section className="delete-confirm"><div><p>CONFIRMAR ELIMINACIÓN</p><h2>{title}</h2><span>{blocked ? 'No se puede eliminar porque tiene registros relacionados.' : detail}</span></div><div className="confirm-actions"><button className="secondary" onClick={onCancel}>Cancelar</button><button className="destructive" disabled={blocked} onClick={onConfirm}>Eliminar</button></div></section> }

function CatalogList({ kind, items, onNew, onEdit, onDelete, readOnly = false }: { kind: CatalogKind; items: CatalogRecord[]; onNew: () => void; onEdit: (x: CatalogRecord) => void; onDelete: (x: CatalogRecord) => void; readOnly?: boolean }) { const [search, setSearch] = useState(''); const rows = items.filter(x => `${x.name} ${x.department || ''}`.toLowerCase().includes(search.toLowerCase())); return <><Header title={labels[kind]} action={readOnly ? undefined : `Nuevo ${kind === 'municipios' ? 'municipio' : 'registro'}`} onAction={onNew} /><div className="catalog-search"><Field label="Buscar"><div className="input-icon"><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar..." /><Search size={18} /></div></Field></div><div className="table-card"><table><thead><tr><th>Nombre</th>{kind === 'municipios' && <th>Departamento</th>}{kind === 'ticketTypes' && <><th>Descuento</th><th>Requiere adulto</th></>}{!readOnly && <th>Acciones</th>}</tr></thead><tbody>{rows.map(x => <tr key={x.id}><td>{x.name}</td>{kind === 'municipios' && <td>{x.department}</td>}{kind === 'ticketTypes' && <><td>{x.discount}%</td><td>{x.requiresAdult ? 'Sí' : 'No'}</td></>}{!readOnly && <td><div className="actions"><button onClick={() => onEdit(x)}><Pencil size={15} /> Editar</button><button className="danger-text" onClick={() => onDelete(x)}><Trash2 size={15} /> Eliminar</button></div></td>}</tr>)}</tbody></table></div></> }
function Header({ title, action, onAction }: { title: string; action?: string; onAction?: () => void }) { return <div className="page-heading"><div><p className="eyebrow">OFERTA TURÍSTICA</p><h1>{title}</h1></div>{action && <button className="primary" onClick={onAction}><Plus size={18} /> {action}</button>}</div> }

function CatalogForm({ kind, editing, items, onSave, onCancel }: { kind: CatalogKind; editing: CatalogRecord | null; items: CatalogRecord[]; onSave: (x: CatalogRecord) => void | Promise<void>; onCancel: () => void }) { const [name, setName] = useState(editing?.name || ''); const [department, setDepartment] = useState(editing?.department || ''); const [discount, setDiscount] = useState(String(editing?.discount ?? 0)); const [requiresAdult, setRequiresAdult] = useState(editing?.requiresAdult ?? false); const [error, setError] = useState(''); const [guardando, setGuardando] = useState(false); async function save(e: React.FormEvent) { e.preventDefault(); if (guardando) return; setError(''); if (!name.trim() || kind === 'municipios' && !department.trim()) return setError('Completa los campos obligatorios.'); if (kind === 'ticketTypes' && (Number(discount) < 0 || Number(discount) > 100)) return setError('El descuento debe estar entre 0 y 100.'); if (kind === 'roles' && items.some(x => x.name.toLowerCase() === name.trim().toLowerCase() && x.id !== editing?.id)) return setError('Ya existe un registro con ese nombre.'); setGuardando(true); try { await onSave({ id: editing?.id || `${kind}-${Date.now()}`, name: name.trim(), ...(kind === 'municipios' ? { department } : {}), ...(kind === 'ticketTypes' ? { discount: Number(discount), requiresAdult } : {}) }) } catch (error) { setError((error as Error).message) } finally { setGuardando(false) } } return <form onSubmit={save}><Header title={`${editing ? 'Editar' : 'Nuevo'} ${labels[kind].slice(0, -1).toLowerCase()}`} /><section className="form-card"><Field label="Nombre"><input autoFocus value={name} onChange={e => setName(e.target.value)} /></Field>{kind === 'municipios' && <Field label="Departamento"><input value={department} onChange={e => setDepartment(e.target.value)} /></Field>}{kind === 'ticketTypes' && <div className="form-grid two"><Field label="Descuento (%)"><input type="number" min="0" max="100" step="0.01" required value={discount} onChange={e => setDiscount(e.target.value)} /></Field><Field label="Requiere adulto"><Select value={requiresAdult ? 'Sí' : 'No'} onChange={v => setRequiresAdult(v === 'Sí')}><option>Sí</option><option>No</option></Select></Field></div>}</section>{error && <div className="form-error form-error-wide">{error}</div>}<div className="form-actions"><button className="primary" disabled={guardando}>Guardar</button><button type="button" className="secondary" disabled={guardando} onClick={onCancel}>Cancelar</button></div></form> }

function ProviderPage({ items, municipalities, types, administrador, onConsultar, onSave, onDelete }: { items: Provider[]; municipalities: CatalogRecord[]; types: CatalogRecord[]; administrador: boolean; onConsultar: (id: string) => Promise<Provider>; onSave: (x: Provider) => Promise<void>; onDelete: (x: Provider) => Promise<void> }) { const [editing, setEditing] = useState<Provider | null>(null); const [search, setSearch] = useState(''); const [error, setError] = useState(''); const [deleting, setDeleting] = useState<Provider | null>(null); const [ocupado, setOcupado] = useState(false); const rows = items.filter(x => `${x.nit} ${x.name} ${x.email}`.toLowerCase().includes(search.toLowerCase())); if (editing) return <ProviderForm editing={editing.id ? editing : null} municipalities={municipalities} types={types} items={items} onCancel={() => setEditing(null)} onSave={async x => { await onSave(x); setEditing(null) }} />; return <><Header title="Prestadores" action={administrador ? "Nuevo prestador" : undefined} onAction={() => setEditing({ id: '', nit: '', name: '', email: '', municipalityId: municipalities[0]?.id || '', typeId: types[0]?.id || '' })} /><div className="catalog-search"><Field label="Buscar"><div className="input-icon"><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar por NIT, nombre o correo..." /><Search size={18} /></div></Field></div><div className="table-card"><table><thead><tr><th>NIT</th><th>Nombre</th><th>Correo</th><th>Municipio</th><th>Tipo</th><th>Acciones</th></tr></thead><tbody>{rows.map(x => <tr key={x.id}><td>{x.nit}</td><td>{x.name}</td><td>{x.email}</td><td>{municipalities.find(m => m.id === x.municipalityId)?.name}</td><td>{types.find(t => t.id === x.typeId)?.name}</td><td><div className="actions"><button disabled={ocupado} onClick={async () => { setError(""); setOcupado(true); try { setEditing(await onConsultar(x.id)) } catch (e) { setError((e as Error).message) } finally { setOcupado(false) } }}><Pencil size={15} /> Editar</button>{administrador && <button className="danger-text" disabled={ocupado} onClick={() => { setError(""); setDeleting(x) }}><Trash2 size={15} /> Eliminar</button>}</div></td></tr>)}</tbody></table></div>{deleting && <ConfirmDelete title={`¿Eliminar ${deleting.name}?`} detail="Esta acción no se puede deshacer." onCancel={() => setDeleting(null)} onConfirm={async () => { if (ocupado) return; setOcupado(true); try { await onDelete(deleting) } catch (e) { setError((e as Error).message) } finally { setDeleting(null); setOcupado(false) } }} />}{error && <div className="form-error">{error}</div>}</> }
function ProviderForm({ editing, items, municipalities, types, onSave, onCancel }: { editing: Provider | null; items: Provider[]; municipalities: CatalogRecord[]; types: CatalogRecord[]; onSave: (x: Provider) => Promise<void>; onCancel: () => void }) { const [form, setForm] = useState<Provider>(editing || { id: '', nit: '', name: '', email: '', municipalityId: municipalities[0]?.id || '', typeId: types[0]?.id || '' }); const [error, setError] = useState(''); const [guardando, setGuardando] = useState(false); async function save(e: React.FormEvent) { e.preventDefault(); if (guardando) return; setError(""); if (!form.nit || !form.name || !form.municipalityId || !form.typeId || form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return setError('Completa los campos y usa un correo válido cuando lo proporciones.'); setGuardando(true); try { await onSave(form) } catch (e) { setError((e as Error).message) } finally { setGuardando(false) } } return <form onSubmit={save}><Header title={`${editing?.id ? 'Editar' : 'Nuevo'} prestador`} /><section className="form-card"><div className="form-grid two"><Field label="NIT"><input value={form.nit} onChange={e => setForm({ ...form, nit: e.target.value })} /></Field><Field label="Nombre"><input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field><Field label="Correo electrónico"><input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></Field><Field label="Municipio"><Select value={form.municipalityId} onChange={v => setForm({ ...form, municipalityId: v })}>{municipalities.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}</Select></Field><Field label="Tipo de prestador"><Select value={form.typeId} onChange={v => setForm({ ...form, typeId: v })}>{types.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}</Select></Field></div></section>{error && <div className="form-error form-error-wide">{error}</div>}<div className="form-actions"><button className="primary" disabled={guardando}>Guardar prestador</button><button type="button" className="secondary" disabled={guardando} onClick={onCancel}>Cancelar</button></div></form> }

type PrestadorApi = {
  idPrestador: number
  nit: string
  nombre: string
  correo: string | null
  idMunicipio: number
  idTipoPrestador: number
}

function prestadorDeApi(p: PrestadorApi): Provider {
  return { id: String(p.idPrestador), nit: p.nit, name: p.nombre, email: p.correo ?? '',
    municipalityId: String(p.idMunicipio), typeId: String(p.idTipoPrestador) }
}

function PrestadoresOracle() {
  const [datos, setDatos] = useState<{ items: Provider[]; municipios: CatalogRecord[]; tipos: CatalogRecord[]; administrador: boolean }>()
  const [error, setError] = useState('')

  useEffect(() => {
    const control = new AbortController()
    const consultar = (recurso: 'prestadores' | 'municipios' | 'tipos-prestador') =>
      solicitarCatalogo(recurso, '', 'GET', undefined, control.signal).then(r => r.json())
    Promise.all([
      consultar('prestadores'), consultar('municipios'), consultar('tipos-prestador'),
      fetch('/api/auth/sesion', { cache: 'no-store', signal: control.signal }).then(async r => r.ok ? r.json() : null),
    ]).then(([prestadores, municipios, tipos, sesion]) => {
      if (!control.signal.aborted) setDatos({
        items: prestadores.map(prestadorDeApi),
        municipios: municipios.map((m: { idMunicipio: number; nombre: string }) => ({ id: String(m.idMunicipio), name: m.nombre })),
        tipos: tipos.map((t: { idTipoPrestador: number; nombre: string }) => ({ id: String(t.idTipoPrestador), name: t.nombre })),
        administrador: sesion?.rol === 'ADMINISTRADOR',
      })
    }).catch(e => { if (!control.signal.aborted) setError(e.message) })
    return () => control.abort()
  }, [])

  if (error) return <div role="alert" className="form-error">{error}</div>
  if (!datos) return <p role="status">Consultando prestadores en Oracle…</p>

  return <ProviderPage items={datos.items} municipalities={datos.municipios} types={datos.tipos}
    administrador={datos.administrador}
    onConsultar={async id => prestadorDeApi(await (await solicitarCatalogo('prestadores', '/' + id)).json())}
    onSave={async x => {
      const respuesta = await solicitarCatalogo('prestadores', x.id ? '/' + x.id : '', x.id ? 'PUT' : 'POST', {
        nit: x.nit, nombre: x.name, correo: x.email,
        idMunicipio: Number(x.municipalityId), idTipoPrestador: Number(x.typeId),
      })
      const fila = prestadorDeApi(await respuesta.json())
      setDatos(actual => actual && { ...actual, items: [...actual.items.filter(p => p.id !== fila.id), fila] })
    }}
    onDelete={async x => {
      await solicitarCatalogo('prestadores', '/' + x.id, 'DELETE')
      setDatos(actual => actual && { ...actual, items: actual.items.filter(p => p.id !== x.id) })
    }}
  />
}
function SimpleEntityPage({ kind, items, providers, blocked, onConsultar, onSave, onDelete }: { kind: 'planes' | 'atracciones'; items: (Plan | Attraction)[]; providers: Provider[]; blocked?: (x: Plan | Attraction) => boolean; onConsultar?: (id: string) => Promise<Plan | Attraction>; onSave: (x: Plan | Attraction) => void | Promise<void>; onDelete: (x: Plan | Attraction) => void | Promise<void> }) { const [editing, setEditing] = useState<Plan | Attraction | null>(null); const [deleting, setDeleting] = useState<Plan | Attraction | null>(null); const [search, setSearch] = useState(''); const [error, setError] = useState(''); const [ocupado, setOcupado] = useState(false); const isPlan = kind === 'planes'; const rows = items.filter(x => x.name.toLowerCase().includes(search.toLowerCase())); if (editing) return <SimpleForm kind={kind} editing={editing} providers={providers} items={items} onSave={async x => { await onSave(x); setEditing(null) }} onCancel={() => setEditing(null)} />; return <><Header title={isPlan ? 'Planes' : 'Atracciones'} action={`Nueva ${isPlan ? 'plan' : 'atracción'}`} onAction={() => setEditing(isPlan ? { id: '', name: '', description: '', status: 'ACTIVO', providerId: providers[0]?.id || '' } : { id: '', name: '', description: '', providerId: providers[0]?.id || '' })} /><div className="catalog-search"><Field label="Buscar"><div className="input-icon"><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar..." /><Search size={18} /></div></Field></div><div className="table-card"><table><thead><tr><th>Nombre</th><th>Descripción</th><th>Prestador</th>{isPlan && <th>Estado</th>}<th>Acciones</th></tr></thead><tbody>{rows.map(x => <tr key={x.id}><td>{x.name}</td><td>{x.description}</td><td>{providers.find(p => p.id === x.providerId)?.name}</td>{isPlan && <td><span className={`status ${(x as Plan).status.toLowerCase()}`}>{(x as Plan).status}</span></td>}<td><div className="actions"><button disabled={ocupado} onClick={async () => { if (!onConsultar) return setEditing(x); setError(""); setOcupado(true); try { setEditing(await onConsultar(x.id)) } catch (e) { setError((e as Error).message) } finally { setOcupado(false) } }}><Pencil size={15} /> Editar</button><button className="danger-text" onClick={() => setDeleting(x)}><Trash2 size={15} /> Eliminar</button></div></td></tr>)}</tbody></table></div>{deleting && <ConfirmDelete title={`¿Eliminar ${deleting.name}?`} detail="Esta acción no se puede deshacer." blocked={blocked?.(deleting)} onCancel={() => setDeleting(null)} onConfirm={async () => { if (ocupado) return; setOcupado(true); setError(""); try { await onDelete(deleting) } catch (e) { setError((e as Error).message) } finally { setOcupado(false); setDeleting(null) } }} />}{error && <div role="alert" className="form-error">{error}</div>}</> }
function SimpleForm({ kind, editing, providers, items, onSave, onCancel }: { kind: 'planes' | 'atracciones'; editing: Plan | Attraction; providers: Provider[]; items: (Plan | Attraction)[]; onSave: (x: Plan | Attraction) => void | Promise<void>; onCancel: () => void }) { const isPlan = kind === 'planes'; const [form, setForm] = useState<Plan | Attraction>(editing); const [error, setError] = useState(''); const [guardando, setGuardando] = useState(false); async function save(e: React.FormEvent) { e.preventDefault(); if (guardando) return; setError(""); if (!form.name.trim() || !form.providerId || isPlan && !(form as Plan).status) return setError('Completa los campos obligatorios.'); setGuardando(true); try { await onSave({ ...form, name: form.name.trim() }) } catch (e) { setError((e as Error).message) } finally { setGuardando(false) } } return <form onSubmit={save}><Header title={`${editing.id ? 'Editar' : 'Nuevo'} ${isPlan ? 'plan' : 'atracción'}`} /><section className="form-card"><Field label="Nombre"><input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field><Field label="Descripción"><textarea className="textarea" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></Field><Field label="Prestador"><Select value={form.providerId} onChange={v => setForm({ ...form, providerId: v })}>{providers.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</Select></Field>{isPlan && <Field label="Estado"><Select value={(form as Plan).status} onChange={v => setForm({ ...form as Plan, status: v as Plan['status'] })}><option>ACTIVO</option><option>INACTIVO</option></Select></Field>}</section>{error && <div className="form-error form-error-wide">{error}</div>}<div className="form-actions"><button className="primary" disabled={guardando}>Guardar</button><button type="button" className="secondary" onClick={onCancel}>Cancelar</button></div></form> }

type PlanApi = { idPlan: number; nombre: string; descripcion: string | null; estado: Plan['status']; idPrestador: number }

type AtraccionApi = { idAtraccion: number; nombre: string; descripcion: string | null; idPrestador: number }
function ofertaDeApi(plan: PlanApi | AtraccionApi): Plan | Attraction {
  return { id: String('idPlan' in plan ? plan.idPlan : plan.idAtraccion), name: plan.nombre, description: plan.descripcion ?? '',
    ...('idPlan' in plan ? { status: plan.estado } : {}), providerId: String(plan.idPrestador) }
}

function OfertaOracle({ kind }: { kind: 'planes' | 'atracciones' }) {
  const [datos, setDatos] = useState<{ planes: (Plan | Attraction)[]; prestadores: Provider[] }>()
  const [error, setError] = useState('')

  useEffect(() => {
    const control = new AbortController()
    Promise.all(([kind, 'prestadores'] as const).map(recurso =>
      solicitarCatalogo(recurso, '', 'GET', undefined, control.signal).then(r => r.json())
    )).then(([planes, prestadores]) => {
      if (!control.signal.aborted) setDatos({ planes: planes.map(ofertaDeApi), prestadores: prestadores.map(prestadorDeApi) })
    }).catch(e => { if (!control.signal.aborted) setError(e.message) })
    return () => control.abort()
  }, [kind])

  if (error) return <div role="alert" className="form-error">{error}</div>
  if (!datos) return <p role="status">Consultando datos en Oracle…</p>

  return <SimpleEntityPage kind={kind} items={datos.planes} providers={datos.prestadores}
    onConsultar={async id => ofertaDeApi(await (await solicitarCatalogo(kind, '/' + id)).json())}
    onSave={async x => {
      const respuesta = await solicitarCatalogo(kind, x.id ? '/' + x.id : '', x.id ? 'PUT' : 'POST', {
        nombre: x.name, descripcion: x.description, ...('status' in x ? { estado: x.status } : {}), idPrestador: Number(x.providerId),
      })
      const plan = ofertaDeApi(await respuesta.json())
      setDatos(actual => actual && { ...actual, planes: [...actual.planes.filter(p => p.id !== plan.id), plan] })
    }}
    onDelete={async x => {
      await solicitarCatalogo(kind, '/' + x.id, 'DELETE')
      setDatos(actual => actual && { ...actual, planes: actual.planes.filter(p => p.id !== x.id) })
    }}
  />
}

function ComboPage({ items, attractions, onConsultar, onSave, onDelete }: { items: Combo[]; attractions: Attraction[]; onConsultar: (id: string) => Promise<Combo>; onSave: (x: Combo) => Promise<void>; onDelete: (x: Combo) => Promise<void> }) { const [editing, setEditing] = useState<Combo | null>(null); const [deleting, setDeleting] = useState<Combo | null>(null); const [error, setError] = useState(""); const [ocupado, setOcupado] = useState(false); if (editing) return <ComboForm editing={editing} attractions={attractions} onSave={async x => { await onSave(x); setEditing(null) }} onCancel={() => setEditing(null)} />; return <><Header title="Combos" action="Nuevo combo" onAction={() => setEditing({ id: '', name: '', price: 0, validDays: 1, status: 'INACTIVO', attractions: [] })} /><div className="table-card"><table><thead><tr><th>Nombre</th><th>Precio (COP)</th><th>Vigencia</th><th>Estado</th><th>Atracciones</th><th>Acciones</th></tr></thead><tbody>{items.map(x => <tr key={x.id}><td>{x.name}</td><td>${money(x.price)}</td><td>{x.validDays} días</td><td><span className={`status ${x.status.toLowerCase()}`}>{x.status}</span></td><td>{x.attractions.length}</td><td><div className="actions"><button disabled={ocupado} onClick={async () => { setError(""); setOcupado(true); try { setEditing(await onConsultar(x.id)) } catch (e) { setError((e as Error).message) } finally { setOcupado(false) } }}><Pencil size={15} /> Editar</button><button className="danger-text" onClick={() => setDeleting(x)}><Trash2 size={15} /> Eliminar</button></div></td></tr>)}</tbody></table></div>{deleting && <ConfirmDelete title={`¿Eliminar ${deleting.name}?`} detail="Esta acción no se puede deshacer." onCancel={() => setDeleting(null)} onConfirm={async () => { if (ocupado) return; setOcupado(true); setError(""); try { await onDelete(deleting) } catch (e) { setError((e as Error).message) } finally { setOcupado(false); setDeleting(null) } }} />}{error && <div role="alert" className="form-error">{error}</div>}</> }
function ComboForm({ editing, attractions, onSave, onCancel }: { editing: Combo; attractions: Attraction[]; onSave: (x: Combo) => Promise<void>; onCancel: () => void }) { const [form, setForm] = useState(editing); const [error, setError] = useState(''); const total = form.attractions.reduce((sum, x) => sum + Math.round(x.percentage * 100), 0) / 100;
  const [guardando, setGuardando] = useState(false);
  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (guardando) return;
    setError('');
    setGuardando(true);
    try { await onSave(form) }
    catch (e) { setError((e as Error).message) }
    finally { setGuardando(false) }
  } return <form onSubmit={save}><Header title={`${editing.id ? 'Editar' : 'Nuevo'} combo`} /><section className="form-card"><div className="form-grid two"><Field label="Nombre"><input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field><Field label="Precio (COP)"><input type="number" min="0.01" step="0.01" value={form.price} onChange={e => setForm({ ...form, price: Number(e.target.value) })} /></Field><Field label="Días de vigencia"><input type="number" min="1" max="999" value={form.validDays} onChange={e => setForm({ ...form, validDays: Number(e.target.value) })} /></Field><Field label="Estado"><Select value={form.status} onChange={v => setForm({ ...form, status: v as Combo['status'] })}><option disabled={!editing.id}>ACTIVO</option><option>INACTIVO</option></Select></Field></div></section><fieldset className="form-card" disabled={editing.status === 'ACTIVO' || guardando}><div className="section-title"><h2>Atracciones del combo</h2><p>El total debe ser 100 % para activar. Para editar la composición de un combo activo, primero inactívalo.</p></div><div className="zone-head"><span>Atracción</span><span>Porcentaje del precio</span><span /><span /></div>{form.attractions.map((item, index) => <div className="zone-row combo-row" key={index}><Select value={item.attractionId} onChange={v => setForm({ ...form, attractions: form.attractions.map((x, i) => i === index ? { ...x, attractionId: v } : x) })}>{attractions.filter(a => !form.attractions.some((x, i) => i !== index && x.attractionId === a.id)).map(a => <option key={a.id} value={a.id}>{a.name}</option>)}</Select><input type="number" min="1" max="100" step="0.01" value={item.percentage} onChange={e => setForm({ ...form, attractions: form.attractions.map((x, i) => i === index ? { ...x, percentage: Number(e.target.value) } : x) })} /><span>%</span><button type="button" className="danger-text" onClick={() => setForm({ ...form, attractions: form.attractions.filter((_, i) => i !== index) })}>Quitar</button></div>)}<button type="button" className="secondary add-zone" onClick={() => { const available = attractions.find(a => !form.attractions.some(x => x.attractionId === a.id)); if (available) setForm({ ...form, attractions: [...form.attractions, { attractionId: available.id, percentage: 1 }] }) }}> <Plus size={16} /> Agregar atracción</button><div className={total === 100 ? 'combo-total valid' : 'combo-total'}>Total: {total} %</div></fieldset>{error && <div className="form-error form-error-wide">{error}</div>}<div className="form-actions"><button className="primary" disabled={guardando}>Guardar combo</button><button type="button" className="secondary" onClick={onCancel}>Cancelar</button></div></form> }

type ComboApi = {
  idCombo: number; nombre: string; precio: number; diasVigencia: number; estado: Combo['status'];
  atracciones: { idAtraccion: number; porcentaje: number }[]
}

function comboDeApi(c: ComboApi): Combo {
  return { id: String(c.idCombo), name: c.nombre, price: c.precio, validDays: c.diasVigencia, status: c.estado,
    attractions: c.atracciones.map(a => ({ attractionId: String(a.idAtraccion), percentage: a.porcentaje })) }
}

function CombosOracle() {
  const [datos, setDatos] = useState<{ combos: Combo[]; atracciones: Attraction[] }>()
  const [error, setError] = useState('')

  useEffect(() => {
    const control = new AbortController()
    Promise.all((['combos', 'atracciones'] as const).map(recurso =>
      solicitarCatalogo(recurso, '', 'GET', undefined, control.signal).then(r => r.json())
    )).then(([combos, atracciones]) => {
      if (!control.signal.aborted) setDatos({
        combos: combos.map(comboDeApi), atracciones: atracciones.map(ofertaDeApi),
      })
    }).catch(e => { if (!control.signal.aborted) setError(e.message) })
    return () => control.abort()
  }, [])

  if (error) return <div role="alert" className="form-error">{error}</div>
  if (!datos) return <p role="status">Consultando combos en Oracle…</p>

  return <ComboPage items={datos.combos} attractions={datos.atracciones}
    onConsultar={async id => comboDeApi(await (await solicitarCatalogo('combos', '/' + id)).json())}
    onSave={async combo => {
      const respuesta = await solicitarCatalogo('combos', combo.id ? '/' + combo.id : '', combo.id ? 'PUT' : 'POST', {
        nombre: combo.name, precio: combo.price, diasVigencia: combo.validDays, estado: combo.status,
        atracciones: combo.attractions.map(a => ({ idAtraccion: Number(a.attractionId), porcentaje: a.percentage })),
      })
      const guardado = comboDeApi(await respuesta.json())
      setDatos(actual => actual && { ...actual, combos: [...actual.combos.filter(c => c.id !== guardado.id), guardado] })
    }}
    onDelete={async combo => {
      await solicitarCatalogo('combos', '/' + combo.id, 'DELETE')
      setDatos(actual => actual && { ...actual, combos: actual.combos.filter(c => c.id !== combo.id) })
    }}
  />
}
type FuncionApi = {
  idFuncion: number
  idAtraccion: number
  fecha: string
  horaApertura: string
  horaCierre: string
  estado: Status
}

function funcionDeApi(f: FuncionApi): EventFunction {
  return { id: String(f.idFuncion), attractionId: String(f.idAtraccion), date: f.fecha,
    opening: f.horaApertura, closing: f.horaCierre, status: f.estado, zones: [] }
}

function FuncionesOracle() {
  const [datos, setDatos] = useState<{ funciones: EventFunction[]; atracciones: Attraction[] }>()
  const [edicion, setEdicion] = useState<EventFunction | null>(null)
  const [zonasDe, setZonasDe] = useState<EventFunction | null>(null);
  const [eliminacion, setEliminacion] = useState<EventFunction | null>(null)
  const [error, setError] = useState('')
  const [permiteCancelar, setPermiteCancelar] = useState(false)
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    const control = new AbortController()
    void fetch('/api/auth/sesion', { cache: 'no-store', signal: control.signal })
      .then(r => r.ok ? r.json() : null)
      .then(c => setPermiteCancelar(['ADMINISTRADOR', 'ENCARGADO_PRESTADOR'].includes(c?.rol)))
      .catch(() => {})
    Promise.all((['funciones', 'atracciones'] as const).map(recurso =>
      solicitarCatalogo(recurso, '', 'GET', undefined, control.signal).then(r => r.json())
    )).then(([funciones, atracciones]) => {
      if (!control.signal.aborted) setDatos({
        funciones: funciones.map(funcionDeApi), atracciones: atracciones.map(ofertaDeApi),
      })
    }).catch(e => { if (!control.signal.aborted) setError(e.message) })
    return () => control.abort()
  }, [])

  async function editar(x: EventFunction) {
    setError('')
    setOcupado(true)
    try {
      setEdicion(funcionDeApi(await (await solicitarCatalogo('funciones', '/' + x.id)).json()))
    } catch (error) {
      setError((error as Error).message)
    } finally {
      setOcupado(false)
    }
  }

  async function guardar(form: EventFunction) {
    const respuesta = await solicitarCatalogo('funciones', form.id ? '/' + form.id : '', form.id ? 'PUT' : 'POST', {
      idAtraccion: Number(form.attractionId), fecha: form.date, estado: form.status,
      horaApertura: form.opening, horaCierre: form.closing,
    })
    const fila = funcionDeApi(await respuesta.json())
    setDatos(actual => actual && {
      ...actual, funciones: [...actual.funciones.filter(f => f.id !== fila.id), fila],
    })
    setEdicion(null)
  }

  async function eliminar(x: EventFunction) {
    if (ocupado) return
    setOcupado(true)
    setError('')
    try {
      await solicitarCatalogo('funciones', '/' + x.id, 'DELETE')
      setDatos(actual => actual && { ...actual, funciones: actual.funciones.filter(f => f.id !== x.id) })
    } catch (error) {
      setError((error as Error).message)
    } finally {
      setEliminacion(null)
      setOcupado(false)
    }
  }

  async function cancelarFuncion(x: EventFunction) {
    if (ocupado || !window.confirm('¿Cancelar esta función y anular sus boletas vendidas?')) return
    setOcupado(true)
    setError('')
    try {
      await solicitarCatalogo('funciones', '/' + x.id + '/cancelar', 'POST')
      setDatos(actual => actual && {
        ...actual, funciones: actual.funciones.map(f => f.id === x.id ? { ...f, status: 'CANCELADA' } : f),
      })
    } catch (error) {
      setError((error as Error).message)
    } finally {
      setOcupado(false)
    }
  }

  if (!datos) return error
    ? <div role="alert" className="form-error">{error}</div>
    : <p role="status">Consultando funciones en Oracle…</p>

  if (zonasDe) return <>
    <Header title="Zonas" />
    <ZonasEditor key={zonasDe.id} idFuncion={zonasDe.id} editable={zonasDe.status === 'PROGRAMADA'} />
    <button type="button" className="secondary" onClick={() => setZonasDe(null)}>Volver a funciones</button>
  </>

  if (edicion) return <FunctionForm editing={edicion} attractions={datos.atracciones}
    onCancel={() => setEdicion(null)} onSave={guardar} />

  return <>
    <Header title="Funciones" action={datos.atracciones.length ? 'Nueva función' : undefined}
      onAction={() => {
        setError('')
        setEdicion({ id: '', attractionId: datos.atracciones[0]?.id || '', date: '',
          status: 'PROGRAMADA', opening: '09:00', closing: '18:00', zones: [] })
      }} />
    <div className="table-card"><table>
      <thead><tr><th>Atracción</th><th>Fecha</th><th>Apertura</th><th>Cierre</th><th>Estado</th><th>Acciones</th></tr></thead>
      <tbody>{datos.funciones.map(x => <tr key={x.id}>
        <td>{datos.atracciones.find(a => a.id === x.attractionId)?.name}</td>
        <td>{dateLabel(x.date)}</td><td>{x.opening}</td><td>{x.closing}</td>
        <td><span className={`status ${x.status.toLowerCase()}`}>{x.status}</span></td>
        <td><button type="button" onClick={() => setZonasDe(x)}>Zonas</button>{x.status === 'PROGRAMADA' && <>
          <button disabled={ocupado} onClick={() => void editar(x)}><Pencil size={15} /> Editar</button>
          {permiteCancelar && <button className="danger-text" disabled={ocupado} onClick={() => void cancelarFuncion(x)}>Cancelar función</button>}
          <button className="danger-text" disabled={ocupado} onClick={() => setEliminacion(x)}><Trash2 size={15} /> Eliminar</button>
        </>}</td>
      </tr>)}</tbody>
    </table></div>
    {eliminacion && <ConfirmDelete title="¿Eliminar la función?"
      detail="Solo se puede eliminar una función programada sin zonas."
      onCancel={() => setEliminacion(null)} onConfirm={() => void eliminar(eliminacion)} />}
    {error && <div role="alert" className="form-error">{error}</div>}
  </>
}

function FunctionForm({ editing, attractions, onSave, onCancel }: {
  editing: EventFunction; attractions: Attraction[];
  onSave: (x: EventFunction) => Promise<void>; onCancel: () => void
}) {
  const [form, setForm] = useState(editing)
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  return <form onSubmit={async e => {
    e.preventDefault()
    if (guardando) return
    setError('')
    setGuardando(true)
    try { await onSave(form) }
    catch (error) { setError((error as Error).message) }
    finally { setGuardando(false) }
  }}>
    <Header title={`${editing.id ? 'Editar' : 'Nueva'} función`} />
    <section className="form-card">
      <Field label="Atracción"><Select value={form.attractionId} onChange={v => setForm({ ...form, attractionId: v })}>
        {attractions.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
      </Select></Field>
      <div className="form-grid two">
        <Field label="Fecha"><input required type="date" value={form.date} onChange={e => setForm({ ...form, date: e.target.value })} /></Field>
        <Field label="Estado"><input readOnly value={form.status} /></Field>
        <Field label="Hora de apertura"><input required type="time" value={form.opening} onChange={e => setForm({ ...form, opening: e.target.value })} /></Field>
        <Field label="Hora de cierre"><input required type="time" value={form.closing} onChange={e => setForm({ ...form, closing: e.target.value })} /></Field>
      </div>
    </section>
    <ZonasEditor key={editing.id} idFuncion={editing.id} editable={editing.status === 'PROGRAMADA'} />
    {error && <div role="alert" className="form-error form-error-wide">{error}</div>}
    <div className="form-actions"><button className="primary" disabled={guardando}>Guardar función</button>
      <button type="button" className="secondary" disabled={guardando} onClick={onCancel}>Cancelar</button></div>
  </form>
}
function UsersPage({ users, roles, prestadores, onConsultar, onSave }: { users: SystemUser[]; roles: CatalogRecord[]; prestadores: Provider[]; onConsultar: (id: string) => Promise<SystemUser>; onSave: (x: SystemUser, clave?: string) => Promise<void> }) { const [editing, setEditing] = useState<SystemUser | null>(null); const [error, setError] = useState(""); const [ocupado, setOcupado] = useState(false); const [search, setSearch] = useState(''); const [role, setRole] = useState('TODOS'); const [status, setStatus] = useState('TODOS'); async function editar(usuario: SystemUser, desactivar = false) {
  setError('')
  setOcupado(true)
  try {
    const detalle = await onConsultar(usuario.id)
    setEditing(desactivar ? { ...detalle, status: 'INACTIVO' } : detalle)
  } catch (error) {
    setError((error as Error).message)
  } finally {
    setOcupado(false)
  }
}
const rows = users.filter(u => u.email.toLowerCase().includes(search.toLowerCase()) && (role === 'TODOS' || u.roleId === role) && (status === 'TODOS' || u.status === status)); if (editing) return <UserForm editing={editing.id ? editing : null} roles={roles} prestadores={prestadores} onCancel={() => setEditing(null)} onSave={async (x, clave) => { await onSave(x, clave); setEditing(null) }} />; return <><Header title="Usuarios del sistema" action="Nuevo usuario" onAction={() => setEditing({ id: '', email: '', status: 'ACTIVO', roleId: roles[0]?.id || '' })} /><div className="filters three"><Field label="Buscar por correo"><div className="input-icon"><input value={search} onChange={e => setSearch(e.target.value)} placeholder="correo@ejemplo.com" /><Search size={18} /></div></Field><Field label="Rol"><Select value={role} onChange={setRole}><option value="TODOS">Todos</option>{roles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</Select></Field><Field label="Estado"><Select value={status} onChange={setStatus}><option>TODOS</option><option>ACTIVO</option><option>INACTIVO</option></Select></Field></div><div className="table-card"><table><thead><tr><th>Correo</th><th>Rol</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>{rows.map(u => <tr key={u.id}><td>{u.email}</td><td>{roles.find(r => r.id === u.roleId)?.name || 'Sin rol'}</td><td><span className={`status ${u.status.toLowerCase()}`}>{u.status}</span></td><td><div className="actions"><button disabled={ocupado} onClick={() => void editar(u)}><Pencil size={15} /> Editar</button>{u.status === 'ACTIVO' && <button className="danger-text" disabled={ocupado} onClick={() => void editar(u, true)}>Desactivar</button>}</div></td></tr>)}</tbody></table></div>{error && <div role="alert" className="form-error">{error}</div>}</> }
function UserForm({ editing, roles, prestadores, onSave, onCancel }: { editing: SystemUser | null; roles: CatalogRecord[]; prestadores: Provider[]; onSave: (x: SystemUser, clave?: string) => Promise<void>; onCancel: () => void }) { const [form, setForm] = useState<SystemUser>(editing || { id: '', email: '', status: 'ACTIVO', roleId: roles[0]?.id || '' }); const [password, setPassword] = useState(''); const [error, setError] = useState(''); const [guardando, setGuardando] = useState(false);
  async function save(e: React.FormEvent) {
    e.preventDefault()
    if (guardando) return
    setError('')
    setGuardando(true)
    try { await onSave(form, password || undefined); setPassword('') }
    catch (error) { setError((error as Error).message) }
    finally { setGuardando(false) }
  } return <form onSubmit={save}><Header title={`${editing ? 'Editar' : 'Nuevo'} usuario`} /><section className="form-card"><div className="form-grid two"><Field label="Correo electrónico"><input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></Field><Field label="Contraseña"><input type="password" autoComplete="new-password" required={!editing} minLength={12} value={password} onChange={e => setPassword(e.target.value)} placeholder={editing ? 'Déjela vacía para no cambiarla' : 'Contraseña'} /><small>{editing ? 'Déjela vacía para no cambiarla' : 'No se almacena en texto plano.'}</small></Field><Field label="Estado"><Select value={form.status} onChange={v => setForm({ ...form, status: v as SystemUser['status'] })}><option>ACTIVO</option><option>INACTIVO</option></Select></Field><Field label="Rol"><Select value={form.roleId} onChange={v => setForm({ ...form, roleId: v, idPrestador: v === 'ENCARGADO_PRESTADOR' ? form.idPrestador : undefined })}>{roles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</Select></Field>
  {form.roleId === 'ENCARGADO_PRESTADOR' && <Field label="Prestador asignado"><Select value={form.idPrestador ?? ''} onChange={v => setForm({ ...form, idPrestador: v })}>
    <option value="">Selecciona un prestador</option>
    {prestadores.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
  </Select></Field>}</div></section>{error && <div className="form-error form-error-wide">{error}</div>}<div className="form-actions"><button className="primary" disabled={guardando}>Guardar usuario</button><button type="button" className="secondary" onClick={onCancel}>Cancelar</button></div></form> }
type UsuarioApi = { idUsuario: number; correo: string; estado: SystemUser['status']; rol: string; idPrestador: number | null }

function usuarioDeApi(u: UsuarioApi): SystemUser {
  return { id: String(u.idUsuario), email: u.correo, status: u.estado, roleId: u.rol,
    idPrestador: u.idPrestador === null ? undefined : String(u.idPrestador) }
}

function UsuariosOracle({ soloRoles }: { soloRoles: boolean }) {
  const [datos, setDatos] = useState<{ usuarios: SystemUser[]; roles: CatalogRecord[]; prestadores: Provider[] }>()
  const [error, setError] = useState('')
  useEffect(() => {
    const control = new AbortController()
    const consultar = (recurso: 'roles' | 'usuarios' | 'prestadores') =>
      solicitarCatalogo(recurso, '', 'GET', undefined, control.signal).then(r => r.json())
    Promise.all([consultar('roles'), soloRoles ? [] : consultar('usuarios'), soloRoles ? [] : consultar('prestadores')])
      .then(([roles, usuarios, prestadores]) => {
        if (!control.signal.aborted) setDatos({
          roles: roles.map((nombre: string) => ({ id: nombre, name: nombre })),
          usuarios: usuarios.map(usuarioDeApi), prestadores: prestadores.map(prestadorDeApi),
        })
      }).catch(e => { if (!control.signal.aborted) setError(e.message) })
    return () => control.abort()
  }, [soloRoles])

  if (error) return <div role="alert" className="form-error">{error}</div>
  if (!datos) return <p role="status">Consultando Oracle…</p>
  if (soloRoles) return <CatalogList kind="roles" items={datos.roles} readOnly onNew={() => {}} onEdit={() => {}} onDelete={() => {}} />

  return <UsersPage users={datos.usuarios} roles={datos.roles} prestadores={datos.prestadores}
    onConsultar={async id => usuarioDeApi(await (await solicitarCatalogo('usuarios', '/' + id)).json())}
    onSave={async (usuario, clave) => {
      const r = await solicitarCatalogo('usuarios', usuario.id ? '/' + usuario.id : '', usuario.id ? 'PUT' : 'POST', {
        correo: usuario.email, rol: usuario.roleId, estado: usuario.status,
        idPrestador: usuario.idPrestador ? Number(usuario.idPrestador) : null, clave,
      })
      const guardado = usuarioDeApi(await r.json())
      setDatos(actual => actual && { ...actual, usuarios: [...actual.usuarios.filter(u => u.id !== guardado.id), guardado] })
    }}
  />
}
function ClientsPage({ clients, cuentas, rol, onConsultar, onSave, onDelete }: { clients: Client[]; cuentas: CuentaVinculable[]; rol: RolPerfiles; onConsultar: (id: string) => Promise<Client>; onSave: (x: Client) => Promise<void>; onDelete: (x: Client) => Promise<void> }) { const [editing, setEditing] = useState<Client | null>(null); const [deleting, setDeleting] = useState<Client | null>(null); const [error, setError] = useState(""); const [ocupado, setOcupado] = useState(false); const [search, setSearch] = useState(''); const rows = clients.filter(c => `${c.name} ${c.document}`.toLowerCase().includes(search.toLowerCase())); if (editing) return <ClientForm editing={editing.id ? editing : null} cuentas={cuentas} rol={rol} onCancel={() => setEditing(null)} onSave={async x => { await onSave(x); setEditing(null) }} />; return <><Header title="Clientes" action={rol === 'CLIENTE' ? undefined : "Nuevo cliente"} onAction={() => setEditing({ id: '', document: '', name: '', phone: '', userId: '' })} /><div className="catalog-search"><Field label="Buscar por nombre o documento"><div className="input-icon"><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar cliente..." /><Search size={18} /></div></Field></div><div className="table-card"><table><thead><tr><th>Documento</th><th>Nombre</th><th>Teléfono</th><th>Usuario</th><th>Acciones</th></tr></thead><tbody>{rows.map(c => <tr key={c.id}><td>{c.document}</td><td>{c.name}</td><td>{c.phone || '—'}</td><td>{c.correoCuenta || 'Sin usuario'}</td><td><div className="actions"><button disabled={ocupado} onClick={async () => { setError(""); setOcupado(true); try { setEditing(await onConsultar(c.id)) } catch (e) { setError((e as Error).message) } finally { setOcupado(false) } }}><Pencil size={15} /> Editar</button>{rol === 'ADMINISTRADOR' && <button className="danger-text" disabled={ocupado} onClick={() => setDeleting(c)}><Trash2 size={15} /> Eliminar</button>}</div></td></tr>)}</tbody></table></div>{deleting && <ConfirmDelete title={`¿Eliminar a ${deleting.name}?`} detail="Esta acción no se puede deshacer." onCancel={() => setDeleting(null)} onConfirm={async () => { if (ocupado) return; setOcupado(true); setError(""); try { await onDelete(deleting) } catch (e) { setError((e as Error).message) } finally { setOcupado(false); setDeleting(null) } }} />}{error && <div role="alert" className="form-error">{error}</div>}</> }
function ClientForm({ editing, cuentas, rol, onSave, onCancel }: { editing: Client | null; cuentas: CuentaVinculable[]; rol: RolPerfiles; onSave: (x: Client) => Promise<void>; onCancel: () => void }) { const [form, setForm] = useState<Client>(editing || { id: '', document: '', name: '', phone: '', userId: '' }); const [error, setError] = useState(''); const [guardando, setGuardando] = useState(false);
  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (guardando) return;
    setError('');
    setGuardando(true);
    try { await onSave(form) }
    catch (e) { setError((e as Error).message) }
    finally { setGuardando(false) }
  } return <form onSubmit={save}><Header title={`${editing ? 'Editar' : 'Nuevo'} cliente`} /><section className="form-card"><div className="form-grid two"><Field label="Documento"><input readOnly={rol === 'CLIENTE'} value={form.document} onChange={e => setForm({ ...form, document: e.target.value })} /></Field><Field label="Nombre"><input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field><Field label="Teléfono"><input value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></Field><Field label="Usuario asociado">{rol === 'ADMINISTRADOR'
  ? <Select value={form.userId} onChange={v => setForm({ ...form, userId: v })}>
      <option value="">Sin cuenta (mostrador)</option>
      {editing?.userId && <option value={editing.userId}>{editing.correoCuenta}</option>}
      {cuentas.filter(c => String(c.idUsuario) !== editing?.userId)
        .map(c => <option key={c.idUsuario} value={c.idUsuario}>{c.correo}</option>)}
    </Select>
  : <input readOnly value={form.correoCuenta ?? 'Sin cuenta (mostrador)'} />}</Field></div></section>{error && <div className="form-error form-error-wide">{error}</div>}<div className="form-actions"><button className="primary" disabled={guardando}>Guardar cliente</button><button type="button" className="secondary" onClick={onCancel}>Cancelar</button></div></form> }

type RolPerfiles = 'ADMINISTRADOR' | 'OPERADOR' | 'CLIENTE'
type CuentaVinculable = { idUsuario: number; correo: string }
type PerfilApi = { idPerfil: number; documento: string; nombre: string; telefono: string | null;
  idUsuario: number | null; correoCuenta: string | null }

function clienteDeApi(p: PerfilApi): Client {
  return { id: String(p.idPerfil), document: p.documento, name: p.nombre, phone: p.telefono ?? '',
    userId: p.idUsuario === null ? '' : String(p.idUsuario), correoCuenta: p.correoCuenta ?? undefined }
}

function PerfilesOracle() {
  const [datos, setDatos] = useState<{ perfiles: Client[]; cuentas: CuentaVinculable[]; rol: RolPerfiles }>()
  const [error, setError] = useState('')

  async function cuentasDisponibles(rol: RolPerfiles, signal?: AbortSignal): Promise<CuentaVinculable[]> {
    return rol === 'ADMINISTRADOR'
      ? (await solicitarCatalogo('perfiles', '/cuentas-vinculables', 'GET', undefined, signal)).json() : []
  }

  useEffect(() => {
    const control = new AbortController()
    async function cargar() {
      try {
        const sesion = await fetch('/api/auth/sesion', { cache: 'no-store', signal: control.signal })
        if (!sesion.ok) throw new Error('La sesión expiró. Vuelve a iniciar sesión.')
        const { rol } = await sesion.json()
        const [perfiles, cuentas] = await Promise.all([
          solicitarCatalogo('perfiles', '', 'GET', undefined, control.signal).then(r => r.json()),
          cuentasDisponibles(rol, control.signal),
        ])
        if (!control.signal.aborted) setDatos({ perfiles: perfiles.map(clienteDeApi), cuentas, rol })
      } catch (error) {
        if (!control.signal.aborted) setError((error as Error).message)
      }
    }
    void cargar()
    return () => control.abort()
  }, [])

  if (error) return <div role="alert" className="form-error">{error}</div>
  if (!datos) return <p role="status">Consultando clientes en Oracle…</p>

  function refrescarCuentas() {
    void cuentasDisponibles(datos!.rol).then(cuentas =>
      setDatos(actual => actual && { ...actual, cuentas })).catch(() => {})
  }

  return <ClientsPage clients={datos.perfiles} cuentas={datos.cuentas} rol={datos.rol}
    onConsultar={async id => clienteDeApi(await (await solicitarCatalogo('perfiles', '/' + id)).json())}
    onSave={async cliente => {
      const r = await solicitarCatalogo('perfiles', cliente.id ? '/' + cliente.id : '', cliente.id ? 'PUT' : 'POST', {
        documento: cliente.document, nombre: cliente.name, telefono: cliente.phone,
        idUsuario: cliente.userId ? Number(cliente.userId) : null,
      })
      const perfil = clienteDeApi(await r.json())
      setDatos(actual => actual && { ...actual, perfiles: [...actual.perfiles.filter(p => p.id !== perfil.id), perfil] })
      refrescarCuentas()
    }}
    onDelete={async cliente => {
      await solicitarCatalogo('perfiles', '/' + cliente.id, 'DELETE')
      setDatos(actual => actual && { ...actual, perfiles: actual.perfiles.filter(p => p.id !== cliente.id) })
      refrescarCuentas()
    }}
  />
}
async function vistaDeSesion(signal?: AbortSignal): Promise<View> {
  const sesion = await fetch('/api/auth/sesion', { cache: 'no-store', signal })
  if (!sesion.ok) return 'login'
  const { rol } = await sesion.json()
  return rol === 'CLIENTE' || rol === 'OPERADOR' ? 'clientes' : 'functions'
}

export function BoleteriaApp() { const [view, setView] = useState<View>('login'); const [sesionError, setSesionError] = useState('');
useEffect(() => { const solicitud = new AbortController(); void vistaDeSesion(solicitud.signal).then(vista => { if (!solicitud.signal.aborted && vista !== 'login') setView(vista) }).catch(() => {}); return () => solicitud.abort() }, []);
 const [mobile, setMobile] = useState(false); const [catalogs, setCatalogs] = useState(initialCatalogs); const [providers, setProviders] = useState(initialProviders); const [plans, setPlans] = useState(initialPlans); const [attractions, setAttractions] = useState(initialAttractions); const [combos, setCombos] = useState(initialCombos); const [functions, setFunctions] = useState(initialFunctions); const [users, setUsers] = useState<SystemUser[]>(initialUsers); const [clients, setClients] = useState<Client[]>(initialClients); const [reservations, setReservations] = useState<Reservation[]>([]); const [tickets, setTickets] = useState<Ticket[]>([]); useEffect(() => { const read = <T,>(key: string, fallback: T) => { const raw = localStorage.getItem(key); return raw ? JSON.parse(raw) as T : fallback }; setCatalogs(read('boleteria-catalogs', initialCatalogs)); setProviders(read('boleteria-providers', initialProviders)); setPlans(read('boleteria-plans', initialPlans)); setAttractions(read('boleteria-attractions', initialAttractions)); setCombos(read('boleteria-combos', initialCombos)); const storedFunctions = normalizeFunctions(read('boleteria-functions', initialFunctions)); setFunctions(storedFunctions); localStorage.setItem('boleteria-functions', JSON.stringify(storedFunctions)); setUsers(read('boleteria-users', initialUsers)); setClients(read('boleteria-clients', initialClients)); const migratedSales = migrateSalesData(read('boleteria-reservations', []), read('boleteria-tickets', []), storedFunctions); setReservations(migratedSales.reservations); setTickets(migratedSales.tickets); localStorage.removeItem('boleteria-auth') }, []); if (view === 'login') return <Login onLogin={() => { void vistaDeSesion().then(setView).catch(() => {}) }} />; const nav = (v: View) => { if (v === 'login') {
  setSesionError('')
  void cerrarSesion().then(() => setView('login')).catch(error => setSesionError(error.message))
  return
} setView(v); setMobile(false) }; return <div className="app-shell"><button className="mobile-menu" onClick={() => setMobile(!mobile)} aria-label="Abrir menú"><Menu /></button><div className={mobile ? 'sidebar-wrap open' : 'sidebar-wrap'}><Sidebar view={view} onNavigate={nav} /></div><main className="content">{sesionError && <div role="alert" className="form-error">{sesionError}</div>}{(view === 'municipios' || view === 'prestadorTypes' || view === 'ticketTypes') && <CatalogoOracle
  key={view}
  recurso={view === 'municipios' ? 'municipios' : view === 'prestadorTypes' ? 'tipos-prestador' : 'tipos-entrada'}
  listado={props => <CatalogList kind={view} {...props} />}
  formulario={props => <CatalogForm kind={view} {...props} />}
/>}{view === 'roles' && <UsuariosOracle key='roles' soloRoles />}{view === 'prestadores' && <PrestadoresOracle />}{(view === 'planes' || view === 'atracciones') && <OfertaOracle key={view} kind={view} />}{view === 'combos' && <CombosOracle />}{view === 'usuarios' && <UsuariosOracle key='usuarios' soloRoles={false} />}{view === 'clientes' && <PerfilesOracle />}{view === 'notificaciones' && <NotificationsModule onBack={() => setView('functions')} />}{view === 'reservas' && <ReservasOracle />}{view === 'boletas' && <BoletasOracle />}{(view === 'functions' || view === 'form') && <FuncionesOracle />}{view === 'reports' && <ReportsDashboard attractions={attractions} functions={functions} reservations={reservations} tickets={tickets} clients={clients} providers={providers} municipalities={catalogs.municipios} providerTypes={catalogs.prestadorTypes} ticketTypes={catalogs.ticketTypes} combos={combos} onBack={() => setView('functions')} />}{false && <><Header title="Aforo vendido por función" /><div className="report-filters"><Field label="Atracción"><Select value="Todas" onChange={() => {}}><option>Todas</option>{attractions.map(a => <option key={a.id}>{a.name}</option>)}</Select></Field><Field label="Desde"><input type="date" /></Field><Field label="Hasta"><input type="date" /></Field><button className="primary"><Search size={17} /> Consultar</button><button className="secondary" onClick={() => window.print()}><FileDown size={17} /> Exportar PDF</button></div><div className="table-card"><table><thead><tr><th>Atracción</th><th>Función</th><th>Zona</th><th>Aforo</th><th>Vendidas</th><th>Disponibles</th><th>Ocupación</th></tr></thead><tbody>{functions.flatMap(f => f.zones.map((z, i) => <tr key={`${f.id}-${z.name}`}><td>{attractions.find(a => a.id === f.attractionId)?.name || (f.attractionId === 'legacy-parque' ? 'Parque del Café' : 'Jardín Botánico del Quindío')}</td><td>{dateLabel(f.date)}</td><td>{z.name}</td><td>{z.capacity}</td><td>{tickets.filter(t => t.zoneId === (z.id || `zone-${f.id}-${z.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`) && t.status !== 'ANULADA').length}</td><td>{Math.max(0, z.capacity - tickets.filter(t => t.zoneId === (z.id || `zone-${f.id}-${z.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`) && t.status !== 'ANULADA').length)}</td><td>{Math.round(tickets.filter(t => t.zoneId === (z.id || `zone-${f.id}-${z.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`) && t.status !== 'ANULADA').length / z.capacity * 100)} %</td></tr>))}</tbody></table></div></>}</main></div> }





