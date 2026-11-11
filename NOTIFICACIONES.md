# Notificaciones operativas

La bandeja permite listar, consultar y marcar como leídos los avisos propios. El servidor resuelve el usuario desde la sesión y Oracle filtra por ID_USUARIO en todas las operaciones. Marcar como leído puede repetirse. Los mensajes históricos se conservan.

Reservas, Ventas y Cancelaciones publican avisos dentro de la misma transacción JDBC que confirma sus cambios. Los perfiles sin cuenta completan la operación sin insertar un aviso. Un fallo de Oracle al guardar un aviso obligatorio revierte la operación completa.

La clave única combina la operación, la reserva y el destinatario. La anulación individual incluye la boleta, la anulación de combo incluye el combo y el grupo, y la cancelación de función incluye la función. Dos anulaciones legítimas en una reserva conservan claves distintas. Una cancelación de función agrega las boletas recién anuladas y publica un aviso por reserva. Los mensajes muestran la cantidad y hasta tres códigos por conjunto, con la vigencia observada al consultar para el aviso. El detalle de la reserva permite consultar su estado actual.

La escritura del aviso usa la misma conexión transaccional y consulta el destinatario sin solicitar bloqueos de cabecera adicionales. La operación de función conserva el protocolo que empieza en FUNCION. El índice único protege también ante publicación simultánea. La migración deja la clave vacía para los avisos históricos.

La bandeja cubre confirmación, cancelación de reserva, expiración, anulación individual o de unidad y cancelación de función. Los avisos informan estados y entradas afectadas. Los registros de pago y devolución corresponden a etapas separadas.
