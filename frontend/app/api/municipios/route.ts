import { proxyMunicipios } from '@/lib/proxy-municipios'

export function GET(request: Request) {
  return proxyMunicipios(request)
}

export function POST(request: Request) {
  return proxyMunicipios(request)
}
