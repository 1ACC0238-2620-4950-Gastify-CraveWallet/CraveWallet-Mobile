# CraveWallet · App móvil (Android)

App para controlar suscripciones (streaming, productividad, gimnasio, delivery…) con todo expresado en **soles**:
cuánto pagas al mes, qué se cobra pronto, qué no estás usando y cuánto subió tu gasto por el tipo de cambio.

Implementa los wireframes y el Design System del Figma
[Mobile UX/UI](https://www.figma.com/design/lIN0zLBZ4E0PmQudY5JOip/Mobile-UX-UI).

## Conexión con el backend

Esta rama agrega registro, login, sesión JWT, suscripciones y gastos de Delivery
contra `CraveWallet-Backend`. Al abrirla, puedes **iniciar sesión** o elegir
**Ver demostración sin conexión**. La demostración está identificada en pantalla.

La dirección predeterminada es **https://cravewallet-api.onrender.com**, con
PostgreSQL alojado en Render. Puedes usarla con Internet sin encender la PC
del equipo. El servidor gratuito puede tardar unos minutos en despertar.

Para desarrollo local, cambia el servidor a `http://10.0.2.2:8080` en el emulador;
por USB utiliza `adb reverse tcp:8080 tcp:8080` y `http://127.0.0.1:8080`. Consulta la [guía de integración](docs/backend-integration.md)
para los contratos, pruebas y límites de este avance.

## Tecnología

| | |
|---|---|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 (tema propio con los tokens del Design System) |
| Navegación | Navigation Compose |
| Estado | `ViewModel` + `StateFlow` |
| Persistencia | Backend REST para suscripciones y Delivery; preferencias, notas y datos de demostración en SharedPreferences |
| Recordatorios | WorkManager (notificaciones push) y `CalendarContract` (evento en el calendario) |
| Tipo de cambio | En sesión conectada proviene del backend, con fecha y atribución. La demostración utiliza la consulta pública original |
| Fuentes / íconos | Poppins + Inter · Material Symbols Rounded (los mismos del Figma) |

- `minSdk` 26 (Android 8.0) · `targetSdk`/`compileSdk` 37
- Android Gradle Plugin 9.4 · Gradle 9.8 · Kotlin 2.4

## Cómo ejecutarla

1. Abre la carpeta del proyecto en **Android Studio** (File › Open).
2. Espera a que termine la sincronización de Gradle.
3. Elige un emulador o un teléfono con depuración USB y pulsa **Run ▶**.

Desde la terminal:

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Pantallas implementadas

| Wireframe | Pantalla | Historias |
|---|---|---|
| I1, I2, I3, I4 | Inicio (con datos, vacío, después de agregar con y sin calendario) | US04, US05, US08–US10, US12, US14, US17, US35, US36 |
| G1, G2 | Gastos: búsqueda, filtros por estado y categoría, orden, deslizar para marcar "Sin usar" | US06, US10, US15, US27 |
| G3 | Detalle de la suscripción: monto en soles, variación por tipo de cambio, historial, notas, editar, cancelar | US11, US15, US16, US29, US37 |
| A1–A8 | Alta en 3 pasos: elegir servicio, datos con validación, moneda, vista previa en soles, recordatorio, permiso de calendario, descartar | US04, US05, US12, US14, US15, US16, US34 |
| A9 | Límite del plan gratuito (5 suscripciones) | US21, US39 |
| N1–N6 | Análisis: mes / 6 meses / año, barra seleccionable con detalle, hallazgo, por categoría, detalle de categoría, sin datos, solo Premium, sin conexión | US08, US09, US15, US16, US17, US21, US37, US39 |
| P1–P9 | Perfil y recordatorios: canales, permiso de notificaciones, anticipación, hora, próximos avisos, guardar con "Deshacer", notificaciones bloqueadas | US03, US12, US28, US33, US36 |

## Estructura

```
app/src/main/java/com/cravewallet/app/
├── MainActivity.kt, CraveApplication.kt, AppViewModel.kt
├── data/        Modelos, repositorio, cálculos de análisis, formatos en español, catálogo de servicios
├── reminders/   Notificaciones (WorkManager) y eventos de calendario
└── ui/
    ├── theme/       Colores y tipografía del Design System
    ├── components/  Tarjetas, chips de estado, botones, campos, stepper, bottom sheet
    ├── home/  expenses/  add/  analysis/  profile/  premium/
    └── AppShell.kt  Navegación inferior, rutas, snackbar y botón "Agregar"
```

## Modo demostración

Se abre desde la pantalla de acceso; Perfil permite salir para iniciar sesión.

En **Perfil › Demostración**:

- **Restablecer datos de ejemplo**: Renzo, plan Premium, 7 suscripciones (como en los wireframes).
- **Empezar sin suscripciones**: Camila, plan gratuito, sin datos. Sirve para ver el inicio vacío,
  el Análisis bloqueado y el límite de 5 suscripciones.

Las fechas de ejemplo se calculan a partir del día de hoy, así siempre hay un cobro "mañana" y otros "pronto".
El plan Premium es simulado: no se realiza ningún cobro.

## Permisos

| Permiso | Para qué |
|---|---|
| `POST_NOTIFICATIONS` | Avisos push antes de cada cobro (se pide desde Perfil › Recordatorios) |
| `READ_CALENDAR`, `WRITE_CALENDAR` | Crear un evento antes de cada cobro. Si el teléfono no tiene calendarios, se crea uno local llamado "CraveWallet" |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Actualizar el tipo de cambio y mostrar el aviso "Sin conexión" |

## Licencias

Fuentes Poppins e Inter bajo SIL Open Font License (ver `licenses/`). Íconos Material Symbols de Google bajo Apache 2.0.
