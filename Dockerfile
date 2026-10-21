# Etapa 1: compila el jar con Maven (las pruebas corren en el CI, no en la imagen)
# Imagen base con Maven y JDK 17; el alias "build" permite copiar de esta etapa mas adelante
FROM maven:3.9-eclipse-temurin-17 AS build
# Carpeta de trabajo dentro del contenedor
WORKDIR /app
# Primero solo el pom: si no cambian las dependencias, Docker reutiliza la capa en cache y no las vuelve a descargar
COPY pom.xml .
# Descarga las dependencias del proyecto
RUN mvn -B -q dependency:go-offline
# Ahora si el codigo fuente (lo que mas cambia, por eso va despues)
COPY src ./src
# Genera target/backend-bpmn.jar sin correr pruebas
RUN mvn -B -q clean package -DskipTests

# Etapa 2: imagen liviana solo con el JRE y el jar
# Imagen final: solo el JRE, sin Maven ni codigo fuente, para que pese menos
FROM eclipse-temurin:17-jre
# Carpeta de trabajo de la imagen final
WORKDIR /app
# Usuario sin privilegios: el servidor no corre como root
RUN groupadd --system app && useradd --system --gid app app
# Trae solo el jar compilado de la etapa anterior
COPY --from=build /app/target/backend-bpmn.jar app.jar
# A partir de aqui todo se ejecuta como el usuario "app"
USER app
# Documenta el puerto en que escucha la API (el mismo de server.port)
EXPOSE 8090
# Comando que arranca la aplicacion al iniciar el contenedor
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
