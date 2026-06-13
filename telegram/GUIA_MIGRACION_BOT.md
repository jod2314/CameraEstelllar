# Guía Técnica: Cómo Migrar o Integrar un Bot de Telegram Diferente

Esta guía te indica paso a paso cómo crear un nuevo bot de Telegram, obtener tus credenciales autorizadas y actualizar el archivo de estado de este proyecto para realizar la integración.

---

## 1. Crear un Nuevo Bot en Telegram

Para crear tu propio bot, utilizaremos el bot oficial de Telegram `@BotFather`:

1.  Abre la aplicación de Telegram y busca al usuario **`@BotFather`** (asegúrate de que tenga el check azul de verificación).
2.  Inicia la conversación presionando el botón **`/start`** o enviando el comando `/start`.
3.  Crea el bot enviando el comando:
    ```
    /newbot
    ```
4.  `BotFather` te pedirá un **nombre** para tu bot (ej: `Mi Stellar Control Bot`).
5.  Luego te pedirá un **nombre de usuario (username)** único. Este debe terminar obligatoriamente en `bot` o `_bot` (ej: `stellar_control_bot`).
6.  Una vez creado, `BotFather` te responderá con un mensaje de felicitación que contiene el **API Token** del bot.
    *   *Ejemplo de Token:* `8701361971:AAEpCaY3eQZA1gOqGIXYzBEyYeh8MWWSIu4`

---

## 2. Obtener tu Chat ID de Usuario

Para garantizar la seguridad de tu sistema local, el bot solo debe responder a tus comandos. Para ello necesitamos tu identificador numérico de chat (`ChatId`):

1.  Busca en Telegram al bot **`@userinfobot`** o **`@RawDataBot`** y presiona `/start`.
2.  El bot te responderá de inmediato con tu información de usuario.
3.  Copia el número que aparece en el campo **`Id`** (es una cadena numérica, ej: `1421409751`).

---

## 3. Registrar el Bot en el Proyecto

Una vez que tengas el **Token** y tu **ChatId**, debes actualizar la configuración local:

1.  Abre el archivo de configuración del proyecto localizado en:
    `[Workspace]/telegram/telegram_state.json`
2.  Reemplaza los campos con tus nuevas credenciales:
    ```json
    {
      "Token": "TU_NUEVO_TOKEN_AQUI",
      "ChatId": "TU_CHAT_ID_AQUI",
      "LastUpdateId": 0
    }
    ```
    *(Nota: Deja el `LastUpdateId` en `0` para que el nuevo bot empiece a leer mensajes desde el inicio de su cola).*

---

## 4. Inicializar y Probar la Comunicación

1.  **Iniciar conversación con tu bot:** Busca el nombre de usuario de tu nuevo bot en Telegram (el que creaste en el paso 1.5) y envíale un mensaje inicial presionando **`/start`**. *(Este paso es OBLIGATORIO para permitir que el bot pueda enviarte mensajes de vuelta).*
2.  **Enviar una prueba de texto:**
    Ejecuta el script de PowerShell desde la terminal para verificar que el envío funcione:
    ```powershell
    powershell -ExecutionPolicy Bypass -File ./telegram/send_reply.ps1 -Text "Prueba exitosa con el nuevo Bot"
    ```
3.  **Enviar una prueba de archivo:**
    Prueba adjuntar un documento (reemplazando por una ruta de archivo válida en tu máquina):
    ```powershell
    powershell -ExecutionPolicy Bypass -File ./telegram/send_files.ps1 -FilePath "./telegram/telegram_state.json" -Caption "Respaldando configuración"
    ```
