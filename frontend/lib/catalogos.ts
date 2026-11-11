export async function solicitarCatalogo(recurso: 'municipios' | 'tipos-prestador' | 'tipos-entrada' | 'prestadores' | 'planes' | 'atracciones' | 'funciones' | 'zonas' | 'combos' | 'perfiles' | 'usuarios' | 'roles' | 'reservas' | 'boletas' | 'notificaciones', path = '', method = 'GET', datos?: Record<string, unknown>, signal?: AbortSignal) {
  const headers = new Headers()
  if (method !== 'GET') {
    const csrf = await fetch('/api/auth/csrf', { cache: 'no-store' })
    if (!csrf.ok) throw new Error('No se pudo validar la sesión.')
    headers.set('X-CSRF-TOKEN', (await csrf.json()).token)
  }
  if (datos) headers.set('Content-Type', 'application/json')
  const respuesta = await fetch('/api/' + recurso + path, {
    method, headers, signal, cache: 'no-store',
    body: datos ? JSON.stringify(datos) : undefined,
  })
  if (respuesta.ok) return respuesta

  const detalle = await respuesta.json().catch(() => null)
  const mensajes: Record<number, string> = {
    400: 'Revisa los datos ingresados.',
    401: 'La sesión expiró. Vuelve a iniciar sesión.',
    403: 'Tu cuenta no tiene permiso para modificar este catálogo.',
    404: 'El registro ya no existe.',
    409: 'La operación entra en conflicto con los datos existentes.',
  }
  throw Object.assign(new Error(detalle?.detail || mensajes[respuesta.status] || 'No fue posible completar la operación.'), { detalle })
}















