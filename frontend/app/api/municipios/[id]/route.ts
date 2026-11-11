import { proxyMunicipios } from '@/lib/proxy-municipios'

async function item(request: Request, { params }: { params: Promise<{ id: string }> }) {
  return proxyMunicipios(request, (await params).id)
}

export const GET = item
export const PUT = item
export const DELETE = item
