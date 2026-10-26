# CLAUDE.md — MiArriendo (app Android + backend)

Reglas para Claude en este proyecto:
- Responder en español.
- ⚠️ Antes de probar cualquier pago, confirmar si el backend usa llaves `test_` o de producción: con producción se cobra dinero real.
- En el teléfono de pruebas (Samsung A34) solo tomar capturas; no tocar la pantalla ni abrir apps sin permiso explícito.
- Figma es la fuente de verdad de colores y tipografía.
- No subir secretos: `.env*`, `local.properties` y `backend/.cuentas-prueba.json` quedan fuera de git.

---


Última actualización: 13 de octubre de 2026. Unifica los 5 resúmenes de chat que Edward compartió (10–13/10/2026) y lo revisado en GitHub.

## 1. Qué es

MiArriendo es una plataforma de arriendos para Colombia con dos roles: **propietario** e **inquilino**. El inquilino busca, solicita y paga el arriendo desde la app; el propietario publica inmuebles, acepta solicitudes y ve el recaudo.

## 2. Dónde está cada cosa

| Pieza | Ubicación | Estado |
|---|---|---|
| **App Android + backend (proyecto principal)** | `C:\Users\Usuario\AndroidStudioProjects\miarriendo_v1` (local) | Casi todo **sin commitear**. Un solo commit de la auditoría (`0d0e8f7` en `master`). |
| Backend Node | `backend/` dentro del proyecto anterior | Desplegado en https://miarriendo-backend.vercel.app (Vercel Hobby). |
| Diseño | Figma `Design-miarriendo-v2` (clave `5TJ1ZTIwxrnrpYylEzp3Vx`), páginas `Vistas` y `Components` | Auditado y corregido el 13/10. |
| Firebase | proyecto `miarriendo-ff0a9` (Auth + Realtime Database) | Plan gratuito (Spark), sin Storage. |
| Repo GitHub `edwardtrigosp/miarriendo_movil_v1` | también en `C:\Users\Usuario\StudioProjects\miarriendo_movil_v1` | Versión vieja: 5 pantallas, un commit "Primera version". **No** es el proyecto actual. |
| Repo GitHub `edwardtrigosp/miarriendo_v1` | — | Es la **web en PHP** (MVC propio, MySQL, Docker, despliegue en cPanel), no la app Android pese al nombre. |
| Respaldo de la base | `Documents\miarriendo_respaldo_2026-10-10.json` | Hecho antes de vaciar la base el 10/10. |
| Parche de Storage | `Documents\miarriendo_firebase_storage.patch` | Subida de fotos a Storage + Glide; revertido por exigir plan Blaze. |

> ⚠️ El nombre `miarriendo_v1` se usa para dos cosas distintas: la carpeta local de la app Android y el repo GitHub de la web PHP.

## 3. Stack de la app

- **Android en Java**: ~60 clases, ~29 pantallas. Paquete `com.example.miarriendo_v1` (hay que cambiarlo para Play).
- **Firebase Auth** (registro, login, huella, recuperar contraseña por correo) y **Realtime Database** (usuarios, propiedades, fotos, solicitudes, cuotas, abonos).
- **Google Maps** y huella digital.
- Fotos en **base64 dentro de la base** (300–500 KB por propiedad; 1280 px, JPEG 70 %), porque Storage exige Blaze.
- Tipografía: Roboto del sistema (`sans-serif`) a propósito; Figma usa Roboto Flex. Íconos Material Symbols.
- Colores: **Figma manda**. `colors.xml` usa roles `md_theme_*` que apuntan a `colors_tokens.xml`.
- Navegación por rol: `Pantallas.abrirInicio` abre el inicio que corresponde.
- `BACKEND_URL` va en `local.properties` (ignorado por git, igual que `.env*`).

### Pantallas principales
- **Login/huella**: modo contraseña y modo huella; si cierras sesión con huella activa, la próxima vez abre la huella.
- **Propietario**: inicio (saludo, tarjetas con chip de estado, agregar propiedad), detalle de propiedad (portada, inquilino, próximo pago, historial, solicitudes, "Cambiar fotos" hasta 10), publicar propiedad (pasos con título, pantalla "Publicando"), pagos por inmueble, solicitudes recibidas, cuenta bancaria.
- **Inquilino**: inicio, filtros, resultados, detalle con galería "1 de N", solicitar arriendo (2 pasos), solicitud enviada, mis solicitudes (en vivo), configuración (huella, cerrar sesión), Mi arriendo, forma de pago, Pagar con Nequi, Aprueba en Nequi, resultado (`ResultadoPagoActivity` espera el estado definitivo), seguimiento de abonos, comprobante, historial.

## 4. Backend y pagos

- **Node 24 + Express en Vercel**, cobra con **Wompi, solo Nequi** (`wompiApi.js` siempre usa `type: 'NEQUI'`).
- Endpoints: aceptar solicitud y crear cuotas; cobrar en un pago, en 3 abonos o el saldo; consultar estado; webhook `/webhooks/wompi` con firma verificada.
- Cron diario a las 10:00 UTC (5:00 a. m. Colombia) que recalcula "al día"/"atrasado".
- El servidor calcula los montos y bloquea dos cobros simultáneos sobre la misma cuota.
- No arranca con llaves de producción salvo `PERMITIR_PRODUCCION=si`.
- `jose` 5 forzado con `overrides` en `backend/package.json` para que `firebase-admin` arranque en Vercel.
- Solo 3 pantallas llaman al backend: `PagarNequiActivity`, `ConfirmarNequiActivity`, `SolicitudesRecibidasActivity`. La app envía el token de Firebase; el servidor escribe en la misma Realtime Database.
- Tests: backend 25 pasan y 2 se omiten (necesitan emuladores); JUnit de la app para reglas de pago.

### Reglas de Firebase (`app/database.rules.json`, se publican con `firebase.json` y `.firebaserc`)
- `usuarios/{uid}`: cada uno lee y escribe lo suyo; `nombre` lo lee cualquiera con sesión.
- `propiedades`: lectura con sesión; escribe solo el dueño.
- `solicitudes_arriendo`: el inquilino crea en `pendiente`; el propietario actualiza; cada uno consulta por su uid.
- Pagos: **solo el servidor** escribe.

### Pago real de prueba (13/10/2026, 12:02)
$2.000 por Nequi en Wompi producción, aprobado y registrado por webhook en 2 s. Comprobante `MA-NOV26-PU-558`. El dinero quedó en la cuenta Wompi **bycreativos**.

### Comisión de Wompi
2,65 % + $700 fijos + IVA 19 %. En $2.000 fue $896 (44,8 %); en $1.000.000 sería ~3,2 %. Cada abono vuelve a cobrar los $700. Hoy la comisión la absorbe el comercio y la app no la muestra en ningún lado.

### Alternativas investigadas
| Opción | Comisión | Esfuerzo |
|---|---|---|
| PSE por Wompi (permite Nu) | igual a Wompi | moderado, reutiliza el webhook |
| Tarjeta por Wompi | igual o más | mayor (tokenizar) |
| **Cobre** (Checkout o Request to Pay, Bre-B/PSE/Nequi) | no pública, ~1 % de referencia | otro proveedor; pide NIT y KYB; puede pagar al propietario a su llave Bre-B |
| Bre-B directo a la llave del propietario | $0 | sin webhook, confirmación manual |

Notas técnicas de Cobre: `POST /v1/auth` (token de 20 min), montos en la unidad mínima, header `idempotency`, webhooks HMAC-SHA256 sobre `timestamp + "." + body` con solo 3 reintentos (hay que consultar por GET de respaldo), QA en `https://api.qa.cobre.co`.

## 5. Diseño (Figma)

- Sistema de diseño auditado (16 hallazgos). Decisiones: Figma manda en colores; controles a 48 de alto; obsoletos movidos a `Archivo · Obsoleto (no usar)` sin borrar; la sección de Pagos oficial está marcada `(OFICIAL)` y la otra `[Referencia]`.
- Sin cambiar a propósito: ancho de pantalla 369 (no 360), colección `M3 · Miarriendo` (en uso), pines del mapa a 32, textos de 11 px de `Label / Small`.
- Hay una versión Material 3 de todas las pantallas del propietario en una página aparte.
- Propuestas de inicio: secciones **05B** (inquilino con arriendo), **05C** (inquilino sin arriendo), **02B** (propietario) y **00 Mapa de módulos**, con 11 componentes Módulo nuevos.
- Auditoría de patrones de Nu (9 patrones y backlog): https://claude.ai/artifact/KadfaXT1QWf31SZRkwe7fU

## 6. Decisiones tomadas

1. **Backend propio en Node/Vercel** en lugar de pagar Firebase Blaze.
2. **Fotos en base64** hasta pasar a Blaze; el parche de Storage queda guardado.
3. **Figma es la fuente de verdad** de colores y tipografía.
4. **Un solo inicio por módulos** (`InicioActivity`) que reemplaza `InquilinoInicioActivity` y `PropietarioInicioActivity`. Regla: mostrar menos opciones y nunca deshabilitadas. Plan técnico: https://claude.ai/artifact/Kch6n72ZWNQAKFuNGdFPzD
   - `PlanDeInicio` (Java puro, con pruebas), `ModuloInicio` (`iniciar`/`detener`), `DatosInicio`.
   - Fases 0 a 4 detrás del interruptor `INICIO_UNIFICADO`. No requiere cambiar reglas de Firebase.
5. En el teléfono de pruebas (Samsung Galaxy A34) solo se toman capturas; no se toca la pantalla salvo permiso explícito.

## 7. Estado actual y riesgos

- ⚠️ **El backend está en PRODUCCIÓN**: cualquier pago desde la app cobra dinero real.
- ⚠️ **Nada está en git** salvo el commit `0d0e8f7`, que no compila por sí solo (falta `dimens.xml`, `strings_*.xml` y layouts).
- ⚠️ Las llaves sandbox de Wompi se pegaron en un chat: conviene regenerarlas.
- ⚠️ `.claude/settings.local.json` aún permite `shell input` (tocar cualquier app del teléfono).
- Datos de prueba en el Firebase real: tres cuentas `@miarriendo.test` (contraseñas solo en `backend/.cuentas-prueba.json`), un inmueble de $450.000 y "Prueba real ($2.000)". Además, tres casas de ejemplo a nombre de Edward en Aguachica, Río de Oro y Ocaña.
- El teléfono tiene sesión de `inquilino.real@miarriendo.test`.

## 8. Pendientes

**Urgente**
1. Volver a sandbox: llaves `test_` en `backend/.env`, luego `node backend/scripts/subir-env-vercel.js` y `vercel deploy --prod --cwd backend` (opcional: `vercel env rm PERMITIR_PRODUCCION production --cwd backend`).
2. Hacer commit de todo el trabajo (y subirlo a un repo).
3. Regenerar las llaves sandbox de Wompi.
4. Quitar la regla `shell input` de `.claude/settings.local.json`.

**Producto**
5. Respuesta del propietario a solicitudes (aceptar/rechazar) en diseño.
6. Mensaje de rechazo de pago con texto propio (hoy muestra el de Wompi).
7. Pago al propietario: hoy el dinero queda en Wompi (manual, Pagos a Terceros de Wompi, o Cobre).
8. Inicio unificado: empezar por la **fase 0** cuando se resuelvan las preguntas de abajo.
9. `diaDePago` en publicar; recordatorios antes del vencimiento (no hay FCM).
10. Opciones del perfil del inquilino que siguen en "Próximamente"; "No disponible" se corta en la versión M3.

**Legal y negocio**
11. Campo `deposito` frente a la Ley 820 (prohíbe depósitos en dinero).
12. Revisión legal del recaudo a nombre de terceros.
13. Contactar a Cobre: caso de uso, tarifa Bre-B y pagos a terceros, credenciales de QA.
14. Vercel Pro si se va a cobrar a terceros (Hobby es solo para uso personal).

**Google Play**
15. Cambiar el paquete `com.example.miarriendo_v1` y registrarlo en Firebase.
16. Opción "Eliminar cuenta" en la app y por enlace web.
17. Clave de firma y `.aab`; cuenta de desarrollador (USD 25); política de privacidad.
18. Prueba cerrada: 12 testers durante 14 días.

**Figma (limpieza)**
19. Borrar el archivo de prueba "miarriendo · Propuesta de inicio" en Borradores.
20. Renombrar los campos cruzados de `PropertyCard` ("Title" muestra el precio).
21. Compartir los dos artifacts (auditoría Nu y plan técnico), hoy privados.

## 9. Preguntas abiertas

1. ¿Quién paga la comisión: inquilino, propietario o plataforma?
2. ¿Seguir con Wompi (+PSE) o migrar a Cobre/Bre-B?
3. ~~¿Encabezado del inicio morado (estilo Nu) o blanco?~~ Decidido el 26/10/2026: **blanco** (opción 3), con las sugerencias en tarjetas `surface/high` (lila claro) sin borde. Aplicado en Figma y en código.
4. ¿"Activar recordatorios" en la v1 se oculta o usa notificaciones locales?
5. ¿El recaudo mensual lo calcula la app o el servidor?
6. ¿Qué solicitud mostrar si el inquilino tiene varias?
