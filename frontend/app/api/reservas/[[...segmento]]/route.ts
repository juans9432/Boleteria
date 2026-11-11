import { proxyCatalogo } from '@/lib/proxy-catalogo'

type Context = { params: Promise<{ segmento?: string[] }> }
async function reenviar(request: Request, context: Context) {
  const { segmento = [] } = await context.params
  const ruta = segmento.join('/')
  const permitido = request.method === 'GET' ? /^(?:catalogos|\d+(?:\/cotizacion)?)?$/.test(ruta)
    : request.method === 'PUT' ? /^\d+$/.test(ruta)
    : /^(?:\d+\/(?:cancelar|cancelar-confirmada|confirmar))?$/.test(ruta)
  if (!permitido) return new Response(null, { status: 404 })
  return proxyCatalogo(request, 'reservas', ruta || undefined)
}
export { reenviar as GET, reenviar as POST, reenviar as PUT }



