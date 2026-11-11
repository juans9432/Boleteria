
-- =========================================================
-- BOLETERÍA Y EVENTOS TURÍSTICOS DEL QUINDÍO
-- BASES DE DATOS I - UNIVERSIDAD DEL QUINDÍO
--
-- SCRIPT 02: REGLAS DE NEGOCIO
-- BLOQUE 1: VALIDACIONES COMPLEMENTARIAS
-- ESQUEMA: BOLETERIA_APP
-- =========================================================

-- RN: Los horarios deben tener formato HH24:MI.
ALTER TABLE FUNCION
ADD CONSTRAINT CK_FUNCION_FORMATO_APERTURA
CHECK (
    REGEXP_LIKE(
        HORA_APERTURA,
        '^([01][0-9]|2[0-3]):[0-5][0-9]$'
    )
);

ALTER TABLE FUNCION
ADD CONSTRAINT CK_FUNCION_FORMATO_CIERRE
CHECK (
    REGEXP_LIKE(
        HORA_CIERRE,
        '^([01][0-9]|2[0-3]):[0-5][0-9]$'
    )
);

-- RN: Una boleta USADA o ANULADA
-- no puede cambiar nuevamente de estado.
CREATE OR REPLACE TRIGGER TRG_BOLETA_ESTADO
BEFORE UPDATE OF ESTADO ON BOLETA
FOR EACH ROW
BEGIN
    IF :OLD.ESTADO IN ('USADA', 'ANULADA')
       AND :NEW.ESTADO <> :OLD.ESTADO THEN

        RAISE_APPLICATION_ERROR(
            -20001,
            'Una boleta USADA o ANULADA no puede cambiar de estado.'
        );

    END IF;
END;
/


-- =========================================================
-- BLOQUE 2: CONTROL DE CRUCES DE HORARIOS
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_FUNCION_SIN_CRUCES
FOR INSERT OR UPDATE OF
    FECHA, HORA_APERTURA, HORA_CIERRE,
    ESTADO, ID_ATRACCION
ON FUNCION
COMPOUND TRIGGER

    TYPE T_IDS IS TABLE OF FUNCION.ID_FUNCION%TYPE
        INDEX BY PLS_INTEGER;

    V_IDS T_IDS;
    V_CANTIDAD PLS_INTEGER := 0;
    V_CRUCES NUMBER;
    V_ATRACCION NUMBER;

    BEFORE EACH ROW IS
    BEGIN
        -- Bloquea la atracción durante la operación.
        -- Ayuda a serializar cambios concurrentes
        -- sobre funciones de la misma atracción.
        SELECT ID_ATRACCION
        INTO V_ATRACCION
        FROM ATRACCION
        WHERE ID_ATRACCION = :NEW.ID_ATRACCION
        FOR UPDATE;

        -- Guardar el identificador de la función.
        V_CANTIDAD := V_CANTIDAD + 1;
        V_IDS(V_CANTIDAD) := :NEW.ID_FUNCION;
    END BEFORE EACH ROW;

    AFTER STATEMENT IS
    BEGIN
        FOR I IN 1 .. V_CANTIDAD LOOP

            SELECT COUNT(*)
            INTO V_CRUCES
            FROM FUNCION F1
            JOIN FUNCION F2
                ON F1.ID_ATRACCION = F2.ID_ATRACCION
            WHERE F1.ID_FUNCION = V_IDS(I)
              AND F1.ID_FUNCION <> F2.ID_FUNCION
              AND F1.ESTADO <> 'CANCELADA'
              AND F2.ESTADO <> 'CANCELADA'
              AND TRUNC(F1.FECHA) = TRUNC(F2.FECHA)
              AND F1.HORA_APERTURA < F2.HORA_CIERRE
              AND F1.HORA_CIERRE > F2.HORA_APERTURA;

            IF V_CRUCES > 0 THEN
                RAISE_APPLICATION_ERROR(
                    -20002,
                    'La funcion presenta un cruce de horarios con otra funcion de la misma atraccion.'
                );
            END IF;

        END LOOP;
    END AFTER STATEMENT;

END TRG_FUNCION_SIN_CRUCES;
/


-- =========================================================
-- BLOQUE 3: CONTROL DE AFORO POR ZONA
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_BOLETA_CONTROL_AFORO
FOR INSERT OR UPDATE OF ID_ZONA, ESTADO
ON BOLETA
COMPOUND TRIGGER

    TYPE T_ZONAS IS TABLE OF ZONA.ID_ZONA%TYPE
        INDEX BY PLS_INTEGER;

    V_ZONAS T_ZONAS;
    V_TOTAL_ZONAS PLS_INTEGER := 0;

    V_AFORO NUMBER;
    V_OCUPADAS NUMBER;

    BEFORE EACH ROW IS
    BEGIN
        -- Bloquear la zona durante la operación
        -- para coordinar ventas concurrentes.

        SELECT AFORO
        INTO V_AFORO
        FROM ZONA
        WHERE ID_ZONA = :NEW.ID_ZONA
        FOR UPDATE;

        -- Registrar la zona que debe verificarse.

        V_TOTAL_ZONAS := V_TOTAL_ZONAS + 1;
        V_ZONAS(V_TOTAL_ZONAS) := :NEW.ID_ZONA;

    END BEFORE EACH ROW;

    AFTER STATEMENT IS
    BEGIN

        FOR I IN 1 .. V_TOTAL_ZONAS LOOP

            -- Obtener el aforo máximo.
            SELECT AFORO
            INTO V_AFORO
            FROM ZONA
            WHERE ID_ZONA = V_ZONAS(I);

            -- Contar las boletas que consumen aforo.
            SELECT COUNT(*)
            INTO V_OCUPADAS
            FROM BOLETA
            WHERE ID_ZONA = V_ZONAS(I)
              AND ESTADO IN ('VENDIDA', 'USADA');

            -- Rechazar el exceso de aforo.
            IF V_OCUPADAS > V_AFORO THEN

                RAISE_APPLICATION_ERROR(
                    -20003,
                    'No se puede completar la operacion: '
                    || 'se supera el aforo permitido de la zona.'
                );

            END IF;

        END LOOP;

    END AFTER STATEMENT;

END TRG_BOLETA_CONTROL_AFORO;
/


-- =========================================================
-- BLOQUE 3.1: PROTEGER MODIFICACIONES DEL AFORO
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_ZONA_VALIDAR_AFORO
BEFORE UPDATE OF AFORO ON ZONA
FOR EACH ROW

DECLARE
    V_OCUPADAS NUMBER;

BEGIN

    -- Contar las boletas que consumen aforo.
    SELECT COUNT(*)
    INTO V_OCUPADAS
    FROM BOLETA
    WHERE ID_ZONA = :OLD.ID_ZONA
      AND ESTADO IN ('VENDIDA', 'USADA');

    -- Impedir que el nuevo aforo sea inferior
    -- a la cantidad de boletas ocupadas.
    IF :NEW.AFORO < V_OCUPADAS THEN

        RAISE_APPLICATION_ERROR(
            -20004,
            'No se puede reducir el aforo. '
            || 'La zona tiene '
            || V_OCUPADAS
            || ' boletas ocupadas.'
        );

    END IF;

END;
/


-- =========================================================
-- BLOQUE 4.1: MAXIMO DE 6 BOLETAS POR CLIENTE Y FUNCION
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_BOLETA_LIMITE_CLIENTE
FOR INSERT OR UPDATE OF ID_RESERVA, ID_ZONA, ESTADO
ON BOLETA
COMPOUND TRIGGER

    TYPE T_PAR IS RECORD (
        ID_PERFIL RESERVA.ID_PERFIL%TYPE,
        ID_FUNCION ZONA.ID_FUNCION%TYPE
    );

    TYPE T_PARES IS TABLE OF T_PAR
        INDEX BY PLS_INTEGER;

    V_PARES T_PARES;
    V_TOTAL PLS_INTEGER := 0;

    V_PERFIL RESERVA.ID_PERFIL%TYPE;
    V_FUNCION ZONA.ID_FUNCION%TYPE;
    V_CANTIDAD NUMBER;
    V_BLOQUEO NUMBER;

    BEFORE EACH ROW IS
    BEGIN
        -- Obtener el cliente de la reserva.
        SELECT ID_PERFIL
        INTO V_PERFIL
        FROM RESERVA
        WHERE ID_RESERVA = :NEW.ID_RESERVA;

        -- Obtener la funcion asociada a la zona.
        SELECT ID_FUNCION
        INTO V_FUNCION
        FROM ZONA
        WHERE ID_ZONA = :NEW.ID_ZONA;

        -- Bloquear el perfil para coordinar
        -- operaciones concurrentes del mismo cliente.
        SELECT ID_PERFIL
        INTO V_BLOQUEO
        FROM PERFIL_CLIENTE
        WHERE ID_PERFIL = V_PERFIL
        FOR UPDATE;

        -- Guardar el par cliente-funcion
        -- para verificarlo al finalizar la sentencia.
        V_TOTAL := V_TOTAL + 1;
        V_PARES(V_TOTAL).ID_PERFIL := V_PERFIL;
        V_PARES(V_TOTAL).ID_FUNCION := V_FUNCION;

    END BEFORE EACH ROW;

    AFTER STATEMENT IS
    BEGIN
        FOR I IN 1 .. V_TOTAL LOOP

            SELECT COUNT(*)
            INTO V_CANTIDAD
            FROM BOLETA B
            JOIN RESERVA R
                ON B.ID_RESERVA = R.ID_RESERVA
            JOIN ZONA Z
                ON B.ID_ZONA = Z.ID_ZONA
            WHERE R.ID_PERFIL = V_PARES(I).ID_PERFIL
              AND Z.ID_FUNCION = V_PARES(I).ID_FUNCION
              AND B.ESTADO IN ('VENDIDA', 'USADA');

            IF V_CANTIDAD > 6 THEN
                RAISE_APPLICATION_ERROR(
                    -20005,
                    'El cliente supera el limite de '
                    || '6 boletas para la misma funcion.'
                );
            END IF;

        END LOOP;
    END AFTER STATEMENT;

END TRG_BOLETA_LIMITE_CLIENTE;
/


-- =========================================================
-- BLOQUE 4.2: ACOMPANAMIENTO DE MENORES
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_BOLETA_ACOMPANANTE
FOR INSERT OR UPDATE OR DELETE ON BOLETA
COMPOUND TRIGGER

    TYPE T_PAR IS RECORD (
        ID_RESERVA RESERVA.ID_RESERVA%TYPE,
        ID_FUNCION FUNCION.ID_FUNCION%TYPE
    );

    TYPE T_PARES IS TABLE OF T_PAR
        INDEX BY PLS_INTEGER;

    V_PARES T_PARES;
    V_TOTAL PLS_INTEGER := 0;
    V_BLOQUEO NUMBER;
    V_FUNCION NUMBER;

    PROCEDURE REGISTRAR_PAR(
        P_RESERVA NUMBER,
        P_ZONA NUMBER
    ) IS
    BEGIN
        SELECT ID_FUNCION
        INTO V_FUNCION
        FROM ZONA
        WHERE ID_ZONA = P_ZONA;

        -- Serializar operaciones sobre la misma reserva.
        SELECT ID_RESERVA
        INTO V_BLOQUEO
        FROM RESERVA
        WHERE ID_RESERVA = P_RESERVA
        FOR UPDATE;

        V_TOTAL := V_TOTAL + 1;
        V_PARES(V_TOTAL).ID_RESERVA := P_RESERVA;
        V_PARES(V_TOTAL).ID_FUNCION := V_FUNCION;
    END REGISTRAR_PAR;

    BEFORE EACH ROW IS
    BEGIN
        IF INSERTING OR UPDATING THEN
            REGISTRAR_PAR(:NEW.ID_RESERVA, :NEW.ID_ZONA);
        END IF;

        IF UPDATING OR DELETING THEN
            REGISTRAR_PAR(:OLD.ID_RESERVA, :OLD.ID_ZONA);
        END IF;
    END BEFORE EACH ROW;

    AFTER STATEMENT IS
        V_MENORES NUMBER;
        V_ADULTOS NUMBER;
    BEGIN
        FOR I IN 1 .. V_TOTAL LOOP

            -- Contar boletas que necesitan acompanante.
            SELECT COUNT(*)
            INTO V_MENORES
            FROM BOLETA B
            JOIN ZONA Z
                ON Z.ID_ZONA = B.ID_ZONA
            JOIN TIPO_ENTRADA T
                ON T.ID_TIPO_ENTRADA = B.ID_TIPO_ENTRADA
            WHERE B.ID_RESERVA = V_PARES(I).ID_RESERVA
              AND Z.ID_FUNCION = V_PARES(I).ID_FUNCION
              AND B.ESTADO IN ('VENDIDA', 'USADA')
              AND T.REQUIERE_ADULTO = 'S';

            -- Contar boletas de adulto activas.
            SELECT COUNT(*)
            INTO V_ADULTOS
            FROM BOLETA B
            JOIN ZONA Z
                ON Z.ID_ZONA = B.ID_ZONA
            JOIN TIPO_ENTRADA T
                ON T.ID_TIPO_ENTRADA = B.ID_TIPO_ENTRADA
            WHERE B.ID_RESERVA = V_PARES(I).ID_RESERVA
              AND Z.ID_FUNCION = V_PARES(I).ID_FUNCION
              AND B.ESTADO IN ('VENDIDA', 'USADA')
              AND UPPER(TRIM(T.NOMBRE)) = 'ADULTO';

            IF V_MENORES > 0 AND V_ADULTOS = 0 THEN
                RAISE_APPLICATION_ERROR(
                    -20006,
                    'La reserva tiene entradas de menores '
                    || 'sin una boleta de adulto activa '
                    || 'para la misma funcion.'
                );
            END IF;

        END LOOP;
    END AFTER STATEMENT;

END TRG_BOLETA_ACOMPANANTE;
/


-- =========================================================
-- BLOQUE 5.1: VALIDAR PORCENTAJES AL ACTIVAR COMBOS
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_COMBO_VALIDAR_ACTIVACION
BEFORE INSERT OR UPDATE OF ESTADO ON COMBO
FOR EACH ROW

DECLARE
    V_TOTAL NUMBER(7,2);
    V_CANTIDAD NUMBER;

BEGIN

    -- Solo verificar cuando el combo se activa.
    IF :NEW.ESTADO = 'ACTIVO' THEN

        -- Los combos deben configurarse primero
        -- como INACTIVO y despues activarse.
        IF INSERTING THEN

            RAISE_APPLICATION_ERROR(
                -20007,
                'El combo debe crearse INACTIVO '
                || 'y configurarse antes de activarse.'
            );

        END IF;

        -- Obtener la cantidad de atracciones
        -- y la suma de los porcentajes.
        SELECT
            COUNT(*),
            NVL(SUM(PORCENTAJE_PRECIO), 0)
        INTO
            V_CANTIDAD,
            V_TOTAL
        FROM COMBO_ATRACCION
        WHERE ID_COMBO = :NEW.ID_COMBO;

        -- Debe existir al menos una atraccion.
        IF V_CANTIDAD = 0 THEN

            RAISE_APPLICATION_ERROR(
                -20008,
                'No se puede activar un combo sin atracciones.'
            );

        END IF;

        -- La distribucion debe sumar exactamente 100.
        IF V_TOTAL <> 100 THEN

            RAISE_APPLICATION_ERROR(
                -20009,
                'No se puede activar el combo. '
                || 'Los porcentajes suman '
                || TO_CHAR(V_TOTAL)
                || ' y deben sumar 100.'
            );

        END IF;

    END IF;

END;
/


-- =========================================================
-- BLOQUE 5.2: PROTEGER COMPOSICION DE COMBOS ACTIVOS
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_COMBO_PROTEGER_ATRACCIONES
BEFORE INSERT OR UPDATE OR DELETE
ON COMBO_ATRACCION
FOR EACH ROW

DECLARE
    V_ESTADO COMBO.ESTADO%TYPE;

    PROCEDURE VALIDAR_COMBO(P_ID_COMBO NUMBER) IS
    BEGIN
        -- Bloquear el combo para coordinar cambios
        -- concurrentes con su activacion.
        SELECT ESTADO
        INTO V_ESTADO
        FROM COMBO
        WHERE ID_COMBO = P_ID_COMBO
        FOR UPDATE;

        -- No permitir cambios en combos activos.
        IF V_ESTADO = 'ACTIVO' THEN
            RAISE_APPLICATION_ERROR(
                -20010,
                'No se pueden modificar las atracciones '
                || 'de un combo ACTIVO. '
                || 'Primero debe desactivarlo.'
            );
        END IF;
    END VALIDAR_COMBO;

BEGIN

    IF INSERTING THEN

        -- Validar el combo al que se agrega
        -- una nueva atraccion.
        VALIDAR_COMBO(:NEW.ID_COMBO);

    ELSIF DELETING THEN

        -- Validar el combo del que se elimina
        -- una atraccion.
        VALIDAR_COMBO(:OLD.ID_COMBO);

    ELSIF UPDATING THEN

        -- Validar el combo original.
        VALIDAR_COMBO(:OLD.ID_COMBO);

        -- Si se cambia el identificador del combo,
        -- validar tambien el combo de destino.
        IF :NEW.ID_COMBO <> :OLD.ID_COMBO THEN
            VALIDAR_COMBO(:NEW.ID_COMBO);
        END IF;

    END IF;

END;
/


-- =========================================================
-- BLOQUE 5.3: VALIDACION DE BOLETAS CON COMBOS
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_BOLETA_VALIDAR_COMBO
FOR INSERT OR UPDATE OF ID_COMBO, ID_ZONA, ID_RESERVA
ON BOLETA
COMPOUND TRIGGER

    TYPE T_PAR IS RECORD (
        ID_RESERVA RESERVA.ID_RESERVA%TYPE,
        ID_COMBO COMBO.ID_COMBO%TYPE
    );

    TYPE T_PARES IS TABLE OF T_PAR
        INDEX BY PLS_INTEGER;

    V_PARES T_PARES;
    V_TOTAL PLS_INTEGER := 0;
    V_BLOQUEO NUMBER;

    BEFORE EACH ROW IS
    BEGIN
        IF :NEW.ID_COMBO IS NOT NULL
           AND :NEW.ESTADO IN ('VENDIDA', 'USADA') THEN

            -- Serializar ventas del combo
            -- dentro de una misma reserva.
            SELECT ID_RESERVA
            INTO V_BLOQUEO
            FROM RESERVA
            WHERE ID_RESERVA = :NEW.ID_RESERVA
            FOR UPDATE;

            V_TOTAL := V_TOTAL + 1;
            V_PARES(V_TOTAL).ID_RESERVA := :NEW.ID_RESERVA;
            V_PARES(V_TOTAL).ID_COMBO := :NEW.ID_COMBO;
        END IF;
    END BEFORE EACH ROW;

    AFTER STATEMENT IS
        V_INVALIDAS NUMBER;
        V_DUPLICADAS NUMBER;
    BEGIN
        FOR I IN 1 .. V_TOTAL LOOP

            -- Validar estado, atraccion y vigencia.
            SELECT COUNT(*)
            INTO V_INVALIDAS
            FROM BOLETA B
            JOIN RESERVA R
                ON R.ID_RESERVA = B.ID_RESERVA
            JOIN ZONA Z
                ON Z.ID_ZONA = B.ID_ZONA
            JOIN FUNCION F
                ON F.ID_FUNCION = Z.ID_FUNCION
            JOIN COMBO C
                ON C.ID_COMBO = B.ID_COMBO
            WHERE B.ID_RESERVA = V_PARES(I).ID_RESERVA
              AND B.ID_COMBO = V_PARES(I).ID_COMBO
              AND B.ESTADO IN ('VENDIDA', 'USADA')
              AND (
                  C.ESTADO <> 'ACTIVO'

                  OR NOT EXISTS (
                      SELECT 1
                      FROM COMBO_ATRACCION CA
                      WHERE CA.ID_COMBO = C.ID_COMBO
                        AND CA.ID_ATRACCION = F.ID_ATRACCION
                  )

                  OR TRUNC(F.FECHA) <
                     TRUNC(CAST(R.FECHA_HORA AS DATE))

                  OR TRUNC(F.FECHA) >=
                     TRUNC(CAST(R.FECHA_HORA AS DATE))
                     + C.DIAS_VIGENCIA
              );

            IF V_INVALIDAS > 0 THEN
                RAISE_APPLICATION_ERROR(
                    -20011,
                    'Boleta invalida: combo inactivo, '
                    || 'atraccion no incluida o '
                    || 'fecha fuera de vigencia.'
                );
            END IF;

            -- Detectar atracciones repetidas
            -- en el mismo combo y reserva.
            SELECT COUNT(*)
            INTO V_DUPLICADAS
            FROM (
                SELECT F.ID_ATRACCION
                FROM BOLETA B
                JOIN ZONA Z
                    ON Z.ID_ZONA = B.ID_ZONA
                JOIN FUNCION F
                    ON F.ID_FUNCION = Z.ID_FUNCION
                WHERE B.ID_RESERVA =
                      V_PARES(I).ID_RESERVA
                  AND B.ID_COMBO =
                      V_PARES(I).ID_COMBO
                  AND B.ESTADO IN ('VENDIDA', 'USADA')
                GROUP BY F.ID_ATRACCION
                HAVING COUNT(*) > 1
            );

            IF V_DUPLICADAS > 0 THEN
                RAISE_APPLICATION_ERROR(
                    -20012,
                    'No se permite repetir una atraccion '
                    || 'dentro del mismo combo y reserva.'
                );
            END IF;

        END LOOP;
    END AFTER STATEMENT;

END TRG_BOLETA_VALIDAR_COMBO;
/


-- =========================================================
-- BLOQUE 6: VALIDACION DEL USO DE BOLETAS
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_BOLETA_VALIDAR_USO
BEFORE UPDATE OF ESTADO ON BOLETA
FOR EACH ROW

DECLARE
    V_FECHA FUNCION.FECHA%TYPE;
    V_APERTURA FUNCION.HORA_APERTURA%TYPE;
    V_CIERRE FUNCION.HORA_CIERRE%TYPE;
    V_ESTADO FUNCION.ESTADO%TYPE;

    V_HORA_ACTUAL VARCHAR2(5);

BEGIN

    -- Validar cuando una boleta pasa a USADA.
    IF :NEW.ESTADO = 'USADA'
       AND :OLD.ESTADO <> 'USADA' THEN

        -- Solo puede utilizarse una boleta vendida.
        IF :OLD.ESTADO <> 'VENDIDA' THEN
            RAISE_APPLICATION_ERROR(
                -20013,
                'Solo una boleta VENDIDA puede '
                || 'cambiar al estado USADA.'
            );
        END IF;

        -- Obtener los datos de la funcion.
        SELECT
            F.FECHA,
            F.HORA_APERTURA,
            F.HORA_CIERRE,
            F.ESTADO
        INTO
            V_FECHA,
            V_APERTURA,
            V_CIERRE,
            V_ESTADO
        FROM ZONA Z
        JOIN FUNCION F
            ON F.ID_FUNCION = Z.ID_FUNCION
        WHERE Z.ID_ZONA = :NEW.ID_ZONA;

        -- Impedir el uso en funciones canceladas.
        IF V_ESTADO = 'CANCELADA' THEN
            RAISE_APPLICATION_ERROR(
                -20014,
                'No se puede utilizar una boleta '
                || 'de una funcion cancelada.'
            );
        END IF;

        -- Validar la fecha de la funcion.
        IF TRUNC(SYSDATE) <> TRUNC(V_FECHA) THEN
            RAISE_APPLICATION_ERROR(
                -20015,
                'La boleta solo puede utilizarse '
                || 'en la fecha de su funcion.'
            );
        END IF;

        -- Obtener la hora actual del servidor Oracle.
        V_HORA_ACTUAL := TO_CHAR(
            SYSDATE,
            'HH24:MI'
        );

        -- Validar el horario permitido.
        IF V_HORA_ACTUAL < V_APERTURA
           OR V_HORA_ACTUAL > V_CIERRE THEN

            RAISE_APPLICATION_ERROR(
                -20016,
                'La boleta solo puede utilizarse '
                || 'durante el horario de la funcion.'
            );

        END IF;

    END IF;

END;
/


-- =========================================================
-- BLOQUE 7: PROTECCION DEL HISTORIAL DE BOLETAS
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_BOLETA_PROTEGER_HISTORIAL
BEFORE UPDATE OR DELETE
ON BOLETA
FOR EACH ROW

BEGIN

    -- No permitir la eliminacion fisica de boletas.
    -- Se conserva el historial de ventas.
    IF DELETING THEN

        RAISE_APPLICATION_ERROR(
            -20017,
            'No se permite eliminar boletas. '
            || 'Debe utilizarse el proceso de anulacion.'
        );

    END IF;

    -- Una boleta usada o anulada es inmutable.
    -- Se impide modificar cualquiera de sus campos.
    IF UPDATING THEN

        IF :OLD.ESTADO IN ('USADA', 'ANULADA') THEN

            RAISE_APPLICATION_ERROR(
                -20018,
                'No se permite modificar una boleta '
                || 'que ya esta USADA o ANULADA.'
            );

        END IF;

    END IF;

END;
/


-- =========================================================
-- BLOQUE 8.1: CANCELACION DE FUNCIONES
-- =========================================================

CREATE OR REPLACE PROCEDURE SP_CANCELAR_FUNCION (
    P_ID_FUNCION IN NUMBER
)
AS
    V_ESTADO FUNCION.ESTADO%TYPE;
    V_USADAS NUMBER;

BEGIN
    -- Bloquear la funcion durante la cancelacion.
    SELECT ESTADO
    INTO V_ESTADO
    FROM FUNCION
    WHERE ID_FUNCION = P_ID_FUNCION
    FOR UPDATE;

    -- Si ya esta cancelada, no repetir la operacion.
    IF V_ESTADO = 'CANCELADA' THEN
        RETURN;
    END IF;

    -- Comprobar si existen boletas utilizadas.
    SELECT COUNT(*)
    INTO V_USADAS
    FROM BOLETA B
    JOIN ZONA Z
        ON Z.ID_ZONA = B.ID_ZONA
    WHERE Z.ID_FUNCION = P_ID_FUNCION
      AND B.ESTADO = 'USADA';

    IF V_USADAS > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20019,
            'No se puede cancelar una funcion '
            || 'que ya tiene boletas utilizadas.'
        );
    END IF;

    -- Anular las boletas vendidas.
    UPDATE BOLETA
    SET ESTADO = 'ANULADA'
    WHERE ESTADO = 'VENDIDA'
      AND ID_ZONA IN (
          SELECT ID_ZONA
          FROM ZONA
          WHERE ID_FUNCION = P_ID_FUNCION
      );

    -- Cancelar la funcion.
    UPDATE FUNCION
    SET ESTADO = 'CANCELADA'
    WHERE ID_FUNCION = P_ID_FUNCION;

EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(
            -20020,
            'La funcion indicada no existe.'
        );
END SP_CANCELAR_FUNCION;
/


-- =========================================================
-- BLOQUE 8.2: PROTEGER CANCELACION DIRECTA
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_FUNCION_PROTEGER_CANCELACION
BEFORE UPDATE OF ESTADO
ON FUNCION
FOR EACH ROW

DECLARE
    V_VENDIDAS NUMBER;

BEGIN
    IF :NEW.ESTADO = 'CANCELADA'
       AND :OLD.ESTADO <> 'CANCELADA' THEN

        SELECT COUNT(*)
        INTO V_VENDIDAS
        FROM BOLETA B
        JOIN ZONA Z
            ON Z.ID_ZONA = B.ID_ZONA
        WHERE Z.ID_FUNCION = :OLD.ID_FUNCION
          AND B.ESTADO = 'VENDIDA';

        IF V_VENDIDAS > 0 THEN
            RAISE_APPLICATION_ERROR(
                -20021,
                'No se puede cancelar directamente '
                || 'una funcion con boletas vendidas. '
                || 'Utilice SP_CANCELAR_FUNCION.'
            );
        END IF;
    END IF;

END;
/


-- =========================================================
-- BLOQUE 8.3: IMPEDIR BOLETAS EN FUNCIONES CANCELADAS
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_BOLETA_FUNCION_ACTIVA
BEFORE INSERT OR UPDATE OF ID_ZONA, ESTADO
ON BOLETA
FOR EACH ROW

DECLARE
    V_ESTADO_FUNCION FUNCION.ESTADO%TYPE;

BEGIN

    -- Validar boletas que permaneceran activas.
    -- Las anulaciones no deben bloquearse.

    IF :NEW.ESTADO IN ('VENDIDA', 'USADA') THEN

        -- Consultar la funcion asociada a la zona.
        -- El bloqueo coordina la venta con una
        -- cancelacion simultanea de esa funcion.

        SELECT F.ESTADO
        INTO V_ESTADO_FUNCION
        FROM FUNCION F
        JOIN ZONA Z
            ON Z.ID_FUNCION = F.ID_FUNCION
        WHERE Z.ID_ZONA = :NEW.ID_ZONA
        FOR UPDATE OF F.ESTADO;

        -- Impedir operaciones con funciones canceladas.
        IF V_ESTADO_FUNCION = 'CANCELADA' THEN

            RAISE_APPLICATION_ERROR(
                -20022,
                'No se pueden emitir ni mantener '
                || 'boletas activas en una funcion cancelada.'
            );

        END IF;

        -- Impedir nuevas ventas para funciones realizadas.
        IF V_ESTADO_FUNCION = 'REALIZADA'
           AND :NEW.ESTADO = 'VENDIDA' THEN

            RAISE_APPLICATION_ERROR(
                -20023,
                'No se pueden vender boletas '
                || 'para una funcion ya realizada.'
            );

        END IF;

    END IF;

END;
/


-- =========================================================
-- BLOQUE 9.1: EXPIRACION DE RESERVAS PENDIENTES
-- =========================================================

CREATE OR REPLACE PROCEDURE SP_EXPIRAR_RESERVAS
AS
    V_LIMITE TIMESTAMP;
BEGIN

    -- Las fechas de las reservas y la hora actual
    -- se interpretan en la zona horaria de la sesion.
    V_LIMITE := CAST(CURRENT_TIMESTAMP AS TIMESTAMP)
                - INTERVAL '15' MINUTE;

    -- Recorrer las reservas pendientes vencidas.
    -- SKIP LOCKED evita esperar por reservas
    -- que otra transaccion esta modificando.
    FOR R IN (
        SELECT ID_RESERVA
        FROM RESERVA
        WHERE ESTADO = 'PENDIENTE'
          AND FECHA_HORA <= V_LIMITE
        FOR UPDATE SKIP LOCKED
    ) LOOP

        -- Si existen boletas vendidas, anularlas
        -- para que no consuman aforo.
        UPDATE BOLETA
        SET ESTADO = 'ANULADA'
        WHERE ID_RESERVA = R.ID_RESERVA
          AND ESTADO = 'VENDIDA';

        -- Marcar la reserva como expirada.
        UPDATE RESERVA
        SET ESTADO = 'EXPIRADA'
        WHERE ID_RESERVA = R.ID_RESERVA
          AND ESTADO = 'PENDIENTE';

    END LOOP;

END SP_EXPIRAR_RESERVAS;
/


-- =========================================================
-- BLOQUE 9.2: IMPEDIR CONFIRMAR RESERVAS VENCIDAS
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_RESERVA_VALIDAR_CONFIRMACION
BEFORE UPDATE OF ESTADO
ON RESERVA
FOR EACH ROW
BEGIN

    IF :NEW.ESTADO = 'CONFIRMADA'
       AND :OLD.ESTADO <> 'CONFIRMADA' THEN

        -- Solo una reserva pendiente puede confirmarse.
        IF :OLD.ESTADO <> 'PENDIENTE' THEN
            RAISE_APPLICATION_ERROR(
                -20024,
                'Solo se puede confirmar una reserva PENDIENTE.'
            );
        END IF;

        -- Verificar los 15 minutos de vigencia.
        IF :OLD.FECHA_HORA <=
           CAST(CURRENT_TIMESTAMP AS TIMESTAMP)
           - INTERVAL '15' MINUTE THEN

            RAISE_APPLICATION_ERROR(
                -20025,
                'La reserva ha superado los 15 minutos '
                || 'y no puede confirmarse.'
            );

        END IF;

    END IF;

END;
/


-- =========================================================
-- BLOQUE 10.1: CALCULO DEL PRECIO DE UNA BOLETA
-- =========================================================

CREATE OR REPLACE FUNCTION FN_CALCULAR_PRECIO_BOLETA (
    P_ID_ZONA         IN NUMBER,
    P_ID_TIPO_ENTRADA IN NUMBER,
    P_ID_COMBO        IN NUMBER DEFAULT NULL
)
RETURN NUMBER
AS
    V_PRECIO_BASE NUMBER(12,2);
    V_DESCUENTO NUMBER(5,2);
    V_PRECIO_FINAL NUMBER(12,2);

BEGIN

    -- Obtener el descuento del tipo de entrada.
    SELECT PORCENTAJE_DESCUENTO
    INTO V_DESCUENTO
    FROM TIPO_ENTRADA
    WHERE ID_TIPO_ENTRADA = P_ID_TIPO_ENTRADA;

    IF P_ID_COMBO IS NULL THEN

        -- Entrada individual: precio de la zona.
        SELECT PRECIO
        INTO V_PRECIO_BASE
        FROM ZONA
        WHERE ID_ZONA = P_ID_ZONA;

    ELSE

        -- Entrada de combo: asignar el porcentaje
        -- del precio correspondiente a la atraccion.
        SELECT
            C.PRECIO * CA.PORCENTAJE_PRECIO / 100
        INTO V_PRECIO_BASE
        FROM COMBO C
        JOIN COMBO_ATRACCION CA
            ON CA.ID_COMBO = C.ID_COMBO
        JOIN FUNCION F
            ON F.ID_ATRACCION = CA.ID_ATRACCION
        JOIN ZONA Z
            ON Z.ID_FUNCION = F.ID_FUNCION
        WHERE C.ID_COMBO = P_ID_COMBO
          AND Z.ID_ZONA = P_ID_ZONA
          AND C.ESTADO = 'ACTIVO';

    END IF;

    -- Aplicar el descuento correspondiente.
    V_PRECIO_FINAL := ROUND(
        V_PRECIO_BASE * (1 - V_DESCUENTO / 100),
        2
    );

    RETURN V_PRECIO_FINAL;

EXCEPTION

    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(
            -20026,
            'No se pudo calcular el precio: '
            || 'zona, tipo de entrada o combo invalido.'
        );

END FN_CALCULAR_PRECIO_BOLETA;
/


-- =========================================================
-- BLOQUE 10.2: VALIDAR EL PRECIO PAGADO DE UNA BOLETA
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_BOLETA_VALIDAR_PRECIO
BEFORE INSERT ON BOLETA
FOR EACH ROW

DECLARE
    V_PRECIO_CORRECTO NUMBER(12,2);

BEGIN

    -- Calcular el precio oficial de la boleta.
    V_PRECIO_CORRECTO := FN_CALCULAR_PRECIO_BOLETA(
        :NEW.ID_ZONA,
        :NEW.ID_TIPO_ENTRADA,
        :NEW.ID_COMBO
    );

    -- Comprobar que se haya enviado un precio.
    IF :NEW.PRECIO_PAGADO IS NULL THEN

        RAISE_APPLICATION_ERROR(
            -20027,
            'Debe indicar el precio pagado de la boleta.'
        );

    END IF;

    -- Validar el precio registrado.
    IF :NEW.PRECIO_PAGADO <> V_PRECIO_CORRECTO THEN

        RAISE_APPLICATION_ERROR(
            -20028,
            'El precio registrado no coincide con '
            || 'el precio calculado. Valor correcto: '
            || TO_CHAR(V_PRECIO_CORRECTO)
        );

    END IF;

END;
/


----------------------BLOQUE 1----------------------------------

-- Comprobar restricciones de horarios.
SELECT
    CONSTRAINT_NAME,
    STATUS
FROM USER_CONSTRAINTS
WHERE TABLE_NAME = 'FUNCION'
  AND CONSTRAINT_NAME IN (
      'CK_FUNCION_FORMATO_APERTURA',
      'CK_FUNCION_FORMATO_CIERRE'
  );

-- Comprobar el trigger de boletas.
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_BOLETA_ESTADO';


SELECT LINE, POSITION, TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_ESTADO'
ORDER BY SEQUENCE;

----------------------BLOQUE 2-----------------------------------

-- Estado del trigger
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_FUNCION_SIN_CRUCES';

-- Errores de compilación
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_FUNCION_SIN_CRUCES'
ORDER BY SEQUENCE;

---------------------BLOQUE 3----------------------------------

SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_BOLETA_CONTROL_AFORO';


SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_CONTROL_AFORO'
ORDER BY SEQUENCE;

----------------------BLOQUE 3.1-------------------------------

SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME IN (
    'TRG_BOLETA_CONTROL_AFORO',
    'TRG_ZONA_VALIDAR_AFORO'
)
ORDER BY TRIGGER_NAME;


SELECT
    NAME,
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_ZONA_VALIDAR_AFORO'
ORDER BY SEQUENCE;

------------------------BLOQUE 4.1-----------------------------


-- Verificar el estado del trigger.
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_BOLETA_LIMITE_CLIENTE';

-- Comprobar errores de compilacion.
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_LIMITE_CLIENTE'
ORDER BY SEQUENCE;

------------------------BLOQUE 4.2-----------------------------


-- Verificar estado.
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_BOLETA_ACOMPANANTE';

-- Verificar errores de compilacion.
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_ACOMPANANTE'
ORDER BY SEQUENCE;

------------------------BLOQUE 5.1-----------------------------

-- Verificar el estado del trigger
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_COMBO_VALIDAR_ACTIVACION';

-- Verificar errores de compilacion
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_COMBO_VALIDAR_ACTIVACION'
ORDER BY SEQUENCE;

------------------------BLOQUE 5.2-----------------------------

-- Comprobar estado del trigger.
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_COMBO_PROTEGER_ATRACCIONES';

-- Comprobar errores de compilacion.
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_COMBO_PROTEGER_ATRACCIONES'
ORDER BY SEQUENCE;

------------------------BLOQUE 5.3-----------------------------

-- Estado del trigger
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_BOLETA_VALIDAR_COMBO';

-- Errores de compilacion
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_VALIDAR_COMBO'
ORDER BY SEQUENCE;

------------------------BLOQUE 6--------------------------------

-- Comprobar estado del trigger.
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_BOLETA_VALIDAR_USO';

-- Comprobar errores de compilacion.
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_VALIDAR_USO'
ORDER BY SEQUENCE;

------------------------BLOQUE 7--------------------------------

-- Verificar que el trigger este habilitado.
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_BOLETA_PROTEGER_HISTORIAL';

-- Comprobar errores de compilacion.
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_PROTEGER_HISTORIAL'
ORDER BY SEQUENCE;

------------------------BLOQUE 8.1 y 8.2-------------------------------

-- Verificar el procedimiento.
SELECT
    OBJECT_NAME,
    OBJECT_TYPE,
    STATUS
FROM USER_OBJECTS
WHERE OBJECT_NAME IN (
    'SP_CANCELAR_FUNCION',
    'TRG_FUNCION_PROTEGER_CANCELACION'
)
ORDER BY OBJECT_NAME;

-- Verificar errores de compilacion.
SELECT
    NAME,
    TYPE,
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME IN (
    'SP_CANCELAR_FUNCION',
    'TRG_FUNCION_PROTEGER_CANCELACION'
)
ORDER BY NAME, SEQUENCE;

------------------------BLOQUE 8.3-------------------------------

-- Verificar estado del trigger
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_BOLETA_FUNCION_ACTIVA';

-- Verificar errores
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_FUNCION_ACTIVA'
ORDER BY SEQUENCE;

------------------------BLOQUE 9.1 y 9.2-------------------------------

-- Verificar el procedimiento y el trigger.
SELECT
    OBJECT_NAME,
    OBJECT_TYPE,
    STATUS
FROM USER_OBJECTS
WHERE OBJECT_NAME IN (
    'SP_EXPIRAR_RESERVAS',
    'TRG_RESERVA_VALIDAR_CONFIRMACION'
)
ORDER BY OBJECT_NAME;

-- Verificar errores de compilacion.
SELECT
    NAME,
    TYPE,
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME IN (
    'SP_EXPIRAR_RESERVAS',
    'TRG_RESERVA_VALIDAR_CONFIRMACION'
)
ORDER BY NAME, SEQUENCE;

------------------------BLOQUE 10.1-------------------------------

-- Verificar que la funcion este compilada.
SELECT
    OBJECT_NAME,
    OBJECT_TYPE,
    STATUS
FROM USER_OBJECTS
WHERE OBJECT_NAME = 'FN_CALCULAR_PRECIO_BOLETA';

-- Comprobar errores de compilacion.
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'FN_CALCULAR_PRECIO_BOLETA'
ORDER BY SEQUENCE;

------------------------BLOQUE 10.2-------------------------------

SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_BOLETA_VALIDAR_PRECIO';

SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_VALIDAR_PRECIO'
ORDER BY SEQUENCE;

--============================================================
CREATE OR REPLACE TRIGGER TRG_BOLETA_PRECIO_HISTORICO
BEFORE UPDATE OF PRECIO_PAGADO
ON BOLETA
FOR EACH ROW
BEGIN

    IF :NEW.PRECIO_PAGADO <> :OLD.PRECIO_PAGADO THEN
        RAISE_APPLICATION_ERROR(
            -20029,
            'No se permite modificar el precio '
            || 'historico de una boleta emitida.'
        );
    END IF;

END;
/


SELECT
    OBJECT_NAME,
    STATUS
FROM USER_OBJECTS
WHERE OBJECT_NAME = 'TRG_BOLETA_PRECIO_HISTORICO';

SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_PRECIO_HISTORICO'
ORDER BY SEQUENCE;


-- =========================================================
-- AUDITORIA: PROTEGER DATOS DE BOLETAS VENDIDAS
-- =========================================================

CREATE OR REPLACE TRIGGER TRG_BOLETA_DATOS_INMUTABLES
BEFORE UPDATE ON BOLETA
FOR EACH ROW
BEGIN

    -- Una boleta vendida conserva los datos
    -- originales de la compra.

    IF :OLD.ESTADO = 'VENDIDA' THEN

        IF :NEW.CODIGO <> :OLD.CODIGO
           OR :NEW.ID_RESERVA <> :OLD.ID_RESERVA
           OR :NEW.ID_ZONA <> :OLD.ID_ZONA
           OR :NEW.ID_TIPO_ENTRADA <> :OLD.ID_TIPO_ENTRADA
           OR NVL(:NEW.ID_COMBO, -1)
              <> NVL(:OLD.ID_COMBO, -1) THEN

            RAISE_APPLICATION_ERROR(
                -20030,
                'No se pueden modificar los datos '
                || 'originales de una boleta vendida.'
            );

        END IF;

    END IF;

END;
/


-- Verificar estado del nuevo trigger
SELECT
    TRIGGER_NAME,
    STATUS
FROM USER_TRIGGERS
WHERE TRIGGER_NAME = 'TRG_BOLETA_DATOS_INMUTABLES';

-- Comprobar errores de compilacion
SELECT
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
WHERE NAME = 'TRG_BOLETA_DATOS_INMUTABLES'
ORDER BY SEQUENCE;




