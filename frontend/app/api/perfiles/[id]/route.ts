import { proxyCatalogo } from '@/lib/proxy-catalogo'

async function item(request: Request, { params }: { params: Promise<{ id: string }> }) {
  return proxyCatalogo(request, 'perfiles', (await params).id)
}

export const GET = item
export const PUT = item
export const DELETE = item







