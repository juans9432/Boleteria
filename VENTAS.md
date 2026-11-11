# Ventas · Subetapa 1

Cotizar y confirmar trabajan sobre la reserva existente. El cliente acepta una revisión SHA-256 de la selección y de los importes por línea. La revisión sirve para detectar cambios, no otorga permisos. La confirmación vuelve a consultar Oracle. Si cambió la cotización devuelve HTTP 409 y la revisión actualizada. El vencimiento original se conserva. La API de confirmación devuelve los mismos códigos si la reserva ya fue emitida por este flujo.

## Migración y datos

La migración 03_boleta_linea_reserva.sql agrega LINEA_RESERVA nullable y la FK compuesta hacia RESERVA_DETALLE. Oracle omite del índice único condicional las claves con ambas expresiones nulas. Así se mantiene la unicidad por línea para las ventas nuevas y se conservan las boletas históricas, incluso cuando varias pertenecen a una misma reserva. El índice no único adicional cubre la FK. No se cambian los importes históricos.

## Bloqueos

Confirmar adquiere los bloqueos en este orden: RESERVA, FUNCION, ATRACCION, ZONA, PLAN, COMBO y TIPO_ENTRADA. Las filas de cada conjunto se bloquean por ID ascendente, explícitamente una por una. Las funciones se determinan desde las zonas. Después de bloquear las funciones se vuelve a leer la atracción, porque una edición de Funciones puede cambiarla. El módulo Zonas ya bloquea función, atracción y zona en ese orden. El módulo Reservas y el planificador bloquean la cabecera, sin pedir zonas. El límite de seis usa FUNCION como exclusión mutua y cuenta todos los estados activos del perfil incluso cuando las compras usan zonas distintas.

Los catálogos se revalidan bajo esos bloqueos. Una edición de tipos de entrada toma primero y únicamente su fila de tipo y comprueba el historial antes de cambiar el requisito de adulto o la clasificación ADULTO. Sigue permitiendo modificar el descuento. La eliminación de un plan referenciado aborta bajo el bloqueo de RESERVA del módulo Planes antes de intentar eliminar la fila del plan. Funciones toma su bloqueo de tabla de zonas después de bloquear función y atracción. Los límites de espera de Oracle son reintentables. Una confirmación revisa el vencimiento antes de emitir y antes de actualizar la cabecera, con reversión de todo el lote si se vence.

## Alcance

Se conserva el alcance de Reservas. Administrador y operador tienen acceso operativo global. Cliente, solo a su perfil. Encargado, solo si la selección completa y el plan pertenecen a su prestador. La emisión verifica aforo por zona, seis activas por perfil y función, y acompañamiento por reserva y función. Cada unidad de combo incluye una selección por atracción. Su ventana cuenta días calendario de Bogotá desde la confirmación, con final exclusivo.

Los diez reportes conservan su SQL. Los importes de ingresos representan valores registrados de ventas vigentes. El flujo no registra cobros, conciliaciones, abonos ni devoluciones. El comprobante se construye a partir de los precios registrados en BOLETA. El reporte de combos conserva sus métricas de reservas y boletas, y la agrupación por unidad queda disponible mediante el vínculo a detalle.

La cancelación de funciones queda para otra subetapa. Antes de habilitarla se debe acordar si una unidad de combo parcialmente afectada se puede conservar o requiere resolución operativa. La trazabilidad por grupo permite mostrar los componentes afectados. Para una reserva mixta se propone conservar las boletas de otras funciones y no permitir que una cancelación de prestador anule entradas ajenas. Las unidades históricas sin vínculo requieren una política explícita porque no se puede reconstruir su agrupación con certeza.


## Verificación aislada

En un esquema con el modelo de Reservas y la migración 03 aplicada, apunta el datasource al esquema aislado. Los adaptadores anteriores que califican SQL con BOLETERIA_FINAL se redirigen solo en una copia de prueba. Para ejecutar la suite con Oracle habilita BOLETERIA_RESERVAS_QA y BOLETERIA_VENTAS_QA, deshabilita el planificador periódico con BOLETERIA_RESERVAS_EXPIRACION_ACTIVA=false y ejecuta mvn verify. Las pruebas de Ventas validan el nombre del esquema aislado antes de crear fixtures. En el frontend ejecuta tsc --noEmit y next build --webpack.

Las pruebas del navegador deben mostrar la cotización inicial, el conflicto de recotización sin emisión, la aceptación posterior y el detalle confirmado con código e importe. Una consulta Oracle por ID permite contrastar BOLETA.LINEA_RESERVA y PRECIO_PAGADO. Las comprobaciones de concurrencia deben mostrar una sola compra exitosa para el último cupo y para dos compras que juntas superen seis entradas de una función.
