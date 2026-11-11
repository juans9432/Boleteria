# Etapa 1 · Municipios

La pantalla consulta Oracle a través de Next.js y Spring Boot. El botón
«Administrar datos locales» mantiene accesible el catálogo del prototipo
mientras se integran sus operaciones de escritura y los módulos relacionados.

## Ejecutar

En PowerShell, con Java 17, Maven y Oracle XE iniciado, entra en backend.
Ejecuta por separado la línea que pide la contraseña y escríbela cuando
PowerShell la solicite.

```powershell
$claveOracle = Read-Host 'Contraseña de Oracle' -AsSecureString
$env:BOLETERIA_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $claveOracle).Password
mvn test
mvn spring-boot:run
```

BOLETERIA_DB_URL y BOLETERIA_DB_USER pueden personalizarse. Sus valores
predeterminados apuntan a localhost:1521/XEPDB1 y BOLETERIA_FINAL.

En otra consola, entra en frontend y ejecuta pnpm install --frozen-lockfile
y pnpm dev. El proxy Next.js usa http://127.0.0.1:8080 por defecto. Se puede
cambiar mediante BOLETERIA_API_URL en el entorno de Next.js. Abre
http://localhost:3000 y entra a Municipios desde el menú original. Para iniciar sesión con Oracle, sigue el procedimiento de la etapa 2.

## Verificación

Con la contraseña configurada, Maven compara el JSON del controlador con una
consulta independiente a Oracle. Sin ella, se ejecutan las pruebas con dobles
JDBC y las unitarias, y se omite la prueba contra Oracle.

Para comprobar la pantalla, compara las filas con esta consulta:

```sql
SELECT ID_MUNICIPIO, NOMBRE, DEPARTAMENTO
FROM BOLETERIA_FINAL.MUNICIPIO
ORDER BY NOMBRE, ID_MUNICIPIO;
```

## Decisiones pendientes del modelo

- PERFIL_CLIENTE.ID_USUARIO es obligatorio y único. Un cliente de mostrador
  sin cuenta no se puede registrar con el DDL actual. Evaluar una FK
  opcional conservando UNIQUE para las cuentas asociadas y revisar las
  consultas con INNER JOIN a USUARIO_SISTEMA.
- USUARIO_SISTEMA tiene un rol, pero el esquema carece de una relación con
  PRESTADOR. Antes de implementar la autorización por prestador, confirmar
  si un encargado puede gestionar varios y elegir una FK o tabla de
  asociación conforme a esa cardinalidad.
- Cualquier migración futura debe ensayarse en un esquema de prueba con
  datos existentes y verificaciones de integridad antes de aplicarse.

## Transacciones

Municipios ejecuta una sola sentencia de lectura, con la consistencia de
lectura de Oracle. Para operaciones futuras con varias escrituras, delimitar
la transacción por caso de uso desde infraestructura y compartir su conexión.
La venta y el cambio de aforo deberán bloquear la misma fila de ZONA antes
de leer la ocupación. El límite por cliente y función necesita además una
clave común de serialización. El orden de bloqueos y las carreras entre
operaciones se probarán en Oracle antes de habilitar esas escrituras.

## Etapa 2 · Identidad

En una instalación existente, ejecuta database/migraciones/01_identidad.sql
conectado como el dueño de las tablas en XEPDB1. En una instalación limpia
ejecuta 01, 03 y 04 en ese orden. En SQL*Plus habilita SET SQLBLANKLINES ON
y SET DEFINE OFF antes de ejecutar los scripts. El archivo
database/migraciones/verificar_identidad.sql prueba el modelo con registros
temporales. Ejecútalo en un esquema de prueba con datos de demostración.

La migración agrega el rol ENCARGADO_PRESTADOR. Las cuentas anteriores
conservan sus datos y no se asignan automáticamente a un prestador.
Un encargado requiere una única asignación válida. Administradores pueden
administrar prestadores. Operadores y clientes no reciben ese permiso.
Los módulos del prototipo que siguen en localStorage aún no tienen
operaciones protegidas en Oracle.

### Aprovisionar el administrador de demostración

En una instalación limpia, la cuenta administrativa es
`admin@boleteria.example`. El aprovisionamiento actualiza únicamente la
credencial de la cuenta pendiente. Conserva su ID, rol y estado. Usa BCrypt
con costo 12. La clave debe tener al menos 12 caracteres y un máximo de
72 bytes UTF-8.

1. En PowerShell, entra a la carpeta `backend` y verifica que `java -version`
   indique Java 17. Compila con `mvn -DskipTests package`.
2. Con `BOLETERIA_DB_PASSWORD` definida para la conexión a `BOLETERIA_FINAL`,
   ejecuta el bloque siguiente. Elige una clave única y guárdala en tu gestor
   de contraseñas antes de ingresarla en el cuadro oculto.

```powershell
$env:BOLETERIA_CUENTA_CORREO = 'admin@boleteria.example'
$claveCuenta = Read-Host 'Nueva clave del administrador' -AsSecureString
try {
    $env:BOLETERIA_CUENTA_CLAVE = [System.Net.NetworkCredential]::new('', $claveCuenta).Password
    java -jar target/boleteria-backend-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --spring.profiles.active=provisionar-clave
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo aprovisionar la cuenta.' }
} finally {
    Remove-Item Env:BOLETERIA_CUENTA_CORREO,Env:BOLETERIA_CUENTA_CLAVE -ErrorAction SilentlyContinue
    $claveCuenta.Dispose()
}
```

3. Ejecuta `mvn spring-boot:run` en `backend`. En otra consola, desde
   `frontend`, ejecuta `pnpm install --frozen-lockfile` y `pnpm dev`.
   Abre `http://localhost:3000` e inicia sesión con el correo y la clave configurada. Abre
   **Usuarios del sistema** y **Roles**. Usa **Cerrar sesión** y verifica
   que el navegador regrese al formulario de acceso.
4. Para sustentar el comportamiento tras reiniciar, inicia sesión otra vez
   y reinicia solo Spring Boot. Una petición a `/api/usuarios` con la
   sesión anterior debe devolver `401`. Inicia sesión de nuevo y verifica
   que la petición devuelva `200`. La sesión HTTP se guarda en memoria
   del servidor y se pierde al reiniciar. Durante la ejecución, cada
   solicitud autenticada contrasta la cuenta con Oracle.

En una base ya aprovisionada, cambia la contraseña desde **Usuarios del
sistema** mientras tengas una sesión administrativa. Después vuelve a
iniciar sesión con la nueva clave. Para la sustentación captura la lista de
roles, el retorno al formulario tras cerrar sesión y los códigos HTTP.
Mantén fuera de las capturas las contraseñas, hashes, cookies y tokens.
Define `BOLETERIA_COOKIE_SECURE=true` cuando el frontend se sirva por HTTPS.

Con BOLETERIA_DB_PASSWORD definida, mvn test prueba identidad contra Oracle
y crea y elimina cuentas temporales de prueba. La integración necesita
al menos dos prestadores en el esquema.

## Etapa 3 · CRUD de Municipios

Los GET de la colección y de un ID conservan el acceso público. Crear,
actualizar y eliminar requieren una sesión de administrador y un token
CSRF. El catálogo es global, por lo que la asignación a un prestador no
otorga permisos de escritura. Oracle rechaza el borrado de un municipio
referenciado por un prestador y la API responde 409.

La vista Municipios conserva la tabla y el formulario originales. Sus
operaciones usan Oracle. Los demás módulos del prototipo mantienen sus
datos locales mientras esperan su propia integración. Con credenciales
Oracle, mvn test ejecuta el CRUD contra datos temporales que limpia al final.

## Etapa 3 · Tipos de prestador

GET /api/tipos-prestador y GET /api/tipos-prestador/{id} son públicos.
POST, PUT y DELETE requieren un administrador y CSRF. Oracle conserva la
unicidad del nombre y protege las referencias de PRESTADOR. La API responde
409 con un detalle distinto para cada conflicto.

La pantalla Tipos de prestador usa Oracle. La pantalla de Prestadores
sigue usando su propio catálogo local hasta que se migre ese módulo.
Las pruebas de Maven contra Oracle crean y limpian datos temporales.

## Etapa 3 · Prestadores

El administrador puede listar, consultar, crear, actualizar y eliminar
prestadores. El encargado solo lista, consulta y modifica su prestador.
La aplicación comprueba la asignación a partir de la cuenta autenticada.
Las escrituras requieren CSRF. El NIT duplicado y el borrado de un
prestador referenciado devuelven 409. Las referencias a municipios o tipos
inexistentes devuelven 400.

El formulario carga ambos catálogos de Oracle. Planes y Atracciones
conservan sus datos locales mientras esperan su propia integración.

## Etapa 3 · Planes turísticos

El administrador gestiona todos los planes. El encargado solo gestiona los
planes de su prestador, incluida la baja de planes sin reservas. La API
deriva el alcance de la sesión y protege las escrituras con CSRF.

Oracle conserva el ON DELETE SET NULL original. La API rechaza con 409
la baja de cualquier plan referenciado por una reserva. Cambiar el estado a
INACTIVO conserva la relación. Durante la comprobación y el borrado se toma
un bloqueo SHARE ROW EXCLUSIVE sobre RESERVA con espera máxima de cinco
segundos, dentro de la misma transacción. El bloqueo es de tabla y
serializa brevemente los borrados con las escrituras de reservas.

El formulario de Planes usa prestadores de Oracle. Atracciones y los demás
módulos pendientes siguen usando sus datos locales.

## Etapa 3 · Atracciones

El administrador gestiona todas las atracciones. El encargado solo gestiona
las de su prestador y no puede reasignarlas. La API toma el alcance de la
cuenta autenticada. El formulario carga los prestadores permitidos desde
Oracle y la descripción es opcional.

Las claves foráneas de FUNCION y COMBO_ATRACCION impiden borrar una
atracción referenciada. La API traduce el rechazo de Oracle a HTTP 409.
Los módulos pendientes todavía usan sus datos locales y pueden mostrar
un catálogo de atracciones distinto al de Oracle hasta que se integren.

## Etapa 3 · Funciones

El formulario usa las atracciones de Oracle que corresponden a la cuenta.
El administrador gestiona todas las funciones y el encargado solo las de
su prestador. La API valida fecha ISO, horas HH:mm y apertura antes del cierre.

En esta etapa se crean y modifican funciones PROGRAMADA. CANCELADA y
REALIZADA se consultan como históricas. La cancelación coordinada con la
anulación de boletas queda para Ventas. Zonas sigue fuera de esta subetapa.

Para una misma atracción y día, se permite que el cierre coincida con la
apertura siguiente. Las canceladas no bloquean horarios. El backend bloquea
la atracción dentro de la transacción antes de comprobar cruces y escribir.
Las escrituras externas deben respetar el mismo protocolo de bloqueo.

La edición conserva la programación cuando existen boletas. La baja solo se
permite para funciones programadas sin zonas, porque Oracle elimina ZONA
en cascada y las boletas apuntan a ZONA. Se comprueba la dependencia con un
bloqueo breve de ZONA dentro de la transacción del borrado. Al incorporar
Ventas, la emisión de boletas deberá coordinarse con el bloqueo de FUNCION
para serializar ventas y cambios de programación.

## Etapa 3 · Zonas y aforo

El editor de una función guardada carga sus zonas de Oracle. Las zonas de
funciones canceladas o realizadas son de solo lectura. La zona permanece
en la función donde se creó. El administrador gestiona todas las zonas
y el encargado solo las de su prestador.

El aforo no puede quedar por debajo de las boletas VENDIDA o USADA.
Cambiar la tarifa solo actualiza ZONA.PRECIO. Eliminar una zona con
cualquier boleta, incluida una ANULADA, devuelve 409. Oracle también
garantiza la unicidad exacta del nombre dentro de la función.

Las escrituras bloquean FUNCION, luego ATRACCION para comprobar el
prestador, y después ZONA cuando la fila existe. El conteo y la escritura
usan la misma transacción. Ventas deberá adquirir FUNCION antes de ZONA y
mantener ambos bloqueos mientras comprueba el aforo y registra la boleta.
Para ventas que toquen varias funciones, el orden de adquisición deberá
ser estable. Las escrituras directas que omitan el protocolo quedan fuera
de esta garantía.

## Etapa 3 · Combos turísticos

La plataforma es dueña del catálogo de combos. El modelo no asigna un
prestador al combo y sus atracciones pueden pertenecer a varios prestadores.
Por eso el módulo administrativo, incluidas sus consultas, está restringido
a ADMINISTRADOR. La futura consulta comercial de combos activos pertenece
al flujo de Ventas.

Un combo se crea INACTIVO. Un borrador puede estar vacío o incompleto.
Para activar se exige una suma exacta de 100 %. Los porcentajes de cada
atracción respetan 1 a 100 y dos decimales. Si el combo está activo,
inactívalo antes de modificar su composición. El mismo formulario guarda
el estado, los datos y las relaciones. Una actualización de borrador puede
completar la composición y activar en una sola transacción.

La aplicación bloquea el combo al actualizar o eliminar. Si una referencia
de atracción falla, se revierten tanto el combo como sus relaciones.
Las boletas históricas bloquean el borrado con 409 incluso si están anuladas.
La inactivación conserva la referencia y cambiar la tarifa del combo no
modifica PRECIO_PAGADO. Ventas deberá verificar y bloquear el combo activo
al emitir boletas para coordinarse con estas operaciones.

## Etapa 3 · Perfiles de cliente

El administrador gestiona perfiles y vincula cuentas activas de rol CLIENTE.
El operador registra, consulta y actualiza perfiles de mostrador sin cuenta.
El cliente solo consulta su propio perfil y edita nombre y teléfono.
El encargado no tiene acceso al catálogo de perfiles, que no pertenece a
ningún prestador. Solo el administrador consulta las cuentas vinculables.

La cuenta es opcional. Oracle conserva la unicidad de documento y de
cuenta vinculada. Una reserva existente impide borrar el perfil, cambiar su
documento o sustituir o desvincular una cuenta ya asociada. El administrador
puede vincular por primera vez un perfil de mostrador tras verificar al
titular. El sistema comprueba que la cuenta exista, esté activa y sea de
rol CLIENTE. La identidad del titular se verifica fuera del sistema porque
USUARIO_SISTEMA no almacena el documento.

La pantalla Clientes usa Oracle. Reservas, Boletas y Notificaciones siguen
en sus subetapas pendientes.

## Etapa 3 · Tipos de entrada

El listado y la consulta por ID son públicos, como los demás catálogos
básicos. Crear, actualizar y eliminar requieren ADMINISTRADOR y CSRF.
El descuento acepta de 0 a 100 con hasta dos decimales y REQUIERE_ADULTO
se guarda como S o N. Oracle garantiza el nombre único.

La edición actualiza únicamente TIPO_ENTRADA. Las boletas existentes
conservan PRECIO_PAGADO. La clave foránea impide borrar un tipo referenciado
por cualquier boleta, incluso anulada, y la API responde 409.

La pantalla Tipos de entrada usa Oracle. Las pantallas locales pendientes
conservan sus catálogos hasta sus respectivas subetapas.

## Administración de usuarios y roles

La pantalla **Roles** muestra los cuatro roles de Oracle como catálogo de solo lectura. Un administrador puede crear cuentas y editar correo, rol, estado y prestador desde **Usuarios del sistema**. La baja se realiza desactivando la cuenta. Un encargado requiere un prestador existente y una cuenta vinculada a un perfil conserva el rol CLIENTE.

Para crear una cuenta o restablecer su contraseña, el administrador introduce una contraseña única generada por un gestor de contraseñas en el formulario. Usa al menos 12 caracteres y un máximo de 72 bytes UTF-8. En producción, accede mediante HTTPS y configura la cookie segura. Comparte la credencial con su titular mediante el canal seguro de tu organización. Para editar otros datos, deja vacío el campo de contraseña. El backend almacena el hash BCrypt y las respuestas HTTP omiten tanto la contraseña como su hash.

La edición se ejecuta en una transacción Oracle. El bloqueo de la fila del rol ADMINISTRADOR serializa los cambios que afectan al último administrador activo con credenciales utilizables. La fila de la cuenta se bloquea para coordinar la edición con la vinculación de perfiles. El backend contrasta la sesión con el estado actual en Oracle en cada solicitud autenticada. Los cambios de rol, prestador, estado, correo o contraseña requieren iniciar sesión de nuevo. La generación de revocación adicional se mantiene en memoria en la instancia que atendió el cambio.



