import { proxyCatalogo } from '@/lib/proxy-catalogo'

type Context = { params: Promise<{ segmento?: string[] }> }
async function reenviar(request: Request, context: Context) {
  const { segmento = [] } = await context.params
  const ruta = segmento.join('/')
  const permitida = request.method === 'GET' ? /^\d*$/.test(ruta) : /^\d+\/leer$/.test(ruta)
  return permitida ? proxyCatalogo(request, 'notificaciones', ruta || undefined) : new Response(null, { status: 404 })
}
export { reenviar as GET, reenviar as POST }

