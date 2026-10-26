# Pendientes de la auditoría UI/UX (23/10/2026)

Hallazgos de la auditoría hecha en el Samsung A34 con la app real (Maira propietaria, Edward inquilino).
Evidencias y textos completos: `Descargas/auditoria_capturas/para_cargar/` (hallazgos.md + itemNN.jpg).
Marcar con [x] lo que se vaya corrigiendo.

## Accesibilidad

- [ ] **Objetivos táctiles < 48 dp (ítem 15).** `Widget.Miarriendo.IconButton` (botón atrás, flechas del calendario) de 40 → 48 dp; avatar del inicio 40 → 48 dp; chips con `ensureMinTouchTargetSize=false` → true (`Widget.Miarriendo.Chip`); días del calendario de 34 dp (`calendario_dia`); botones − / + de Solicitar de 40 dp; cerrar tarjeta del mapa de 32 dp. Pantalla más afectada: Solicitar arriendo (16 de 18). **Figma listo (26/10):** días del calendario a 48 de alto (ancho ~42, limitado por el ancho de pantalla) con círculo de 40, flechas del mes en áreas de 48×48, botones − / + del CounterCard y de Solicitar a 48, opciones No/Sí a 48 de alto, "Cerrar" de la tarjeta del mapa a 48 y avatar del encabezado a 48. En Figma el botón atrás (TopAppBar) y los chips ya medían 48: lo que falla es el código (`Widget.Miarriendo.IconButton` 40 dp, `ensureMinTouchTargetSize=false`).
- [ ] **TalkBack (ítem 17).** El ícono de inicio de los campos (`app:startIconDrawable`) es un botón sin etiqueta en 27 campos: agregar `startIconContentDescription` o volverlo no tocable. Revisar 6 ImageView sin etiqueta ni `importantForAccessibility="no"`. Los pines del mapa no son accesibles (la alternativa es "Ver lista").
- [ ] **Foco visible (ítem 20).** Solo el sombreado gris del sistema; casi invisible sobre el encabezado lila. Definir un indicador de foco (contorno 2–3 dp en brand_primary). **Figma listo (26/10):** variante `Estado=Foco` con velo de brand/primary al 12 % (estado "focused" de M3; se descartó el anillo porque WCAG AA solo pide que el foco se vea) en Atajo, IconButton y ListItem, y pantalla "Inquilino / Inicio · Foco con teclado". Falta en código: color con `state_focused` en el ripple/selector de atajos, botones de ícono y filas.
- [ ] **Texto al 200 % (ítem 18).** 10 textos con alto fijo en dp, 28 con `maxLines="1"`, 31 con ellipsize. Cambiar altos fijos por minHeight y permitir 2 líneas en títulos y etiquetas. Visto al 200 %: atajos del inicio cortados ("Mis", "Contrat", "Reporta"), "Mi arriendo · Octu…", "Vence el 23 de o…", sugerencias cortadas; en Mi contrato el canon se parte ("$350.00" / "0") y se cortan título y dirección; en Reportar los chips quedan apretados y "Foto (opcional)" queda bajo el botón fijo. Para probar: `./gradlew.bat :app:installDebug -PescalaTexto=2.0` (solo debug).
- [ ] **Borde de tarjetas (ítem 16).** `border_card` tiene 1,19:1 sobre blanco; oscurecer un poco. **Figma listo (26/10):** `border/card` (Light) pasa de purple-100 a purple-200 (#DDD6FE, 1,39:1). Falta en código: `border_card` → `@color/purple_200` en `colors_tokens.xml`.

## Interfaz

- [ ] **Botón desactivado igual al activo (ítem 05).** `Widget.Miarriendo.Button.Primary` usa `backgroundTint` fijo (brand_primary); usar un ColorStateList con el estado deshabilitado de M3. Afecta "Enviar reporte", "Pagar con Nequi", "Enviar solicitud", "Descargar en PDF". **Figma listo (26/10):** variante `PrimaryButton · Estado=Deshabilitado` (fondo text/primary 12 %, texto 38 %); "Reportar un daño" en App actual ya lo muestra con la descripción vacía. Falta en código.
- [ ] **Modo oscuro (ítem 04).** `values-night/themes.xml` repite el tema claro y `Pantallas.edgeToEdge` fuerza `SystemBarStyle.light`. Crear `values-night/colors_tokens.xml` y usar `SystemBarStyle.auto`. **Figma listo (26/10):** modo **Dark** en "Miarriendo / Semantic" (las 44 variables, con 24 primitivas `dark/*` nuevas y `surface/header` para el encabezado lila) y 2 pantallas de muestra "· Modo oscuro" (Inicio y Mi contrato). Los valores de `values-night` salen de esas primitivas (p. ej. surface #141218, card #1D1B20, texto #E6E0E9, marca #C4B5FD).
- [ ] **Navegación (ítem 01).** Sin Bottom Navigation Bar para el inquilino (4–5 destinos). Publicar no indica el paso (1 de 5).
- [x] **Diálogos con estilos distintos (ítem 13).** Implementado el 26/10: clase `Dialogo` + `dialog_miarriendo.xml` (Informativo, Confirmar, Destructivo, con campo, foto o lista) en los 8 diálogos de la app. Cerrar sesión usa botones con borde; aceptar/rechazar solicitud usan el estilo por defecto de Material. Unificar. **Figma listo (26/10):** `Dialog` es ahora un set (Tipo=Destructivo para Cerrar sesión/Rechazar, Tipo=Confirmar con botón principal para Aceptar) y hay 2 pantallas "Solicitudes recibidas · Confirmar aceptar / rechazar". Falta en código: usar el mismo layout de diálogo en `SolicitudesRecibidasActivity`.
- [ ] **Íconos mezclados (ítem 07).** 7 íconos Material Icons Outlined (ic_send, ic_inbox, ic_lightbulb, ic_remove, ic_chevron_left, ic_download, ic_add_a_photo) entre 58 Material Symbols Rounded.
- [ ] **Retícula (ítem 02).** 51 espaciados fuera de 4/8 dp (márgenes de 6 dp, ±6 dp, rellenos de íconos); pasarlos a tokens.
- [ ] **Estilos de texto (ítem 03).** Mapear H1/Body… a los roles de M3 (opcional).

## Prueba de tareas (ítem 08, simulada con agentes el 24/10)

- [ ] **El inquilino con arriendo no puede buscar (T1 falló 2 de 2).** El inicio no tiene entrada al mapa ni a buscar; el diálogo "Así funciona" dice "Busca en el mapa" sin enlace. Agregar un atajo o módulo "Buscar". **Figma listo (26/10):** fila "Buscar otro hogar en el mapa" (`Módulo / Buscar hogar (fila)`) debajo de "Detalles de mi arriendo" en los 3 inicios del inquilino con arriendo de App actual. Falta en código (`PlanDeInicio`, caso INQUILINO_CON_ARRIENDO) y el botón "Ir al mapa" del diálogo "Así funciona".
- [x] **"Detalles de mi arriendo" y "Ver solicitud"** (implementado el 26/10: `DetalleArriendoActivity` en modo "mi arriendo" cuando `inquilinoId` es el usuario) abren la ficha de la casa arrendada con el chip "Disponible" y el botón "Solicitar arriendo". **Figma listo (26/10):** pantalla "Inquilino / Detalle de mi arriendo" en App actual (título "Mi arriendo", chip "Tu arriendo · desde …", botones "Ver mi contrato" y "Reportar un daño", sin "Solicitar arriendo"). Falta en código: modo "mi arriendo" en `DetalleArriendoActivity` cuando la propiedad es del inquilino.
- [ ] **Sin confirmación** al publicar, al enviar un reporte y al descargar el PDF (abre compartir sin decir dónde quedó). Usar Snackbar. **Figma listo (26/10):** componente `Snackbar` (Simple / Con acción; tokens `surface/inverse`, `text/on-inverse`, `brand/inverse-primary`) y 3 pantallas en App actual: "Reporte enviado al propietario", "PDF guardado en Descargas · Abrir", "Propiedad publicada · Ver". Falta en código.
- [ ] **Publicar:** "Nombre interno" sin explicación y es lo que muestra la tarjeta en vez del título; el teclado tapa campos (el texto terminó en el campo de barrio); la duración viene en 6 meses por defecto; fotos sin "Omitir"; depósito sin indicar si es opcional. **Figma listo (26/10):** StepperBar "Paso X de 5" en los 5 pasos; "Nombre interno (opcional)" con ayuda "Solo lo ves tú…"; fotos "Opcional…" + botón "Omitir por ahora"; duración vacía ("Selecciona la duración"); "Depósito de garantía (opcional)". Falta en código (y el teclado que tapa campos es solo de código: `adjustResize`/scroll al campo enfocado).
- [ ] **El reporte enviado no se ve en el inicio del inquilino.** **Figma listo (26/10):** sección "Tus reportes" con `MaintenanceItem` (chip "Enviado"; pasa a "Resuelto" cuando el propietario lo marca) en "Inquilino / Inicio · Reporte enviado". `MaintenanceItem` ahora deja el título en 2 líneas en vez de cortarlo. Falta en código: módulo en `PlanDeInicio` (INQUILINO_CON_ARRIENDO) que escuche `reportes` del inquilino.
- [ ] **"Ayuda y soporte"** en Configuración no hace nada. **Figma listo (26/10):** pantalla "Inquilino / Ayuda y soporte" (Preguntas frecuentes, WhatsApp, correo, Cómo funciona, Términos, Política de privacidad). Faltan en código la pantalla y los datos reales de contacto.
- [ ] **Texto del contrato:** "se avisa con un mes de aviso" (redundante; viene de las cláusulas escritas por el propietario).

## Formularios y validación

- [ ] **Registro marca un solo error a la vez (ítem 10).** Validar todos los campos juntos.
- [ ] **El error no se borra al corregir (ítem 10).** "Campo obligatorio" del título en Publicar sigue en rojo tras escribir; limpiar con TextWatcher.
- [ ] **Registro pide "Confirmar contraseña"** (campo duplicado, ítem 06). **Figma listo (26/10):** campo quitado de "Registro paso 1" y nueva pantalla "Registro paso 1 · Errores" con los 3 errores a la vez junto a cada campo (también cubre "marca un solo error a la vez"). Falta en código; para compensar, agregar el ojo "mostrar contraseña" (`app:endIconMode="password_toggle"`).

## Carga, estados vacíos y sin conexión

- [x] **Indicador de carga tapado en el inicio (ítem 11).** Implementado el 26/10 (`view_inicio_cargando.xml`, verificado en el A34). En `activity_inicio.xml` el ScrollView con fondo blanco tapa el CircularProgressIndicator: la pantalla queda en blanco mientras carga. Poner el indicador encima o usar skeletons. **Figma listo (26/10):** componente `Esqueleto` y pantalla "Inquilino / Inicio · Cargando" (encabezado lila, sugerencias, 4 atajos, Mi arriendo y filas en bloques grises `status/neutral`). Falta en código.
- [ ] **Aceptar solicitud sin feedback (ítem 11).** El botón no muestra progreso y al terminar no hay mensaje; mostrar "Aceptando…" y un Snackbar "Arriendo activado". **Figma listo (26/10):** "Propietario / Solicitudes recibidas · Aceptando" (botón en Cargando "Aceptando…", Rechazar desactivado) y "· Arriendo activado" (estado vacío + Snackbar "Arriendo activado con María López · Ver"). `SolicitudCard` corregida: botones de 52 del mismo ancho, filas al ancho de la tarjeta, botón Aceptar expuesto. Falta en código.
- [ ] **Estados vacíos solo con texto (ítem 12).** Solicitudes recibidas y Mis pagos (Historial): agregar acción o ilustración. **Figma listo (26/10):** componente `EstadoVacío` (ícono en círculo, título, descripción, botón) y pantallas "Propietario / Solicitudes recibidas · Vacía" (→ Ver mis propiedades) e "Inquilino / Mis pagos · Vacío" (→ Pagar arriendo). Falta en código (reemplazar los `tv_empty` de solo texto).
- [ ] **Sin modo offline (ítem 12).** No hay ConnectivityManager ni persistencia de Realtime Database; activar `setPersistenceEnabled` y avisar "Sin conexión". **Figma listo (26/10):** "Inquilino / Inicio · Sin conexión" con Alert de advertencia ("Sin conexión. Te mostramos los datos guardados…") bajo el encabezado y "Pagar arriendo" deshabilitado. Falta en código.

## Negocio y datos

- [ ] **La primera cuota vence el mismo día de aceptar** cuando la propiedad no tiene día de pago (Edward: aceptado y "Vence hoy" el 23/10). Pedir `diaDePago` al publicar o dar un plazo mínimo.
- [ ] **Nombre del PDF del contrato** usa el id interno (`contrato--P4enQ….pdf`); usar el título y el mes.
- [ ] **Ilustración de "Explorar en mapa"** muestra precios de ejemplo ($1,1M y $850 mil) que no existen.
- [ ] **Tarjeta de Mis propiedades** muestra el nombre interno ("Casa centro") en vez del título publicado (revisar si es lo deseado). **Decisión 26/10: mostrar el título.** Figma: la propiedad de `OwnerPropertyCard` se llama "Título del anuncio" y la descripción lo deja explícito (Figma ya mostraba el título). Falta en código: donde se usa `Propiedad.nombreParaTarjeta()` (prioriza `nombreInterno`) usar `tituloPublico()`.
- [ ] **Depósito de garantía** en Publicar 5 sigue visible (pendiente Ley 820). **Decisión 26/10: se quita.** Figma listo (campo eliminado de Publicar 5; no aparecía en otras pantallas de App actual). Falta en código: campo `deposito` en Publicar, modelo y lo que lo muestre (contrato/PDF).
- [ ] **Eliminar cuenta** no existe (requisito de Google Play). **Figma listo (26/10):** botón de texto rojo "Eliminar mi cuenta" bajo "Cerrar sesión" en los dos perfiles y pantalla "Configuración · Eliminar cuenta" con diálogo destructivo. Falta en código (y el enlace web que pide Google Play).
- [ ] **No hay "deshacer"** al marcar un daño como resuelto.

## Pendientes de la auditoría (no son código)

- [ ] Asignatura en la ficha técnica.
- [x] Captura del ítem 18 con letra al 200 % (23/10).
- [x] Ítems 08, 09 y 14: hechos el 24/10 con una prueba simulada (2 agentes de IA): éxito 75 %, SUS 45/100. Conviene confirmarlos con personas reales.
- [ ] Cambiar la contraseña de edwardtrigosp@gmail.com (quedó expuesta por uiautomator al leer el login).
- [ ] Decidir si se limpian los datos de prueba en Firebase (casa de Maira, arriendo con Edward, reporte abierto).
