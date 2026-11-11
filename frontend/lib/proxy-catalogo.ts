export async function proxyCatalogo(request: Request, recurso: 'municipios' | 'tipos-prestador' | 'tipos-entrada' | 'prestadores' | 'planes' | 'atracciones' | 'funciones' | 'zonas' | 'combos' | 'perfiles' | 'usuarios' | 'roles' | 'reservas' | 'boletas' | 'notificaciones', id?: string) {
  if (!(recurso === 'funciones' && request.method === 'POST' && /^\d+\/cancelar$/.test(id ?? '')) && recurso !== 'reservas' && recurso !== 'boletas' && recurso !== 'notificaciones' && id !== undefined && !(recurso === 'perfiles' && id === 'cuentas-vinculables' && request.method === 'GET') && !/^-?\d+$/.test(id)) {
    return Response.json({ detail: 'El ID debe ser numérico.' }, { status: 400 })
  }
  const headers = new Headers()
  for (const name of ['cookie', 'content-type', 'x-csrf-token']) {
    const value = request.headers.get(name)
    if (value) headers.set(name, value)
  }
  try {
    const backend = process.env.BOLETERIA_API_URL ?? 'http://127.0.0.1:8080'
    const path = '/api/' + recurso + (id === undefined ? '' : '/' + id)
    const destino = new URL(path, backend)
    if (recurso === 'zonas' && id === undefined && request.method === 'GET') {
      const idFuncion = new URL(request.url).searchParams.get('idFuncion')
      if (idFuncion !== null) destino.searchParams.set('idFuncion', idFuncion)
    }
    if (recurso === 'boletas' && id === 'codigo' && request.method === 'GET') {
      const valor = new URL(request.url).searchParams.get('valor')
      if (valor !== null) destino.searchParams.set('valor', valor)
    }
    const response = await fetch(destino, {
      method: request.method,
      headers,
      body: ['POST', 'PUT'].includes(request.method) ? await request.text() : undefined,
      cache: 'no-store',
      redirect: 'manual',
      signal: AbortSignal.timeout(10_000),
    })
    const outgoing = new Headers({ 'Cache-Control': 'no-store' })
    for (const name of ['content-type', 'location']) {
      const value = response.headers.get(name)
      if (value) outgoing.set(name, value)
    }
    return new Response(response.body, { status: response.status, headers: outgoing })
  } catch {
    return Response.json({ detail: 'No se pudo conectar con el backend.' }, { status: 503 })
  }
}















