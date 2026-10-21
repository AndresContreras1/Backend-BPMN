-- Unicidad que Hibernate no sabe generar: un nombre solo se puede repetir con elementos dados de baja
-- (eliminación lógica). Los servicios ya lo validan; el índice lo garantiza aunque lleguen dos peticiones a la vez.

-- HU-04: el nombre del proceso es único dentro de la empresa
CREATE UNIQUE INDEX uk_proceso_nombre_empresa_activo
    ON proceso (empresa_id, lower(nombre))
    WHERE activo = TRUE;

-- HU-17: el nombre del rol de proceso es único dentro de la empresa
CREATE UNIQUE INDEX uk_rol_proceso_nombre_empresa_activo
    ON rol_proceso (empresa_id, lower(nombre))
    WHERE activo = TRUE;

-- HU-08: el nombre de la actividad es único dentro del proceso
CREATE UNIQUE INDEX uk_actividad_nombre_proceso_activo
    ON nodo_flujo (proceso_id, lower(nombre))
    WHERE activo = TRUE AND tipo = 'ACTIVIDAD';

-- HU-21: dos pools del mismo diagrama no se llaman igual
CREATE UNIQUE INDEX uk_pool_nombre_proceso_activo
    ON pool (proceso_id, lower(nombre))
    WHERE activo = TRUE;

-- HU-23: un proceso se comparte una sola vez con cada empresa
CREATE UNIQUE INDEX uk_compartido_proceso_invitada_activo
    ON proceso_compartido (proceso_id, empresa_invitada_id)
    WHERE activo = TRUE;
