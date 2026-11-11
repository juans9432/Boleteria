import { proxyCatalogo } from '@/lib/proxy-catalogo'

export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  return proxyCatalogo(request, 'funciones', (await params).id + '/cancelar')
}
