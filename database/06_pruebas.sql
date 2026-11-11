
-- ==============================================
-- AUDITORIA 1: INVENTARIO DE OBJETOS
-- ==============================================

SELECT
    OBJECT_TYPE AS TIPO_OBJETO,
    STATUS AS ESTADO,
    COUNT(*) AS CANTIDAD
FROM USER_OBJECTS
WHERE OBJECT_TYPE IN (
    'TABLE',
    'TRIGGER',
    'PROCEDURE',
    'FUNCTION',
    'VIEW'
)
GROUP BY OBJECT_TYPE, STATUS
ORDER BY OBJECT_TYPE, STATUS;


-- ==============================================
-- AUDITORIA 2: TRIGGERS POR TABLA
-- ==============================================

SELECT
    TABLE_NAME,
    TRIGGER_NAME,
    TRIGGER_TYPE,
    TRIGGERING_EVENT,
    STATUS
FROM USER_TRIGGERS
ORDER BY TABLE_NAME, TRIGGER_NAME;


-- ==============================================
-- AUDITORIA 3: ERRORES DE COMPILACION
-- ==============================================

SELECT
    NAME,
    TYPE,
    LINE,
    POSITION,
    TEXT
FROM USER_ERRORS
ORDER BY NAME, SEQUENCE;


-- ==============================================
-- AUDITORIA 4: RESTRICCIONES DESHABILITADAS
-- ==============================================

SELECT
    TABLE_NAME,
    CONSTRAINT_NAME,
    CONSTRAINT_TYPE,
    STATUS,
    VALIDATED
FROM USER_CONSTRAINTS
WHERE STATUS <> 'ENABLED'
   OR VALIDATED <> 'VALIDATED'
ORDER BY TABLE_NAME, CONSTRAINT_NAME;


-- =========================================================
-- PRUEBA FUNCIONAL 01
-- VALIDACION DE RESTRICCIONES EN TIPO_ENTRADA
-- =========================================================

DECLARE
    V_ID NUMBER;
    V_RECHAZOS NUMBER := 0;

BEGIN
    SAVEPOINT PRUEBA_01;

    -- CASO 1: Descuento valido.
    INSERT INTO TIPO_ENTRADA (
        NOMBRE,
        PORCENTAJE_DESCUENTO,
        REQUIERE_ADULTO
    )
    VALUES (
        'PRUEBA_DESCUENTO_VALIDO',
        25,
        'N'
    )
    RETURNING ID_TIPO_ENTRADA INTO V_ID;

    DBMS_OUTPUT.PUT_LINE(
        'P01 - OK: descuento valido aceptado.'
    );

    -- CASO 2: Descuento superior al 100 %.
    BEGIN
        INSERT INTO TIPO_ENTRADA (
            NOMBRE,
            PORCENTAJE_DESCUENTO,
            REQUIERE_ADULTO
        )
        VALUES (
            'PRUEBA_DESCUENTO_INVALIDO',
            120,
            'N'
        );

        RAISE_APPLICATION_ERROR(
            -20101,
            'P02 - FALLO: Oracle acepto el 120 %.'
        );

    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -2290 THEN
                V_RECHAZOS := V_RECHAZOS + 1;
                DBMS_OUTPUT.PUT_LINE(
                    'P02 - OK: descuento invalido rechazado.'
                );
            ELSE
                RAISE;
            END IF;
    END;

    -- CASO 3: Valor invalido para REQUIERE_ADULTO.
    BEGIN
        INSERT INTO TIPO_ENTRADA (
            NOMBRE,
            PORCENTAJE_DESCUENTO,
            REQUIERE_ADULTO
        )
        VALUES (
            'PRUEBA_ADULTO_INVALIDO',
            10,
            'X'
        );

        RAISE_APPLICATION_ERROR(
            -20102,
            'P03 - FALLO: Oracle acepto el valor X.'
        );

    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -2290 THEN
                V_RECHAZOS := V_RECHAZOS + 1;
                DBMS_OUTPUT.PUT_LINE(
                    'P03 - OK: valor invalido rechazado.'
                );
            ELSE
                RAISE;
            END IF;
    END;

    IF V_RECHAZOS <> 2 THEN
        RAISE_APPLICATION_ERROR(
            -20103,
            'No se completaron los dos rechazos esperados.'
        );
    END IF;

    -- Deshacer los datos de prueba.
    ROLLBACK TO PRUEBA_01;

    DBMS_OUTPUT.PUT_LINE(
        'PRUEBA 01 FINALIZADA CORRECTAMENTE.'
    );

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK TO PRUEBA_01;
        RAISE;
END;
/


-- =========================================================
-- PRUEBA FUNCIONAL 02
-- VALIDACION DE CRUCES DE HORARIOS
-- =========================================================

DECLARE
    V_MUNICIPIO NUMBER;
    V_TIPO_PRESTADOR NUMBER;
    V_PRESTADOR NUMBER;
    V_ATRACCION NUMBER;
    V_FUNCION NUMBER;
    V_NIT VARCHAR2(20);
    V_RECHAZO BOOLEAN := FALSE;

BEGIN
    SAVEPOINT PRUEBA_02;

    -- Crear datos temporales relacionados.
    INSERT INTO MUNICIPIO (NOMBRE, DEPARTAMENTO)
    VALUES ('Municipio Prueba', 'Quindio')
    RETURNING ID_MUNICIPIO INTO V_MUNICIPIO;

    INSERT INTO TIPO_PRESTADOR (NOMBRE)
    VALUES ('Tipo Prueba Horarios')
    RETURNING ID_TIPO_PRESTADOR INTO V_TIPO_PRESTADOR;

    V_NIT := SUBSTR(RAWTOHEX(SYS_GUID()), 1, 20);

    INSERT INTO PRESTADOR (
        NIT, NOMBRE, CORREO,
        ID_MUNICIPIO, ID_TIPO_PRESTADOR
    )
    VALUES (
        V_NIT, 'Prestador Prueba',
        'prueba@example.com',
        V_MUNICIPIO, V_TIPO_PRESTADOR
    )
    RETURNING ID_PRESTADOR INTO V_PRESTADOR;

    INSERT INTO ATRACCION (
        NOMBRE, DESCRIPCION, ID_PRESTADOR
    )
    VALUES (
        'Atraccion Prueba',
        'Prueba de horarios',
        V_PRESTADOR
    )
    RETURNING ID_ATRACCION INTO V_ATRACCION;

    -- P04: Primera funcion valida.
    INSERT INTO FUNCION (
        FECHA, HORA_APERTURA, HORA_CIERRE,
        ESTADO, ID_ATRACCION
    )
    VALUES (
        DATE '2027-02-10',
        '09:00', '12:00',
        'PROGRAMADA', V_ATRACCION
    )
    RETURNING ID_FUNCION INTO V_FUNCION;

    DBMS_OUTPUT.PUT_LINE(
        'P04 - OK: primera funcion aceptada.'
    );

    -- P05: Segunda funcion con cruce.
    BEGIN
        INSERT INTO FUNCION (
            FECHA, HORA_APERTURA, HORA_CIERRE,
            ESTADO, ID_ATRACCION
        )
        VALUES (
            DATE '2027-02-10',
            '11:00', '14:00',
            'PROGRAMADA', V_ATRACCION
        );

        RAISE_APPLICATION_ERROR(
            -20105,
            'P05 - FALLO: se acepto un cruce.'
        );

    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -20002 THEN
                V_RECHAZO := TRUE;
                DBMS_OUTPUT.PUT_LINE(
                    'P05 - OK: cruce de horarios rechazado.'
                );
            ELSE
                RAISE;
            END IF;
    END;

    IF NOT V_RECHAZO THEN
        RAISE_APPLICATION_ERROR(
            -20106,
            'No se verifico el rechazo esperado.'
        );
    END IF;

    -- P06: Funcion consecutiva, sin cruce.
    INSERT INTO FUNCION (
        FECHA, HORA_APERTURA, HORA_CIERRE,
        ESTADO, ID_ATRACCION
    )
    VALUES (
        DATE '2027-02-10',
        '12:00', '16:00',
        'PROGRAMADA', V_ATRACCION
    );

    DBMS_OUTPUT.PUT_LINE(
        'P06 - OK: funcion consecutiva aceptada.'
    );

    -- Deshacer todos los datos temporales.
    ROLLBACK TO PRUEBA_02;

    DBMS_OUTPUT.PUT_LINE(
        'PRUEBA 02 FINALIZADA CORRECTAMENTE.'
    );

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK TO PRUEBA_02;
        RAISE;
END;
/


-- =========================================================
-- PRUEBA FUNCIONAL 03
-- CONTROL DE AFORO Y ANULACION DE BOLETAS
-- =========================================================

DECLARE
    V_MUNICIPIO NUMBER;
    V_TIPO_PRESTADOR NUMBER;
    V_PRESTADOR NUMBER;
    V_ATRACCION NUMBER;
    V_FUNCION NUMBER;
    V_ZONA NUMBER;

    V_ROL NUMBER;
    V_USUARIO NUMBER;
    V_PERFIL NUMBER;
    V_RESERVA NUMBER;
    V_TIPO_ENTRADA NUMBER;
    V_BOLETA NUMBER;

    V_ID_UNICO VARCHAR2(32);
    V_RECHAZO BOOLEAN := FALSE;
    V_OCUPADAS NUMBER;

BEGIN
    SAVEPOINT PRUEBA_03;

    -- Identificador unico para evitar duplicados.
    V_ID_UNICO := RAWTOHEX(SYS_GUID());

    -- 1. Preparar catalogos y prestador.
    INSERT INTO MUNICIPIO (
        NOMBRE, DEPARTAMENTO
    ) VALUES (
        'Municipio Prueba Aforo', 'Quindio'
    )
    RETURNING ID_MUNICIPIO INTO V_MUNICIPIO;

    INSERT INTO TIPO_PRESTADOR (NOMBRE)
    VALUES ('TP_' || SUBSTR(V_ID_UNICO, 1, 20))
    RETURNING ID_TIPO_PRESTADOR INTO V_TIPO_PRESTADOR;

    INSERT INTO PRESTADOR (
        NIT, NOMBRE, CORREO,
        ID_MUNICIPIO, ID_TIPO_PRESTADOR
    ) VALUES (
        SUBSTR(V_ID_UNICO, 1, 20),
        'Prestador Prueba Aforo',
        'aforo@example.com',
        V_MUNICIPIO, V_TIPO_PRESTADOR
    )
    RETURNING ID_PRESTADOR INTO V_PRESTADOR;

    -- 2. Crear atraccion y funcion.
    INSERT INTO ATRACCION (
        NOMBRE, DESCRIPCION, ID_PRESTADOR
    ) VALUES (
        'Atraccion Prueba Aforo',
        'Prueba automatizada',
        V_PRESTADOR
    )
    RETURNING ID_ATRACCION INTO V_ATRACCION;

    INSERT INTO FUNCION (
        FECHA, HORA_APERTURA, HORA_CIERRE,
        ESTADO, ID_ATRACCION
    ) VALUES (
        DATE '2027-03-10',
        '09:00', '17:00',
        'PROGRAMADA', V_ATRACCION
    )
    RETURNING ID_FUNCION INTO V_FUNCION;

    -- Zona de aforo 1 y precio 80000.
    INSERT INTO ZONA (
        NOMBRE, AFORO, PRECIO, ID_FUNCION
    ) VALUES (
        'GENERAL', 1, 80000, V_FUNCION
    )
    RETURNING ID_ZONA INTO V_ZONA;

    -- 3. Crear usuario y perfil cliente.
    INSERT INTO ROL (NOMBRE)
    VALUES ('ROL_' || SUBSTR(V_ID_UNICO, 1, 20))
    RETURNING ID_ROL INTO V_ROL;

    INSERT INTO USUARIO_SISTEMA (
        CORREO, CLAVE_HASH, ESTADO, ID_ROL
    ) VALUES (
        'test_' || V_ID_UNICO || '@example.com',
        'HASH_SOLO_PRUEBA',
        'ACTIVO',
        V_ROL
    )
    RETURNING ID_USUARIO INTO V_USUARIO;

    INSERT INTO PERFIL_CLIENTE (
        DOCUMENTO, NOMBRE, TELEFONO, ID_USUARIO
    ) VALUES (
        SUBSTR(V_ID_UNICO, 1, 20),
        'Cliente Prueba Aforo',
        '3000000000',
        V_USUARIO
    )
    RETURNING ID_PERFIL INTO V_PERFIL;

    INSERT INTO RESERVA (
        ESTADO, ID_PERFIL
    ) VALUES (
        'PENDIENTE', V_PERFIL
    )
    RETURNING ID_RESERVA INTO V_RESERVA;

    -- Tipo de entrada adulto, sin descuento.
    INSERT INTO TIPO_ENTRADA (
        NOMBRE, PORCENTAJE_DESCUENTO,
        REQUIERE_ADULTO
    ) VALUES (
        'ADULTO', 0, 'N'
    )
    RETURNING ID_TIPO_ENTRADA INTO V_TIPO_ENTRADA;

    -- =========================================
    -- P07: PRIMERA BOLETA (DEBE ACEPTARSE)
    -- =========================================

    INSERT INTO BOLETA (
        CODIGO, ESTADO, PRECIO_PAGADO,
        ID_RESERVA, ID_ZONA,
        ID_TIPO_ENTRADA, ID_COMBO
    ) VALUES (
        RAWTOHEX(SYS_GUID()),
        'VENDIDA',
        80000,
        V_RESERVA,
        V_ZONA,
        V_TIPO_ENTRADA,
        NULL
    )
    RETURNING ID_BOLETA INTO V_BOLETA;

    DBMS_OUTPUT.PUT_LINE(
        'P07 - OK: primera boleta aceptada.'
    );

    -- =========================================
    -- P08: SEGUNDA BOLETA (DEBE RECHAZARSE)
    -- =========================================

    BEGIN
        INSERT INTO BOLETA (
            CODIGO, ESTADO, PRECIO_PAGADO,
            ID_RESERVA, ID_ZONA,
            ID_TIPO_ENTRADA, ID_COMBO
        ) VALUES (
            RAWTOHEX(SYS_GUID()),
            'VENDIDA',
            80000,
            V_RESERVA,
            V_ZONA,
            V_TIPO_ENTRADA,
            NULL
        );

        RAISE_APPLICATION_ERROR(
            -20108,
            'P08 - FALLO: se acepto el sobrecupo.'
        );

    EXCEPTION
        WHEN OTHERS THEN
            IF INSTR(
                DBMS_UTILITY.FORMAT_ERROR_STACK,
                'ORA-20003'
            ) > 0 THEN
                V_RECHAZO := TRUE;
                DBMS_OUTPUT.PUT_LINE(
                    'P08 - OK: sobrecupo rechazado.'
                );
            ELSE
                RAISE;
            END IF;
    END;

    IF NOT V_RECHAZO THEN
        RAISE_APPLICATION_ERROR(
            -20109,
            'No se verifico el rechazo de aforo.'
        );
    END IF;

    -- =========================================
    -- P09: ANULAR Y LIBERAR EL CUPO
    -- =========================================

    UPDATE BOLETA
    SET ESTADO = 'ANULADA'
    WHERE ID_BOLETA = V_BOLETA;

    -- Comprobar que se libero el cupo.
    SELECT COUNT(*)
    INTO V_OCUPADAS
    FROM BOLETA
    WHERE ID_ZONA = V_ZONA
      AND ESTADO IN ('VENDIDA', 'USADA');

    IF V_OCUPADAS <> 0 THEN
        RAISE_APPLICATION_ERROR(
            -20110,
            'P09 - FALLO: no se libero el cupo.'
        );
    END IF;

    -- Volver a vender una boleta.
    INSERT INTO BOLETA (
        CODIGO, ESTADO, PRECIO_PAGADO,
        ID_RESERVA, ID_ZONA,
        ID_TIPO_ENTRADA, ID_COMBO
    ) VALUES (
        RAWTOHEX(SYS_GUID()),
        'VENDIDA',
        80000,
        V_RESERVA,
        V_ZONA,
        V_TIPO_ENTRADA,
        NULL
    );

    DBMS_OUTPUT.PUT_LINE(
        'P09 - OK: cupo liberado y reutilizado.'
    );

    -- Deshacer todos los registros temporales.
    ROLLBACK TO PRUEBA_03;

    DBMS_OUTPUT.PUT_LINE(
        'PRUEBA 03 FINALIZADA CORRECTAMENTE.'
    );

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK TO PRUEBA_03;
        DBMS_OUTPUT.PUT_LINE(
            'ERROR PRUEBA 03: ' || SQLERRM
        );
        RAISE;
END;
/

