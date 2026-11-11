import { proxyCatalogo } from '@/lib/proxy-catalogo'

export function GET(request: Request) {
  return proxyCatalogo(request, 'atracciones')
}

export const POST = GET



