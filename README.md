# Backend BPMN

Backend REST del visor y editor de procesos BPMN multiempresa. Cada empresa se registra, administra sus usuarios y modela sus procesos (pools, lanes, actividades, gateways, arcos, mensajes y correlación). El sistema modela y valida los diagramas; no los ejecuta.

## Stack tecnológico

- Java 17
- Spring Boot 3.5 (Web, Data JPA, Validation)
- Spring Security con JWT stateless (jjwt 0.12) y BCrypt
- springdoc-openapi 2.8 (Swagger UI)
- H2 (desarrollo y pruebas) / PostgreSQL 16 (producción)
- JUnit 5, Mockito, AssertJ, JaCoCo
- Docker (build multi-stage) y Docker Compose
- GitHub Actions (CI) y SonarCloud

## Prerrequisitos

- JDK 17
- Docker (opcional, para PostgreSQL y la imagen)
- Maven no es necesario: el proyecto trae el wrapper `./mvnw`

## Ejecución local

```bash
./mvnw spring-boot:run
```

La API inicia en `http://localhost:8090` con el perfil `default` (H2 en archivo, se recrea en cada arranque). La consola de H2 queda en `http://localhost:8090/h2` (JDBC URL `jdbc:h2:file:./data/bpmndb`, usuario `sa`, sin contraseña).

## Datos de demostración

Con los perfiles `default` y `demo` se cargan dos empresas para probar el aislamiento entre organizaciones:

| Empresa | NIT | Usuario | Contraseña | Rol |
|---|---|---|---|---|
| Facimus Demo S.A.S. | 900123456-1 | `admin@demo.com` | `admin123` | ADMINISTRADOR |
| | | `editor@demo.com` | `editor123` | EDITOR |
| | | `lector@demo.com` | `lector123` | LECTOR |
| Comercial Andina Ltda. | 800987654-3 | `admin@andina.com` | `admin123` | ADMINISTRADOR |
| | | `editor@andina.com` | `editor123` | EDITOR |
| | | `lector@andina.com` | `lector123` | LECTOR |

## Seguridad

- `POST /api/v1/auth/login` devuelve un JWT; el resto de la API exige `Authorization: Bearer <token>`.
- Públicos: `POST /api/v1/empresas` (registro), `POST /api/v1/auth/login`, Swagger y la consola H2 (solo perfil `default`).
- El token lleva el usuario, la empresa y el rol. En cada petición se consulta el usuario vigente: si fue desactivado o cambió de rol, aplica de inmediato.
- Todas las consultas se filtran por la empresa del usuario autenticado; un recurso de otra empresa responde 404.
- Roles de acceso: `ADMINISTRADOR`, `EDITOR`, `LECTOR` (los permisos se declaran con `@PreAuthorize` en los servicios).
- Errores 401/403 con el mismo formato ProblemDetail del resto de la API.

## Endpoints

Todos bajo `/api/v1`. Las respuestas de error usan ProblemDetail; los listados devuelven `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`).

| HU | Método y ruta | Descripción | Rol |
|---|---|---|---|
| HU-01 | `POST /empresas` | Registrar empresa y su administrador inicial | público |
| HU-03 | `GET /empresas/actual` | Empresa del usuario autenticado | todos |
| HU-03 | `POST /auth/login` | Iniciar sesión (devuelve el JWT) | público |
| HU-03 | `GET /auth/yo` · `POST /auth/logout` | Usuario autenticado · cerrar sesión | todos |
| HU-02 | `GET /usuarios?nombre=` | Usuarios de mi empresa (paginado) | todos |
| HU-02 | `POST /usuarios` | Invitar usuario con rol y contraseña inicial | ADMINISTRADOR |
| HU-02 | `PUT /usuarios/{id}/rol` | Cambiar rol de acceso | ADMINISTRADOR |
| HU-02 | `PATCH /usuarios/{id}/desactivar` · `/activar` | Desactivar o reactivar sin borrar | ADMINISTRADOR |
| HU-07 | `GET /procesos?nombre=&estado=&categoria=&activo=` | Buscar, filtrar y paginar procesos | todos |
| HU-07 | `GET /procesos/categorias` | Categorías en uso | todos |
| HU-07 | `GET /procesos/{id}` · `GET /procesos/{id}/historial` | Ficha e historial de cambios | todos |
| HU-04 | `POST /procesos` | Crear proceso (BORRADOR, con el pool de la empresa) | ADMINISTRADOR, EDITOR |
| HU-05 | `PUT /procesos/{id}` | Editar nombre, descripción, categoría y estado | ADMINISTRADOR, EDITOR |
| HU-06 | `DELETE /procesos/{id}` | Eliminación lógica | ADMINISTRADOR |
| HU-07 | `GET /procesos/{id}/diagrama` | Diagrama completo del proceso | todos |
| HU-20 | `GET /roles-proceso?nombre=` | Roles de proceso con su uso (paginado) | todos |
| HU-20 | `GET /roles-proceso/{id}/procesos` · `/historial` | Procesos que usan el rol · historial | todos |
| HU-17 | `POST /roles-proceso` | Crear rol de proceso | ADMINISTRADOR |
| HU-18 | `PUT /roles-proceso/{id}` | Editar (renombra las lanes que lo usan) | ADMINISTRADOR |
| HU-19 | `DELETE /roles-proceso/{id}` | Eliminación lógica si no está en uso | ADMINISTRADOR |
| HU-21 | `GET` · `POST /procesos/{id}/pools` | Pools del proceso · agregar participante | todos · según HU-24 |
| HU-21 | `GET` · `PUT` · `DELETE /pools/{id}` | Consultar, editar, eliminar pool | todos · según HU-24 |
| HU-24 | `PUT /pools/{id}/permisos` | Quién modifica pools y lanes | ADMINISTRADOR |
| HU-24 | `GET /pools/{id}/roles-disponibles` | Roles asignables a las lanes | todos |
| HU-22 | `GET` · `POST /pools/{id}/lanes` · `PUT /pools/{id}/lanes/orden` | Lanes del pool, crear, reordenar | según HU-24 |
| HU-22 | `PUT` · `DELETE /lanes/{id}` | Renombrar (otro rol), eliminar | según HU-24 |
| HU-08 | `POST /lanes/{id}/actividades` | Crear actividad (nombre único, tipo, posición) | ADMINISTRADOR, EDITOR |
| HU-09 | `GET` · `PUT /actividades/{id}` | Consultar, editar (nombre, tipo, lane responsable) | todos · ADMINISTRADOR, EDITOR |
| HU-10 | `GET /actividades/{id}/impacto` · `DELETE /actividades/{id}` | Advertencias previas · eliminar con sus arcos | todos · ADMINISTRADOR |
| HU-14 | `POST /lanes/{id}/gateways` | Crear gateway exclusivo, paralelo o inclusivo | ADMINISTRADOR, EDITOR |
| HU-15 | `GET` · `PUT /gateways/{id}` | Consultar con advertencias, editar tipo y condiciones | todos · ADMINISTRADOR, EDITOR |
| HU-16 | `GET /gateways/{id}/impacto` · `DELETE /gateways/{id}` | Advertencias previas · eliminar con sus arcos | todos · ADMINISTRADOR |
| — | `POST /pools/{id}/eventos` · `GET` · `PUT` · `DELETE /eventos/{id}` | Eventos de inicio y de fin | ADMINISTRADOR, EDITOR (eliminar: ADMINISTRADOR) |
| HU-11 | `GET /procesos/{id}/arcos` · `POST /arcos` | Arcos del proceso · crear arco | todos · ADMINISTRADOR, EDITOR |
| HU-12 | `GET` · `PUT /arcos/{id}` | Consultar, editar origen, destino, etiqueta y condición | todos · ADMINISTRADOR, EDITOR |
| HU-13 | `GET /arcos/{id}/impacto` · `DELETE /arcos/{id}` | Advertencias previas · eliminar | todos · ADMINISTRADOR |
| — | `GET /procesos/{id}/validacion` | Advertencias de coherencia del diagrama | todos |
| HU-25 | `POST /procesos/{id}/mensajes/throw` | Message Throw hacia otro pool del diagrama | ADMINISTRADOR, EDITOR |
| HU-26 | `POST /procesos/{id}/mensajes/externos` | Notificación (evento o tarea) a un sistema externo de caja negra | ADMINISTRADOR, EDITOR |
| HU-27 | `POST /procesos/{id}/mensajes/catch` | Message Catch de inicio o intermedio | ADMINISTRADOR, EDITOR |
| HU-28 | `PUT /mensajes/{id}/correlacion` | Clave de correlación y acción sin caso | ADMINISTRADOR, EDITOR |
| HU-25 a 28 | `GET /procesos/{id}/mensajes` · `GET` · `PUT /mensajes/{id}` | Mensajes del proceso · consultar y editar | todos · ADMINISTRADOR, EDITOR |
| HU-25 a 28 | `GET /mensajes/{id}/impacto` · `DELETE /mensajes/{id}` | Advertencias previas · eliminar | todos · ADMINISTRADOR |
| HU-23 | `PUT` · `GET /procesos/{id}/compartir` | Compartir por NIT en solo lectura · estado | ADMINISTRADOR de la empresa dueña |
| HU-23 | `GET /procesos/compartidos` | Procesos que otras empresas compartieron conmigo | todos |

## Coherencia del diagrama

El sistema modela y valida; no ejecuta procesos. `ValidadorDeProcesoService` revisa cada pool que no es caja negra:

| Código | Severidad | Regla |
|---|---|---|
| `DIAGRAMA_VACIO` | ERROR | El diagrama no tiene elementos |
| `SIN_INICIO` · `SIN_FIN` | ERROR | El pool no tiene evento de inicio o de fin |
| `SIN_ENTRADA` · `SIN_SALIDA` | ERROR | Un elemento no tiene camino de entrada o de salida |
| `INALCANZABLE` | ERROR | Un elemento no se alcanza desde ningún evento de inicio |
| `INICIO_CON_ENTRADAS` · `FIN_CON_SALIDAS` | ERROR | Arcos que entran a un inicio o salen de un fin |
| `ACTIVIDAD_SIN_LANE` | ERROR | Toda actividad pertenece a una lane |
| `GATEWAY_SIN_RAMIFICACION` | ERROR | Un gateway de divergencia necesita al menos dos salidas |
| `CONDICION_FALTANTE` | ERROR | Cada salida de un gateway exclusivo o inclusivo lleva condición |
| `CONDICIONES_NO_EXCLUYENTES` | AVISO | Un gateway exclusivo repite una condición |
| `CAJA_NEGRA_CON_ELEMENTOS` | ERROR | Un pool de caja negra no tiene elementos internos |
| `MENSAJE_SIN_DESTINO` · `MENSAJE_MISMO_POOL` | ERROR | El flujo de mensaje apunta a un pool que no existe o no cruza de pool |
| `MENSAJE_SIN_RECEPTOR` | AVISO | El Message Throw no tiene un Message Catch con su nombre en el pool destino |
| `CATCH_SIN_EMISOR` | ERROR | El Message Catch no tiene Message Throw homónimo ni está marcado como de origen externo |
| `CORRELACION_INCOHERENTE` | ERROR | El Throw y el Catch que se corresponden usan claves distintas |
| `CORRELACION_FALTANTE` | AVISO | Un Message Catch intermedio no tiene clave de correlación |
| `CORRELACION_AMBIGUA` | AVISO | Dos mensajes distintos comparten nombre y clave |

En **BORRADOR** el diagrama puede estar incompleto: las advertencias se muestran pero no bloquean. Para pasar a **PUBLICADO** no puede haber errores (409 con la lista en `detalles`), y un proceso publicado rechaza cualquier cambio que lo deje incoherente. Antes de eliminar un elemento, `GET …/{id}/impacto` devuelve los arcos que se irían con él y los problemas nuevos que dejaría.

Reglas de los arcos: no unen un elemento consigo mismo, no se repiten entre el mismo par de nodos, nunca cruzan de un pool a otro (eso es un mensaje), no entran a un evento de inicio ni salen de uno de fin, y la condición solo existe al salir de un gateway exclusivo o inclusivo.

## Colaboración entre empresas

Por defecto un proceso solo lo ve la empresa que lo creó. Su administrador puede compartirlo por NIT con otras empresas (`PUT /procesos/{id}/compartir`): la invitada ve la ficha, el diagrama, los mensajes y las advertencias, pero no puede modificar nada (toda escritura responde 404), ni ve el historial, los usuarios o el catálogo de roles de la empresa dueña. Dejar de compartir o eliminar el proceso retira el acceso.

## Documentación de la API

Swagger UI en `http://localhost:8090/swagger-ui/index.html` (botón *Authorize* con el token) y contrato OpenAPI en `http://localhost:8090/v3/api-docs`.

## Perfiles

| Perfil | Base de datos | Uso |
|---|---|---|
| `default` | H2 en archivo, `create-drop` | Desarrollo local |
| `test` | H2 en memoria, `create-drop` | Pruebas automatizadas |
| `prod` | PostgreSQL, `update` | Docker Compose y despliegue |
| `demo` | (se combina con otro) | Carga los datos de demostración |

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `PORT` | Puerto HTTP de la API | `8090` |
| `JWT_SECRET` | Clave en Base64 (mínimo 32 bytes) para firmar los JWT | se genera al arrancar |
| `JWT_EXPIRATION_SECONDS` | Duración del token en segundos | `1800` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos, separados por coma | `http://localhost:4200` |
| `DB_HOST` | Host de PostgreSQL (perfil `prod`) | `localhost` |
| `DB_PORT` | Puerto de PostgreSQL (perfil `prod`) | `5432` |
| `DB_NAME` | Nombre de la base (perfil `prod`) | `procesos` |
| `DB_USER` | Usuario de la base (perfil `prod`) | — |
| `DB_PASSWORD` | Contraseña de la base (perfil `prod`) | — |

## Pruebas

```bash
./mvnw verify
```

Ejecuta las pruebas unitarias (`*Test`) y de integración (`*IT`) y genera el reporte de cobertura en `target/site/jacoco/index.html`. JaCoCo exige 50 % de cobertura de línea en todo el proyecto y 70 % de línea / 60 % de rama en los paquetes de servicio.

## Docker

Imagen sola:

```bash
docker build -t backend-bpmn .
docker run -p 8090:8090 backend-bpmn
```

Backend con PostgreSQL (copiar `.env.example` como `.env` y cambiar la contraseña):

```bash
docker compose up -d --build
docker compose down
```

## Integración continua

El workflow `.github/workflows/ci.yml` corre en cada pull request y en cada push a `main`: compila, ejecuta `./mvnw verify`, publica el reporte de JaCoCo y construye la imagen Docker. El análisis de SonarCloud se activa cuando el repositorio tiene el secreto `SONAR_TOKEN`.

## Flujo de trabajo

- `main` siempre estable; todo entra por pull request.
- Ramas `feature/<tema>-<id>` y `fix/<bug>-<id>`, una tarea por rama.
- Commits con prefijo `feat:`, `fix:`, `docs:`, `refactor:` o `test:`.
