import { proxyCatalogo } from '@/lib/proxy-catalogo'

export function GET(request: Request) {
  return proxyCatalogo(request, 'roles')
}
