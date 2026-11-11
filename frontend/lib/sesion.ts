async function enviar(accion: 'login' | 'logout', body?: URLSearchParams) {
  const csrf = await fetch('/api/auth/csrf', { cache: 'no-store' })
  if (!csrf.ok) throw new Error('No fue posible conectar con el servicio de identidad.')
  const { token } = await csrf.json()
  return fetch('/api/auth/' + accion, {
    method: 'POST',
    headers: { 'X-CSRF-TOKEN': token },
    body,
  })
}

export async function iniciarSesion(correo: string, clave: string) {
  const response = await enviar('login', new URLSearchParams({ correo, clave }))
  if (response.status === 401) throw new Error('Correo o contraseña incorrectos.')
  if (!response.ok) throw new Error('No fue posible iniciar sesión. Inténtalo de nuevo.')
}

export async function cerrarSesion() {
  if (!(await enviar('logout')).ok) throw new Error('No fue posible cerrar sesión. Inténtalo de nuevo.')
}

export async function tieneSesion(signal: AbortSignal) {
  return (await fetch('/api/auth/sesion', { cache: 'no-store', signal })).ok
}
