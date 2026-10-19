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
