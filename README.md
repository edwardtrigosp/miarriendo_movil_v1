# MiArriendo

App Android para arrendar vivienda en Colombia, con dos roles:

- **Inquilino:** busca en el mapa, solicita el arriendo, paga cada mes con Nequi, ve su contrato y reporta daños.
- **Propietario:** publica sus inmuebles, acepta o rechaza solicitudes y sigue el recaudo de sus arriendos.

Incluye un backend en Node (carpeta `backend/`) que cobra con Wompi (Nequi) y crea las cuotas al aceptar una solicitud.

## Tecnología

| Parte | Detalle |
|---|---|
| App | Android en Java, Material Components 3, minSdk 33 |
| Datos | Firebase Authentication y Realtime Database (proyecto `miarriendo-ff0a9`) |
| Mapa | Google Maps SDK |
| Backend | Node 24 + Express, desplegado en Vercel (`backend/`) |
| Pagos | Wompi, solo Nequi |
| Diseño | Figma `Design-miarriendo-v2` (colores y tipografía salen de ahí) |

## Abrir y ejecutar la app

1. Clonar o descargar el repositorio (botón **Code → Download ZIP**).
2. Abrir la carpeta raíz en **Android Studio** (Ladybug o más reciente) y esperar la sincronización de Gradle.
3. (Opcional) Crear `local.properties` en la raíz con:

   ```properties
   # Mapa del inquilino (sin esta clave el mapa se ve en blanco)
   MAPS_API_KEY=tu_clave_de_google_maps
   # Backend para pagos y para aceptar solicitudes
   BACKEND_URL=https://miarriendo-backend.vercel.app
   ```

   `local.properties` no se sube a Git. Sin el archivo, la app compila igual: el mapa queda vacío y el backend apunta a `http://10.0.2.2:8080` (un servidor local visto desde el emulador).
4. Ejecutar la configuración **app** en un emulador o teléfono con Android 13 o superior.

Desde la terminal:

```bash
./gradlew :app:assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`.

## Backend (opcional)

```bash
cd backend
npm install
cp .env.example .env   # completar con llaves de prueba de Wompi y la cuenta de servicio de Firebase
npm start              # http://localhost:8080
npm test
```

El servidor no arranca con llaves de producción de Wompi salvo que se defina `PERMITIR_PRODUCCION=si`. Para revisar la app use siempre llaves de prueba (`pub_test_…`, `prv_test_…`).

## Estructura

```
app/                      App Android
  src/main/java/.../      Pantallas (Activity), módulos del inicio y reglas de negocio
  src/main/res/           Layouts, colores (colors_tokens.xml), textos (strings_*.xml)
  src/test/               Pruebas JUnit (reglas de pago, plan del inicio, contrato)
  database.rules.json     Reglas de seguridad de Realtime Database
backend/                  Servidor de pagos (Wompi) y cuotas
docs/                     Pendientes de la auditoría UI/UX
```

## Pruebas

```bash
./gradlew :app:testDebugUnitTest
```

## Notas

- Las fotos de los inmuebles se guardan en base64 dentro de la base de datos (el plan gratuito de Firebase no incluye Storage).
- Los pagos que se hagan con un backend configurado con llaves de producción cobran dinero real.
