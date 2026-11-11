import { proxyCatalogo } from './proxy-catalogo'

export function proxyMunicipios(request: Request, id?: string) {
  return proxyCatalogo(request, 'municipios', id)
}
