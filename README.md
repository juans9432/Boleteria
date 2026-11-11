# Boletería · Instalación local

Guía para una instalación limpia en Windows x64 con PowerShell y Docker Desktop en modo de contenedores Linux. Oracle corre en Docker. Spring Boot y Next.js se ejecutan en el equipo. Los comandos de cada sección parten de la raíz del proyecto, salvo donde se indica otra carpeta.

## 1. Requisitos

| Herramienta | Versión o configuración |
| --- | --- |
| JDK | Java 17. Configura `JAVA_HOME` y el `PATH` para que Maven también use Java 17. |
| Maven | Maven 3.9.x disponible en el `PATH`. |
| Node.js | Node.js 24 recomendado para reproducir el entorno del proyecto. |
| pnpm | `12.3.4`, versión declarada en `frontend/package.json`. |
| Docker | Docker Desktop iniciado con motor de contenedores Linux. |
| Oracle | Oracle Database XE 21c, imagen `gvenzl/oracle-xe:21.3.0-slim` para x86-64. La base conectable es `XEPDB1`. |

```powershell
java -version
mvn -version
node --version
npm install --global pnpm@12.3.4
pnpm --version
docker version
```

`docker version` debe mostrar cliente y servidor. En un equipo ARM utiliza un equipo o una máquina virtual x86-64 para Oracle XE. [Imagen Oracle XE y plataformas](https://github.com/gvenzl/oci-oracle-xe) · [Requisitos de Next.js](https://nextjs.org/docs/app/getting-started/installation)

## 2. Crear Oracle y el esquema

En un equipo nuevo, elige dos claves de base de datos diferentes y guárdalas en un gestor. Una es para la administración de Oracle y la otra para el esquema de la aplicación. Para esta inicialización usa claves largas generadas con letras y números, compatibles con el inicializador del contenedor. La contraseña de inicio de sesión de Boletería se elige aparte en el paso 4.

```powershell
$claveOracle = Read-Host 'Clave de administración de Oracle' -AsSecureString
$claveEsquema = Read-Host 'Clave del esquema BOLETERIA_FINAL' -AsSecureString
try {
    $env:ORACLE_PASSWORD = [System.Net.NetworkCredential]::new('', $claveOracle).Password
    $env:APP_USER_PASSWORD = [System.Net.NetworkCredential]::new('', $claveEsquema).Password
    docker run -d --name boleteria-oracle -p 127.0.0.1:1521:1521 -v boleteria-oradata:/opt/oracle/oradata -e ORACLE_PASSWORD -e APP_USER=BOLETERIA_FINAL -e APP_USER_PASSWORD --health-cmd healthcheck.sh --health-interval 10s --health-timeout 5s --health-retries 30 gvenzl/oracle-xe:21.3.0-slim
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar Oracle.' }
} finally {
    Remove-Item Env:ORACLE_PASSWORD,Env:APP_USER_PASSWORD -ErrorAction SilentlyContinue
    $claveOracle.Dispose()
    $claveEsquema.Dispose()
}
docker ps --filter name=boleteria-oracle
```

Espera a que el contenedor indique `healthy`. La imagen crea el usuario `BOLETERIA_FINAL` en `XEPDB1` al inicializar el volumen. El volumen conserva la base entre arranques. Usa `docker start boleteria-oracle` para iniciar el mismo contenedor en otra sesión. Los valores de inicialización se pasan al entorno de un contenedor local. Limita el acceso al motor Docker a usuarios de confianza.

Desde la raíz, copia los SQL al contenedor y abre SQL*Plus. Cuando aparezca `Enter password`, escribe la clave del esquema.

```powershell
docker cp ./database boleteria-oracle:/tmp/boleteria-sql
docker exec -it boleteria-oracle sqlplus -L 'BOLETERIA_FINAL@//localhost:1521/XEPDB1'
```

Dentro de SQL*Plus, confirma primero que `SHOW USER` muestre `BOLETERIA_FINAL` y `SHOW CON_NAME` muestre `XEPDB1`. Después ejecuta el bloque de instalación una sola vez en el esquema vacío.

```sql
SHOW USER
SHOW CON_NAME
```

```sql
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
@/tmp/boleteria-sql/01_creacion_tablas.sql
@/tmp/boleteria-sql/03_indices.sql
@/tmp/boleteria-sql/04_datos_iniciales.sql
SELECT COUNT(*) AS TABLAS FROM USER_TABLES;
SELECT COUNT(*) AS MUNICIPIOS FROM MUNICIPIO;
SELECT COUNT(*) AS USUARIOS FROM USUARIO_SISTEMA;
SELECT COUNT(*) AS ROLES FROM ROL;
EXIT
```

La carga limpia debe mostrar **17 tablas, 6 municipios, 9 usuarios y 4 roles**. `05_reportes.sql` contiene consultas opcionales. `06_pruebas.sql` incluye operaciones de prueba y se reserva para un esquema de ensayo. `02_reglas_de_negocio.sql` deshabilita triggers de la instalación antigua y `02_reglas_negocio_respaldo.sql` conserva sus definiciones como referencia. La migración de identidad es para bases anteriores a ese ajuste, que ya está incorporado en los scripts de instalación limpia.

**Diferencia con el esquema existente.** La carga original incluía ocho cuentas (un administrador, un operador y seis clientes). La carga limpia actual agrega una novena, el encargado de demostración. La migración de identidad del esquema existente agrega el rol y habilita la asignación a prestadores, pero conserva las cuentas existentes. Por eso el esquema migrado verificado tiene ocho cuentas aunque el catálogo contenga cuatro roles.

**Pendiente para la integración final.** Ejecutar esta guía de principio a fin con un contenedor y volumen nuevos. La revisión inicial contrastó los comandos con el proyecto y comprobó el lockfile, pero el motor Docker estaba detenido durante esa revisión.

**Reservas y ampliación de la Entrega 2.** El modelo original tenía 16 tablas. La instalación actual agrega RESERVA_DETALLE para conservar las selecciones y la cotización orientativa de las pendientes durante 15 minutos. Guardar una pendiente no aparta cupo ni emite boletas. El Reporte 3 identifica las compras confirmadas y los resúmenes pendientes con etiquetas y campos de precio separados.

En una instalación limpia, 01_creacion_tablas.sql y 03_indices.sql ya incluyen la tabla y sus índices. Ejecuta database/migraciones/02_reserva_detalle.sql una sola vez únicamente al actualizar un esquema anterior que carece de RESERVA_DETALLE. Consulta [el diseño de Reservas](RESERVAS.md) para revisar el alcance y las transacciones.

**Ventas y trazabilidad de la emisión.** BOLETA incorpora LINEA_RESERVA y una clave foránea hacia RESERVA_DETALLE. El índice único condicional permite una sola boleta nueva por línea y conserva sin vínculo las boletas anteriores. El modelo sigue teniendo 17 tablas. La instalación limpia ya incluye la columna, la clave foránea y los índices. Aplica database/migraciones/03_boleta_linea_reserva.sql solo para actualizar una base que ya tenga RESERVA_DETALLE y aún carezca de LINEA_RESERVA. Consulta [el diseño de Ventas](VENTAS.md) para ver las reglas de confirmación y los bloqueos.

**Bandeja de notificaciones.** La instalación limpia incluye CLAVE_EVENTO y su índice único. En esquemas existentes sin esa columna aplica una sola vez database/migraciones/04_notificacion_clave_evento.sql. El esquema sigue teniendo 17 tablas. Consulta [el diseño de Notificaciones](NOTIFICACIONES.md).

## 3. Configurar y compilar Spring Boot

Desde la raíz, abre una consola para el backend. Usa la clave del esquema `BOLETERIA_FINAL`, que es la misma que acabas de usar en SQL*Plus.

```powershell
Set-Location backend
$env:BOLETERIA_DB_URL = 'jdbc:oracle:thin:@//localhost:1521/XEPDB1'
$env:BOLETERIA_DB_USER = 'BOLETERIA_FINAL'
$claveEsquema = Read-Host 'Clave del esquema BOLETERIA_FINAL' -AsSecureString
try {
    $env:BOLETERIA_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $claveEsquema).Password
} finally {
    $claveEsquema.Dispose()
}
mvn -DskipTests package
```

Espera `BUILD SUCCESS`. Mantén esta consola para los dos pasos siguientes.

## 4. Aprovisionar el administrador

El usuario de demostración es **admin@boleteria.example**. Define una clave exclusiva para Boletería, de al menos 12 caracteres y hasta 72 bytes UTF-8. Guárdala en tu gestor antes de introducirla en el prompt oculto. El perfil actualiza la cuenta pendiente mediante BCrypt con costo 12.

```powershell
$env:BOLETERIA_CUENTA_CORREO = 'admin@boleteria.example'
$claveCuenta = Read-Host 'Nueva clave del administrador de Boletería' -AsSecureString
try {
    $env:BOLETERIA_CUENTA_CLAVE = [System.Net.NetworkCredential]::new('', $claveCuenta).Password
    java -jar target/boleteria-backend-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --spring.profiles.active=provisionar-clave
    if ($LASTEXITCODE -ne 0) { throw 'Falló el aprovisionamiento.' }
} finally {
    Remove-Item Env:BOLETERIA_CUENTA_CORREO,Env:BOLETERIA_CUENTA_CLAVE -ErrorAction SilentlyContinue
    $claveCuenta.Dispose()
}
```

El mensaje esperado es **Clave inicial configurada.** Si la cuenta ya se aprovisionó, inicia sesión con la contraseña elegida. Puedes cambiarla desde **Usuarios del sistema** con una sesión administrativa.

## 5. Arrancar backend y frontend

En la consola del backend, desde `backend` y con la conexión configurada:

```powershell
java -jar target/boleteria-backend-0.1.0-SNAPSHOT.jar
```

Abre otra consola desde la raíz para Next.js:

```powershell
Set-Location frontend
$env:BOLETERIA_API_URL = 'http://127.0.0.1:8080'
pnpm install --frozen-lockfile
pnpm dev --hostname 127.0.0.1 --port 3000
```

| Servicio | Dirección local |
| --- | --- |
| Oracle XE | `localhost:1521/XEPDB1` |
| API Spring Boot | `http://127.0.0.1:8080` |
| Interfaz Next.js | `http://localhost:3000` |

Para una compilación de producción del frontend, ejecuta `pnpm exec tsc --noEmit`, `pnpm build` y después `pnpm start --hostname 127.0.0.1 --port 3000` desde `frontend`. Detén primero el servidor de desarrollo si ocupa ese puerto.

## 6. Verificar la instalación

1. En una consola aparte ejecuta `(Invoke-RestMethod http://127.0.0.1:8080/api/municipios).Count`. La carga inicial devuelve seis municipios.
2. Abre la interfaz e inicia sesión con **admin@boleteria.example** y la contraseña elegida en el paso 4. El profesor usa el formulario normal. Si otra persona instaló el proyecto, esa persona puede introducir la contraseña en el equipo de evaluación o compartirla por un canal privado.
3. Abre **Usuarios del sistema** y **Roles**. Comprueba que la cuenta administrativa figura activa y que se muestran cuatro roles. Usa **Cerrar sesión** y confirma que aparece el formulario de acceso.
4. Para ejecutar las pruebas acumulativas, usa una base de demostración y configura la conexión a Oracle en la consola de `backend`. Habilita las pruebas de Reservas con el siguiente bloque. La suite prueba el vencimiento directamente y desactiva el planificador periódico durante esta ejecución. Espera `BUILD SUCCESS`. Las pruebas de Ventas y Boletas se habilitan en el esquema aislado. La prueba de reversión de Ventas instala allí un disparador temporal. Consulta [la verificación aislada de Ventas](VENTAS.md#verificación-aislada) y [la guía de Boletas](BOLETAS.md) para ejecutar las 81 sin omisiones. Las pruebas de anulación y cancelación usan el mismo esquema aislado. Consulta [la política de cancelaciones](CANCELACIONES.md).

```powershell
$env:BOLETERIA_RESERVAS_QA = 'true'
$env:BOLETERIA_RESERVAS_EXPIRACION_ACTIVA = 'false'
try {
    mvn verify
    if ($LASTEXITCODE -ne 0) { throw 'Falló la suite de Maven.' }
} finally {
    Remove-Item Env:BOLETERIA_RESERVAS_QA,Env:BOLETERIA_RESERVAS_EXPIRACION_ACTIVA -ErrorAction SilentlyContinue
}
```

## 7. Problemas frecuentes

| Síntoma | Comprobación |
| --- | --- |
| Docker informa que el motor no responde | Inicia Docker Desktop, selecciona contenedores Linux y vuelve a ejecutar `docker version`. |
| Oracle no responde u ORA-12514 | Revisa `docker ps` y espera a que Oracle esté saludable. Comprueba el puerto publicado y usa el servicio `XEPDB1` en la URL JDBC. |
| ORA-01017 | Comprueba el usuario `BOLETERIA_FINAL` y vuelve a introducir la clave del esquema. Si el volumen ya existía, conserva las credenciales con las que se inicializó. |
| ORA-00942 u ORA-00955 durante la carga | Confirma esquema, PDB y orden de scripts. Oracle confirma los cambios DDL automáticamente. Si una carga se interrumpe, inspecciona los objetos ya creados antes de reintentar. |
| Maven usa un JDK incorrecto | Comprueba `mvn -version` y corrige `JAVA_HOME`. Para errores de descarga, revisa la conexión y el proxy configurado para Maven. |
| pnpm falla con el lockfile o durante una descarga | Comprueba Node.js y la versión fijada de pnpm. Ejecuta el comando en `frontend` con acceso al registro npm y conserva `pnpm-lock.yaml`. |
| Next.js muestra error al conectar con identidad | Verifica primero `/api/municipios` en Spring Boot. Comprueba `BOLETERIA_API_URL` y reinicia Next.js si cambiaste la variable. |
| Inicio de sesión rechazado | Usa el correo de demostración y la clave del paso 4. Para HTTP local deja `BOLETERIA_COOKIE_SECURE` sin definir. Si el aprovisionador indica que no hay cuenta pendiente, revisa si el esquema se cargó y si la cuenta ya fue aprovisionada. |
| Puerto ocupado | Libera el puerto o ajusta la configuración de ambos extremos. Si cambias el puerto de Spring con `SERVER_PORT`, actualiza `BOLETERIA_API_URL` en la consola de Next.js. Si cambias el puerto publicado de Oracle, actualiza `BOLETERIA_DB_URL`. |

Consulta el [README técnico del backend](backend/README.md) para detalles del módulo de identidad y de los demás módulos implementados.










