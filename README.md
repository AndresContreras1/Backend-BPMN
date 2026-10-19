# Backend BPMN

Backend REST del visor y editor de procesos BPMN multiempresa. Cada empresa se registra, administra sus usuarios y modela sus procesos (pools, lanes, actividades, gateways, arcos, mensajes y correlación). El sistema modela y valida los diagramas; no los ejecuta.

## Stack tecnológico

- Java 17
- Spring Boot 3.5 (Web, Data JPA, Validation)
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

## Perfiles

| Perfil | Base de datos | Uso |
|---|---|---|
| `default` | H2 en archivo, `create-drop` | Desarrollo local |
| `test` | H2 en memoria, `create-drop` | Pruebas automatizadas |
| `prod` | PostgreSQL, `update` | Docker Compose y despliegue |

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `PORT` | Puerto HTTP de la API | `8090` |
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
