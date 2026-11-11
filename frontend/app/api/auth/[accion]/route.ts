const acciones: Record<string, string> = { csrf: 'GET', sesion: 'GET', login: 'POST', logout: 'POST' }

async function proxy(request: Request, { params }: { params: Promise<{ accion: string }> }) {
  const { accion } = await params
  if (!Object.hasOwn(acciones, accion) || acciones[accion] !== request.method) {
    return new Response(null, { status: 404 })
  }

  const headers = new Headers()
  for (const name of ['cookie', 'content-type', 'x-csrf-token']) {
    const value = request.headers.get(name)
    if (value) headers.set(name, value)
  }

  try {
    const base = process.env.BOLETERIA_API_URL ?? 'http://127.0.0.1:8080'
    const response = await fetch(new URL('/api/auth/' + accion, base), {
      method: request.method,
      headers,
      body: request.method === 'POST' ? await request.text() : undefined,
      cache: 'no-store',
      redirect: 'manual',
      signal: AbortSignal.timeout(10_000),
    })
    const outgoing = new Headers({ 'Cache-Control': 'no-store' })
    const contentType = response.headers.get('content-type')
    if (contentType) outgoing.set('content-type', contentType)
    for (const cookie of response.headers.getSetCookie()) outgoing.append('Set-Cookie', cookie)
    return new Response(response.body, { status: response.status, headers: outgoing })
  } catch {
    return new Response(null, { status: 503, headers: { 'Cache-Control': 'no-store' } })
  }
}

export const GET = proxy
export const POST = proxy
