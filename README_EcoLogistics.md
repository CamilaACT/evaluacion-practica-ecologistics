# Evaluación Práctica - EcoLogistics

##  Descripción del Proyecto
Este proyecto implementa una integración completa con **Apache Camel**, que permite leer archivos CSV con información de envíos, transformarlos a formato JSON y exponerlos mediante una API REST documentada con OpenAPI.

El flujo de procesamiento sigue el modelo de **Integración basada en Archivos (File Transfer)** y **Exposición mediante APIs REST**.

---

##  Tecnologías Utilizadas
- **Java 17**
- **Apache Camel 4.14.1**
- **Maven**
- **Netty HTTP (Servidor embebido)**
- **OpenAPI / Swagger**
- **Postman (para pruebas)**

---

##  Arquitectura General
```
input/ (CSV) ──► Camel File Route ──► output/ (JSON) ──► API REST
                             │
                             └──► archived/ (histórico CSV)
```

1. **input/** → se colocan los archivos CSV nuevos con envíos.
2. **Camel** lee, convierte a JSON y guarda el resultado en `output/`.
3. Los datos se mantienen también en memoria (`EnvioStore`) para consultas rápidas.
4. La API REST (`/api/envios`) permite listar, consultar y registrar nuevos envíos.
5. Los archivos CSV procesados se mueven a `archived/` con timestamp.

---

## Endpoints Principales

### GET `/api/ping`
Verifica que el servicio esté en línea.
```json
{"status": "ok", "service": "EcoLogistics", "ts": "2025-10-29 19:45:00"}
```

### GET `/api/envios`
Devuelve la lista completa de envíos cargados.

### GET `/api/envios/{id}`
Obtiene un envío específico por su ID.

### POST `/api/envios`
Crea un nuevo envío en memoria y actualiza `output/envios.json`.
Ejemplo de cuerpo JSON:
```json
{
  "id": "004",
  "cliente": "Ana Mora",
  "direccion": "Loja",
  "estado": "En tránsito"
}
```

---

##  Estructura del Proyecto
```
evaluacion-practica-ecologistics/
├── input/                # CSV de entrada
├── output/               # JSON generados
├── archived/             # CSV procesados (histórico)
├── src/
│   ├── main/java/edu/udla/isw/
│   │   ├── App.java           # Configuración principal de Camel
│   │   ├── model/Envio.java   # Modelo de datos
│   │   └── store/EnvioStore.java  # Almacenamiento en memoria
│   └── resources/openapi.yaml     # Documentación OpenAPI
├── pom.xml
└── README.txt
```

---

##  Ejecución del Proyecto

1. **Compilar el proyecto**
   ```bash
   mvn clean package -DskipTests
   ```

2. **Ejecutar la aplicación**
   ```bash
   mvn exec:java
   ```

3. **Probar los endpoints**
   - `http://localhost:7760/api/ping`
   - `http://localhost:7760/api/envios`
   - `http://localhost:7760/api/envios/{id}`
   - `POST http://localhost:7760/api/envios`

4. **Ver la documentación OpenAPI**
   - `http://localhost:7760/api/api-doc/openapi.json`

---

##  Pruebas con Postman

Importa la colección `postman_collection.json` incluida en el proyecto para probar:
- GET /envios
- GET /envios/{id}
- POST /envios

---

##  Autor
**Camila Cabrera**  
Universidad de las Américas (UDLA) – Integración de Aplicaciones  
Quito, Ecuador
