import { proxyCatalogo } from '@/lib/proxy-catalogo'

type Context = { params: Promise<{ segmento?: string[] }> }
async function reenviar(request: Request, context: Context) {
  const { segmento = [] } = await context.params
  const ruta = segmento.join('/')
  const permitida = request.method === 'GET' ? /^(?:codigo|\d+)?$/.test(ruta) : /^\d+\/(?:usar|anular|anular-combo)$/.test(ruta)
  return permitida ? proxyCatalogo(request, 'boletas', ruta || undefined) : new Response(null, { status: 404 })
}
export { reenviar as GET, reenviar as POST }


