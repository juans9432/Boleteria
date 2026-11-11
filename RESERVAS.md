# Reservas pendientes · Instalación y operación

La migración `database/migraciones/02_reserva_detalle.sql` crea una tabla y tres índices. No modifica filas de RESERVA ni BOLETA. El Reporte 3 diferencia COMPROBANTE_COMPRA de RESUMEN_PENDIENTE. Los otros nueve reportes conservan su SQL. Para una instalación limpia por SQL*Plus ejecuta `SET SQLBLANKLINES ON` antes de cargar los scripts. La instalación limpia ya incluye la tabla y sus índices. Para actualizar un esquema de 16 tablas, la migración se ejecuta como propietario una sola vez. Oracle confirma el DDL automáticamente.

## Datos y operaciones

- La clave primaria es `(ID_RESERVA, LINEA)`. Cada fila representa una entrada. Las filas de una unidad de combo comparten `GRUPO_COMBO` dentro de la reserva. Otra unidad del mismo combo usa otro grupo. El caso de uso verifica que cada grupo tenga todas las atracciones requeridas una sola vez, que su reparto sume 100 % y que el combo esté vigente para las funciones elegidas.
- `PRECIO_ORIENTATIVO` conserva la estimación al guardar o editar. Spring calcula el precio por línea desde zona, reparto del combo y descuento del tipo de entrada. Redondea a dos decimales. La estimación se recalculará antes de confirmar en Ventas y no se escribe en `BOLETA.PRECIO_PAGADO`.
- El listado y el detalle leen Oracle. Para el historial se muestran las boletas existentes con sus códigos, estados y precios pagados. Una pendiente antigua con boletas queda de solo lectura y fuera del proceso automático. Antes del despliegue se debe verificar si existen casos de ese tipo y decidir su tratamiento individual.
- Los cuatro estados existentes se conservan. `COMPLETADA`, prevista en el núcleo académico, queda por definir con el uso de boletas y las reservas con varias funciones.
- Administrador y operador tienen alcance global operativo para pendientes. El cliente usa su propio perfil vinculado. El encargado solo accede si todas las líneas y el plan opcional corresponden a su prestador. El alcance se deriva de las relaciones vigentes del catálogo. Para garantizar un alcance histórico inmutable ante una futura reasignación de atracciones, esa operación deberá preservar o capturar el prestador histórico.

## Transacciones

Crear y editar guardan cabecera y selección en una transacción. Editar, cancelar y expirar serializan por fila de RESERVA. El catálogo se consulta sin bloqueos explícitos de función o zona, puesto que una pendiente no retiene cupos. Las FK protegen las referencias. Eliminar una zona, un tipo o un combo referenciado por el detalle queda restringido también para pendientes canceladas o expiradas, porque se conserva su selección.

El planificador Spring busca desde el arranque pendientes vencidas con `FOR UPDATE SKIP LOCKED` y procesa hasta 100 por transacción. La fecha de creación se guarda e interpreta con hora de Bogotá para el TIMESTAMP existente. La API aplica el límite inclusivo de 15 minutos aunque la persistencia del estado todavía espere el siguiente lote. Tras un reinicio el planificador recupera el atraso de Oracle.

Para Ventas se propone bloquear reserva, luego funciones por ID ascendente y luego zonas por ID ascendente. El módulo Zonas ya toma función antes de zona. Ventas tendrá que coordinar además la exclusión mutua para el límite de compras del mismo cliente y función, incluso en zonas distintas. Esa operación no forma parte de esta entrega.

## Verificación de instalación o actualización

1. Ejecutar los scripts de carga limpia, la migración y los diez reportes en el esquema aislado.
2. Confirmar que coinciden los conteos históricos y que ninguna operación de pendientes emite boletas.
3. Revisar las pruebas de creación, edición, cancelación, vencimiento, referencias inválidas y alcance de los roles.
4. Verificar desde Next.js la tabla, el aviso de disponibilidad, la cotización, el vencimiento y el estado tras cancelar.
5. Antes de aplicar al esquema definitivo, obtener aprobación y ejecutar la auditoría de pendientes con boletas.

```sql
SELECT R.ID_RESERVA
FROM RESERVA R
WHERE R.ESTADO = 'PENDIENTE'
  AND EXISTS (SELECT 1 FROM BOLETA B WHERE B.ID_RESERVA = R.ID_RESERVA);
```


