# Maps2Waze

Manda un enlace de Google Maps / Waze (o una dirección en texto) desde el iPhone y se abre solo en la radio Android del auto.

```
iPhone (Atajo en "Compartir")  →  ntfy.sh/<tu-canal>  →  app Android en la radio  →  abre Google Maps / Waze
```

No hace falta servidor propio ni cuenta: se usa [ntfy.sh](https://ntfy.sh) (gratis) como mensajero.
La radio necesita internet (datos propios o el hotspot del iPhone).

## 1. Radio Android

1. Descarga el APK: pestaña **Actions** de este repo → último build verde → artefacto **Maps2Waze-apk** (descomprime el zip).
   O compílalo: `cd android && gradle assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
2. Pásalo a la radio (USB) e instálalo (permite "orígenes desconocidos").
3. Abre **Maps2Waze**:
   - Anota el **canal** que aparece (p. ej. `auto-3f9a1c...`). Es la "contraseña": quien lo sepa puede mandar enlaces a tu radio.
   - Toca **Permitir mostrar sobre otras apps** y actívalo (sin esto Android 10+ no deja abrir Maps/Waze desde segundo plano).
   - Toca **Guardar e iniciar**.
4. Si la radio tiene "optimización de batería" o "apps en segundo plano", quita a Maps2Waze de esa lista.

## 2. iPhone (Atajo, sin instalar nada)

En la app **Atajos** → **+** nuevo atajo, nombre "Mandar al auto":

1. Ajustes del atajo (ⓘ) → activa **Mostrar en hoja de compartir**; tipos: **URL** y **Texto**.
2. Agrega la acción **Obtener contenido de URL** (*Get Contents of URL*):
   - URL: `https://ntfy.sh` (sin el canal)
   - Método: **POST**
   - Cuerpo de la solicitud: **JSON**, con dos campos de tipo **Texto**:
     - `topic` → tu canal (p. ej. `auto-3f9a1c...`)
     - `message` → **Entrada del atajo**
   - No uses cuerpo **Archivo**: el iPhone manda el enlace vacío y la radio solo recibe "triggered".
3. (Opcional) Agrega **Mostrar notificación** "Enviado al auto".

## Uso

En WhatsApp (o Google Maps, Waze, Safari…): **Compartir** → **Mandar al auto**. En segundos se abre en la radio.

- Enlaces de Google Maps (`maps.app.goo.gl`, `google.com/maps`…) se convierten y se abren en **Waze**. Si no se puede leer el destino (p. ej. sin internet), la radio muestra un aviso.
- Texto sin enlace (p. ej. "Av. Reforma 222, CDMX") se busca en Waze.
- Cualquier otro enlace se ignora, para que nadie pueda abrir sitios raros en tu auto.
- Si mandas algo con la radio apagada, se abre al encenderla si fue hace menos de 10 minutos.

Prueba rápida desde una computadora: `curl -d "https://waze.com/ul?q=Zocalo" https://ntfy.sh/TU-CANAL`
