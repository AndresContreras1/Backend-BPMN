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

Con los perfiles `default` y `demo` el inicializador (`DataLoader`) carga los datos usando los mismos servicios de la API, así que cumplen todas las reglas:

- **Solicitud de vacaciones** (publicado): el proceso del enunciado con lanes Empleado, Jefe inmediato y Talento Humano, gateway exclusivo Sí/No, notificación al pool externo *Servicio de correo* y correlación por número de solicitud. Compartido en solo lectura con Comercial Andina.
- **Aprobación de compras** (borrador): colaboración con el pool *Proveedor* mediante Message Throw y Message Catch correlacionados por número de orden.
- **Control de calidad 2024** (eliminado): visible con `GET /api/v1/procesos?activo=false`.
- Comercial Andina tiene su propio proceso **Solicitud de vacaciones**: el mismo nombre en otra empresa no interfiere.

Usuarios:

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
- Límite de intentos por IP en el login y el registro (cubo de fichas con Bucket4j): pasado el límite responde `429 Too Many Requests` con `Retry-After`. Por defecto 10 intentos por minuto en el login y 10 registros por hora.

## Endpoints

Todos bajo `/api/v1`. Los listados devuelven `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`). Los errores salen como ProblemDetail (`application/problem+json`) con dos propiedades propias: `codigo`, un código interno estable para que el cliente decida qué hacer sin leer el mensaje, y `timestamp`:

```json
{
  "type": "about:blank",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "No existe el proceso 999999",
  "instance": "/api/v1/procesos/999999",
  "codigo": "RECURSO_NO_ENCONTRADO",
  "timestamp": "2026-10-19T23:40:12.345Z"
}
```

| `codigo` | HTTP | Cuándo |
|---|---|---|
| `DATOS_INVALIDOS` | 400 | `@Valid` falló; `errors` trae el mensaje de cada campo |
| `PARAMETRO_INVALIDO` · `CUERPO_INVALIDO` · `PETICION_INVALIDA` | 400 | Parámetro u orden no permitido · JSON ilegible o con campos desconocidos · otro rechazo de Spring MVC |
| `CREDENCIALES_INVALIDAS` · `NO_AUTENTICADO` | 401 | Login fallido (mensaje genérico) · sin token, token inválido o vencido |
| `SIN_PERMISOS` | 403 | El rol de acceso no permite la operación |
| `RECURSO_NO_ENCONTRADO` | 404 | No existe o es de otra empresa |
| `METODO_NO_PERMITIDO` · `TIPO_DE_CONTENIDO_NO_SOPORTADO` | 405 · 415 | Método o `Content-Type` equivocados |
| `REGLA_DE_NEGOCIO` · `CONFLICTO_DE_EDICION` · `CONFLICTO_DE_DATOS` | 409 | Regla del negocio o del modelo BPMN (`detalles` cuando aplica) · versión desactualizada · restricción de la base |
| `DEMASIADAS_PETICIONES` | 429 | Límite de intentos del login o del registro |
| `ERROR_INTERNO` | 500 | Error inesperado, sin detalles internos (la traza va al log) |

En el log, los 404 se registran en `DEBUG`, los demás errores del cliente en `WARN` y los fallos internos en `ERROR` con la traza.

| HU | Método y ruta | Descripción | Rol |
|---|---|---|---|
| HU-01 | `POST /empresas` | Registrar empresa y su administrador inicial | público |
| HU-03 | `GET /empresas/actual` | Empresa del usuario autenticado | todos |
| HU-03 | `POST /auth/login` | Iniciar sesión (devuelve el JWT) | público |
| HU-03 | `GET /auth/yo` · `POST /auth/logout` | Usuario autenticado · cerrar sesión | todos |
| HU-02 | `GET /usuarios?nombre=` | Usuarios de mi empresa (paginado) | todos |
| HU-02 | `POST /usuarios` | Invitar usuario con rol y contraseña inicial | ADMINISTRADOR |
| HU-02 | `PUT /usuarios/{id}/rol` | Cambiar rol de acceso | ADMINISTRADOR |
| HU-02 | `PUT /usuarios/{id}/estado` (`{"activo": false}`) | Desactivar o reactivar sin borrar | ADMINISTRADOR |
| HU-07 | `GET /procesos?nombre=&estado=&categoria=&activo=` | Buscar, filtrar y paginar procesos | todos |
| HU-07 | `GET /procesos/categorias` | Categorías en uso | todos |
| HU-07 | `GET /procesos/resumen` | Indicadores del inicio: activos por estado, eliminados y compartidos conmigo | todos |
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
| HU-23 | `PUT` · `GET /procesos/{id}/comparticion` | Compartir por NIT en solo lectura · estado | ADMINISTRADOR de la empresa dueña |
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

Por defecto un proceso solo lo ve la empresa que lo creó. Su administrador puede compartirlo por NIT con otras empresas (`PUT /procesos/{id}/comparticion`): la invitada ve la ficha, el diagrama, los mensajes y las advertencias, pero no puede modificar nada (toda escritura responde 404), ni ve el historial, los usuarios o el catálogo de roles de la empresa dueña. Dejar de compartir o eliminar el proceso retira el acceso.

## Documentación de la API

Swagger UI en `http://localhost:8090/swagger-ui/index.html` (botón *Authorize* con el token) y contrato OpenAPI en `http://localhost:8090/v3/api-docs`. En `prod` queda publicado mientras `SWAGGER_ENABLED` no sea `false`.

## Perfiles

| Perfil | Base de datos | Uso |
|---|---|---|
| `default` | H2 en archivo, `create-drop` | Desarrollo local |
| `test` | H2 en memoria, `create-drop` | Pruebas automatizadas |
| `prod` | PostgreSQL con migraciones Flyway, `validate` | Docker Compose y despliegue |
| `demo` | (se combina con otro) | Carga los datos de demostración |

### Migraciones (perfil `prod`)

El esquema de PostgreSQL vive en `src/main/resources/db/migration` y lo aplica Flyway al arrancar, antes que Hibernate:

| Archivo | Qué hace |
|---|---|
| `V1__esquema_inicial.sql` | Tablas, restricciones `CHECK`, llaves foráneas (todas las tablas hacia `empresa`) e índices |
| `V2__nombres_unicos_entre_activos.sql` | Índices únicos parciales: el nombre de un proceso, rol, actividad o pool solo se repite con elementos dados de baja |

Hibernate arranca con `ddl-auto=validate`: si una entidad no cuadra con su tabla, la aplicación no levanta. Una migración aplicada no se edita; los cambios van en una versión nueva (`V3__…`). En desarrollo y pruebas (H2) Flyway está apagado y el esquema se recrea con `create-drop`.

Una base de PostgreSQL creada antes de Flyway (por ejemplo, el volumen de un `docker compose` anterior) se puede conservar arrancando una vez con `FLYWAY_BASELINE_ON_MIGRATE=true`, o empezar de cero con `docker compose down -v`.

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
| `FLYWAY_BASELINE_ON_MIGRATE` | Toma una base existente como V1 (perfil `prod`) | `false` |
| `SWAGGER_ENABLED` | Publica Swagger UI y `/v3/api-docs` (perfil `prod`) | `true` |
| `THREADS` | Hilos de Tomcat (perfil `prod`) | `200` |
| `DB_POOL` | Conexiones de Hikari a PostgreSQL (perfil `prod`) | `10` |
| `RATE_LIMIT_LOGIN` | Intentos de login por IP cada minuto | `10` |
| `RATE_LIMIT_REGISTRO` | Registros de empresa por IP cada hora | `10` |

## Pruebas

```bash
./mvnw verify
```

Ejecuta las pruebas unitarias (`*Test`) y de integración (`*IT`) y genera el reporte de cobertura en `target/site/jacoco/index.html`. JaCoCo exige 50 % de cobertura de línea en todo el proyecto y 70 % de línea / 60 % de rama en los paquetes de servicio.

| Suite | Qué prueba |
|---|---|
| `*RepositoryTest` (`@DataJpaTest`) | Consultas derivadas, JPQL, named queries, restricciones únicas y cascada |
| `*ServiceImplTest` (Mockito) | Cada regla de negocio y cada excepción |
| `*ControllerTest` (`@WebMvcTest`) | Mapeo, validación y códigos HTTP |
| `*IT` (`@SpringBootTest` + JWT real) | Las 28 historias de usuario de punta a punta, con una transacción por petición |
| `AislamientoEntreEmpresasIT` | Cada recurso de otra empresa responde 404 al leerlo, editarlo, borrarlo o crear dentro de él |
| `CascadaFisicaIT` | Borrar un proceso en la base elimina en cascada todo su modelo sin filas huérfanas |
| `DatosDemoIT` | El inicializador carga datos coherentes con las reglas |
| `MigracionesIT` (Testcontainers) | Flyway aplica V1 y V2 sobre PostgreSQL 16 real, Hibernate valida el esquema y los datos demo se guardan; se omite si no hay Docker |
| `RateLimitIT` | El login y el registro responden 429 con `Retry-After` al agotar el cubo, y el resto de la API no se ve afectada |
| `ArquitecturaTest` (ArchUnit) | Capas, nombres, multiempresa, inyección por constructor, sin `System.out`, seguridad sin repositorios salvo el `UserDetailsService` y servicios sin `findById`/`findAll` sin empresa |

### Pruebas de sistema (E2E con Selenium)

Simulan a un usuario en Chrome, del frontend hasta la base de datos. Viven en `src/test/java/.../e2e` con el patrón Page Object (`e2e/paginas`): cada pantalla es una clase con sus selectores, así las pruebas se leen como el caso de uso. Llevan `@Tag("e2e")`, así que `./mvnw verify` no las corre; necesitan el frontend arriba:

```bash
# 1. En Frontend-BPMN: ng serve con el proxy hacia el backend en 8090
npm start
# 2. En este repositorio (el backend no debe estar corriendo: la prueba lo levanta en 8090)
./mvnw verify -Pe2e
```

Cada prueba arranca el backend con los perfiles `test` y `demo` (base en memoria con los datos demo) y reinicia el contexto antes de cada caso (`@DirtiesContext`), así ninguna depende de otra. Chrome corre sin ventana; `-De2e.headless=false` lo muestra y `-De2e.url=...` cambia la URL del frontend. Al terminar cada caso queda una captura en `target/capturas/`.

| Clase | Nivel | Caso |
|---|---|---|
| `PaginaPublicaSistemaIT` | Sencillo | La página pública dibuja el ejemplo y lleva al login; credenciales incorrectas dan el mensaje genérico; una ruta interna sin sesión lleva al login con `returnUrl` |
| `ProcesosSistemaIT` | Medio | Eliminar un proceso deja la lista con uno menos y aparece en "Ver eliminados"; el filtro por estado deja solo los publicados |
| `RegistroYPrimerProcesoSistemaIT` | Caso de uso completo | Registrar una empresa (HU-01), entrar como su administrador, crear el primer proceso (HU-04) y encontrarlo en el inicio y en la lista, también al volver a entrar |
| `CompartirEntreEmpresasSistemaIT` | Caso de uso completo | Facimus comparte un proceso por NIT (HU-23); Comercial Andina lo ve en "Compartidos conmigo" en solo lectura y no entre sus propios procesos |
| `RolesDeAccesoSistemaIT` | Caso de uso completo | El administrador invita a un lector (HU-02); el lector consulta los procesos pero no puede crearlos, ni escribiendo la URL |

El workflow manual `.github/workflows/e2e.yml` hace lo mismo en GitHub Actions contra la rama del frontend que se elija.

### Prueba de carga (k6)

`test.js` (en la raíz) simula 20 usuarios que, después de iniciar sesión una vez, abren en bucle el resumen del inicio, la lista de procesos y un diagrama, con una pausa de 1 s entre iteraciones. Los umbrales hacen que la prueba falle sola: menos de 1 % de errores y el 95 % de las respuestas por debajo de 500 ms.

```bash
k6 run test.js
# o sin instalar k6:
docker run --rm -i -e BASE_URL=http://host.docker.internal:8090 grafana/k6 run - < test.js
```

Con la API en el perfil `default` en un portátil: 2 557 peticiones en 55 s, 0 % de errores, mediana de 4 ms y p95 de 7,5 ms.

## Postman

La colección `postman/BPMN-API.postman_collection.json` (con el entorno `postman/local.postman_environment.json`) recorre las 28 historias en orden: registra una empresa, inicia sesión, crea usuarios, roles, un proceso con su diagrama, una notificación externa con correlación, lo publica, lo comparte y lo elimina. Cada petición tiene su prueba de código HTTP y las variables (`token`, `procesoId`, `laneId`…) se guardan solas.

```bash
npx newman run postman/BPMN-API.postman_collection.json -e postman/local.postman_environment.json
```

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

## Eliminación en cascada

Lo que el usuario elimina es lógico (`activo = false`) para conservar la trazabilidad. Además, las llaves foráneas se generan con `ON DELETE CASCADE` (`@OnDelete`): si un proceso se borrara de la base, se irían sus pools, lanes, nodos, arcos, mensajes, campos, usos de datos, historial y comparticiones, sin tocar el catálogo de la empresa.

## Integración continua

El workflow `.github/workflows/ci.yml` corre en cada pull request y en cada push a `main`: compila, ejecuta `./mvnw verify`, publica el reporte de JaCoCo y construye la imagen Docker. El análisis de SonarCloud se activa cuando el repositorio tiene el secreto `SONAR_TOKEN`.

`.github/workflows/e2e.yml` corre las pruebas de sistema contra el frontend (ver **Pruebas de sistema**) y se lanza a mano.

`.github/workflows/deploy.yaml` es el despliegue del curso (imagen a GHCR y `microk8s kubectl apply -f k8s/deployment.yaml` en el runner self-hosted). Se ejecuta a mano hasta registrar el runner en la entrega 3.

## Documentación técnica

Arquitectura, modelo de datos, mapa de historias a clases, códigos HTTP y estrategia de pruebas en [docs/guia-tecnica.md](docs/guia-tecnica.md).

## Flujo de trabajo

- `main` siempre estable; todo entra por pull request.
- Ramas `feature/<tema>-<id>` y `fix/<bug>-<id>`, una tarea por rama.
- Commits con prefijo `feat:`, `fix:`, `docs:`, `refactor:` o `test:`.
