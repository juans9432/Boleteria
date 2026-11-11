# Anulaciones y cancelaciones

El módulo expone operaciones separadas para anular una boleta, anular una unidad de combo, cancelar una reserva confirmada y cancelar una función programada. Cada caso de uso comparte una transacción JDBC y revalida alcance y estado bajo bloqueo. El orden es reserva, funciones, atracciones, plan cuando corresponde y boletas. Los IDs se bloquean en orden ascendente dentro de cada conjunto. La cancelación de función empieza por su función y nunca pide un bloqueo de reserva. Uso y emisión comparten los primeros bloqueos de función y atracción.

La anulación voluntaria requiere una boleta vendida. Una unidad de combo se identifica mediante la línea de reserva y su grupo. Todas sus entradas vendidas se anulan juntas si ninguna está usada. Los combos históricos sin agrupación verificable requieren la operación de reserva completa. El acompañamiento se evalúa sobre todas las entradas activas que quedarán en cada función afectada, incluyendo las usadas.

Administrador y operador pueden anular y cancelar reservas confirmadas. Solo administrador y encargado pueden cancelar funciones. El encargado se limita al prestador de cada entrada, a todas las líneas de una unidad de combo y a la selección completa y plan de una reserva. El cliente conserva la consulta de sus boletas.

Una función cancelada anula sus propias entradas vendidas y puede dejar una unidad de combo parcialmente anulada. La reserva conserva CONFIRMADA y la pantalla avisa cuando queda sin boletas vigentes. El tratamiento posterior de esas reservas queda pendiente. Una cancelación total explícita cambia la cabecera a CANCELADA si ninguna de sus boletas está usada.

Los diez reportes SQL conservan sus consultas. La ocupación excluye boletas anuladas. El comprobante y los informes de ingresos y combos reflejan las ventas vigentes y los precios originalmente registrados. El reporte de reservas por estado refleja una cancelación total. La auditoría de acompañamiento sigue evaluando boletas activas por reserva y función. Los importes de ventas vigentes no representan dinero recaudado ni una devolución. La interfaz comunica únicamente el cambio operativo.
