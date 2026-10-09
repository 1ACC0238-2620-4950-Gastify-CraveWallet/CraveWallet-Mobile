# Integración móvil con CraveWallet-Backend

La aplicación permite iniciar sesión contra la API REST o abrir una demostración
local. La demostración se identifica en pantalla y no escribe en el servidor.

## Ejecución local

1. En `CraveWallet-Backend`, iniciar el perfil local con Java 21 y
   `mvnw.cmd spring-boot:run`. Swagger se encuentra en
   `http://localhost:8080/swagger-ui.html`.
2. Abrir este proyecto en Android Studio y compilar `assembleDebug`.
3. En un emulador de Android, utilizar `http://10.0.2.2:8080` en el campo
   **Servidor de pruebas** del login. Esa dirección corresponde al equipo host.
4. Para un teléfono conectado mediante USB, ejecutar `adb reverse tcp:8080 tcp:8080`
   y utilizar `http://127.0.0.1:8080` en el teléfono.
5. Crear una cuenta con un correo de prueba y contraseña de al menos ocho
   caracteres, con letras y números. Las cuentas de Swagger se pueden usar si
   pertenecen al mismo servidor y no se ha reiniciado su base H2 en memoria.

No se requiere abrir el backend en la red local para las opciones anteriores.
El backend de desarrollo pierde los datos al reiniciarse; esta propiedad de H2
también invalida sus sesiones anteriores.

La URL se puede fijar al compilar mediante
`-PAPI_BASE_URL=https://servidor-del-equipo.example`. El campo para cambiarla y
HTTP sin TLS solo están habilitados en el APK de depuración. Una entrega de
producción debe definir una URL HTTPS real; no existe una URL pública verificada
del backend en este corte.

## Operaciones conectadas

| Acción en la app | Contrato del backend |
| --- | --- |
| Crear cuenta / ingresar | `POST /api/v1/auth/register` y `POST /api/v1/auth/login`. |
| Mantener la sesión | Bearer JWT; ante 401 se rota el refresh token y se reintenta una vez. La rotación se serializa para solicitudes concurrentes. |
| Consultar perfil | `GET /api/v1/users/me`. |
| Inicio, gastos, filtros y detalle de suscripciones | Listados `GET /api/v1/subscriptions?status=ACTIVE` y `status=CANCELLED`; los filtros locales operan sobre los registros recibidos. |
| Agregar una suscripción | `POST /api/v1/subscriptions`; se conserva el UUID asignado por el servidor. |
| Editar monto, categoría y fecha | `PATCH /api/v1/subscriptions/{id}`. |
| Cancelar una suscripción | `POST /api/v1/subscriptions/{id}/cancel`; permanece en el historial. |
| Total mensual y conversión | El total mensual PEN proviene del portafolio. La tasa y su fecha provienen de `GET /api/v1/exchange-rate` o de los metadatos del portafolio. Se muestra la atribución del proveedor en Perfil. |
| Delivery: guardar gasto | `POST /api/v1/delivery-expenses`; se mantiene el mismo `requestId` al reintentar el mismo formulario tras un error de red. |
| Delivery: consultar período | `GET /api/v1/delivery-expenses/summary?year=...&month=...`. |
| Delivery: presupuesto | `PUT /api/v1/delivery-expenses/budget`; un límite vacío envía null. |
| Actualizar / cerrar sesión | Perfil permite consultar nuevamente los datos y ejecutar `POST /api/v1/auth/logout`. |

Los mensajes de guardado y cancelación aparecen después de la respuesta del
servidor. Si solo falla la actualización posterior del resumen, se informa que
la operación se guardó y que hace falta actualizar los datos.

## Adaptación del diseño al contrato disponible

- En modo conectado se admiten PEN/USD y frecuencias mensual/anual. El nombre,
  moneda y frecuencia no se cambian en una edición porque el PATCH no los admite.
- La eliminación definitiva, la reactivación y Premium no están implementados
  en el backend. La app no los presenta como operaciones remotas exitosas.
- Los pagos históricos no se generan a partir de fechas vencidas. El servidor
  conserva la fecha de renovación; el cliente no la avanza de forma ficticia.
- Una tasa no disponible se representa como conversión no disponible; no se
  reemplaza por la tasa de los datos de ejemplo.
- Notas, marcas «sin usar», preferencias y eventos del calendario pertenecen al
  teléfono. Se guardan separadas por cuenta y no se presentan como campos
  sincronizados del contrato REST.
- Los recordatorios del teléfono mantienen la anticipación y hora elegidas por
  el usuario. El endpoint de recordatorio del backend entrega una propuesta de
  24 horas antes de medianoche y no agenda eventos por sí mismo.
- Las sesiones se guardan en preferencias privadas sin guardar la contraseña;
  el backup Android de la aplicación está deshabilitado.

## Verificación reproducible

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

La suite utiliza JUnit, Robolectric y MockWebServer para comprobar registro,
sesiones persistidas, rotación concurrente, cierre de sesión, PATCH, conflictos,
adaptación del portafolio y el login/logout de Compose.

Para incluir la prueba contra un backend real encendido:

```powershell
$env:CRAVE_LIVE_BACKEND = 'http://127.0.0.1:8080'
./gradlew.bat :app:testDebugUnitTest --no-daemon --rerun-tasks
```

Esta prueba crea una cuenta ficticia única y verifica alta/edición/cancelación de
suscripciones, datos del recordatorio, presupuesto, deduplicación de gastos,
renovación de sesión y logout. No utiliza cuentas personales.

Los tests de Compose se ejecutan en Robolectric. No sustituyen la comprobación
visual y de permisos de calendario/notificaciones en un teléfono o emulador.

### Resultado del corte de integración

El APK de depuración compiló y las nueve pruebas finalizaron sin fallos, errores
ni casos omitidos, incluyendo la prueba contra el backend real. Android Lint
finalizó sin errores. Se corrigieron diez atributos XML de íconos que estaban
fuera de su etiqueta vector.

Ver [resultados y SHA-256 del APK](integration-test-results.json) y
[resumen de la ejecución de Gradle](integration-build.txt).
