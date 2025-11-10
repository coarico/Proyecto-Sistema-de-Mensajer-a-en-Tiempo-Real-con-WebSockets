# Sistema de Mensajería en Tiempo Real con WebSockets

Proyecto: Sistema de Mensajería en Tiempo Real con WebSockets

NRC: 30066 - APLICACIONES DISTRIBUIDAS

Integrantes:
- Cesar Arico
- Alan Herrera
- Fernando Suquillo

---

Descripción general
-------------------
Este repositorio implementa un chat distribuido basado en el protocolo WebSocket. Está compuesto por:

- Un servidor WebSocket (Tyrus) que gestiona conexiones, validación básica, difusión de mensajes y mantenimiento de una lista de usuarios.
- Un cliente de escritorio (Java Swing) que se conecta al servidor, envía/recibe mensajes y presenta una interfaz de usuario simple.

Tecnologías
-----------
- Java 17 (verificado en `pom.xml`).
- Tyrus (implementación de WebSocket).
- Gson para serialización JSON.

Estructura relevante
--------------------

- `src/main/java/server/`:
  - `WebSocketServer.java` — Inicializa el servidor Tyrus en `localhost:8025` con el contexto `/websocket`.
  - `ChatEndpoint.java` — Endpoint `@ServerEndpoint("/chat")` que maneja conexiones, mensajes y difusión.
  - `Message.java` — Objeto de transferencia con campos: `usuario`, `contenido`, `hora`, `tipo`.

- `src/main/java/client/`:
  - `ClientGUI.java` — Interfaz Swing para enviar/recibir mensajes y mostrar usuarios.
  - `ChatClient.java` — `@ClientEndpoint` que se conecta a `ws://localhost:8025/websocket/chat` y maneja la capa cliente.

Cumplimiento de requisitos (análisis técnico)
---------------------------------------------
A continuación se mapea cada requisito funcional y no funcional con la implementación encontrada en el código y se indica si se cumple, se cumple parcialmente o no se cumple, con evidencias y recomendaciones.

Requerimientos funcionales

1. El sistema debe permitir conexiones simultáneas de múltiples clientes.
   - Evidencia: `ChatEndpoint` usa `CopyOnWriteArraySet<Session>` para `sessions` y `ConcurrentHashMap` para `usuariosConectados`, estructuras seguras para concurrencia.
   - Evaluación: Cumple. El servidor admite múltiples sesiones simultáneas.

2. Cada cliente podrá enviar mensajes al servidor, que los reenviará a todos los usuarios conectados.
   - Evidencia: `ChatClient.sendMessage()` envía un `Message` serializado; `ChatEndpoint.onMessage()` valida y llama a `broadcastMessage()` que recorre `sessions` y envía el JSON.
   - Evaluación: Cumple.

3. El servidor debe notificar las conexiones y desconexiones de los usuarios.
   - Evidencia: Al recibir `SET_NAME` el servidor hace `broadcastMessage(...)` con tipo `CONEXION` y también envía lista de usuarios en mensajes `INFO`. En `onClose` envía `DESCONEXION`.
   - Evaluación: Cumple.

4. Los mensajes deben incluir información básica: nombre del usuario, hora y contenido.
   - Evidencia: Clase `Message` tiene `usuario`, `contenido` y `hora` (se asigna LocalDateTime.now() en el constructor). El cliente muestra la hora formateada.
   - Evaluación: Cumple.

5. El sistema debe permitir limpiar o cerrar sesión desde el cliente.
   - Evidencia: `ClientGUI` llama `client.disconnect()` en `windowClosing`, y `ChatClient.disconnect()` cierra la sesión WebSocket.
   - Evaluación: Cumple (cierre), aunque no existe una acción de "cerrar sesión" que notifique explícitamente al servidor con un comando tipo LOGOUT; el cierre de sesión se maneja por cierre de la conexión.

Requerimientos no funcionales

1. Lenguaje: Java 17.
   - Evidencia: `pom.xml` define `<maven.compiler.source>17</maven.compiler.source>` y `<maven.compiler.target>17`.
   - Evaluación: Cumple.

2. Protocolo de comunicación: WebSocket.
   - Evidencia: Uso de Jakarta WebSocket (Tyrus) y endpoints `@ServerEndpoint` / `@ClientEndpoint`.
   - Evaluación: Cumple.

3. Escalabilidad: permitir que el servidor maneje múltiples conexiones concurrentes.
   - Evidencia: Estructuras concurrentes y uso de Tyrus que maneja múltiples conexiones. `broadcastMessage` itera de forma síncrona sobre `sessions` enviando con `session.getBasicRemote().sendText(...)`.
   - Evaluación: Parcial. Soporta múltiples conexiones, pero el broadcast síncrono puede bloquear si una sesión es lenta. Para mayor escalabilidad se recomienda enviar mensajes de forma asíncrona o usar un pool de hilos para enviar, y considerar balanceo de carga / clustering.

4. Usabilidad: interfaz simple e intuitiva para el usuario.
   - Evidencia: `ClientGUI` proporciona una interfaz con área de mensajes, botón enviar y lista de usuarios; usa burbujas y avatares simples.
   - Evaluación: Cumple (enfocado a escritorio). Si se requiere interfaz web, actualmente no existe.

5. Eficiencia: los mensajes deben transmitirse con latencia mínima de 1 seg.
   - Evidencia: `broadcastMessage()` mide tiempo desde inicio del método y emite una advertencia si supera 1000 ms. No hay pruebas automatizadas ni medidas por mensaje individual con timestamps de origen.
   - Evaluación: Parcial. El código intenta detectar latencias >1s, pero no garantiza latencia <1s en condiciones reales. Para certificar latencia se necesitan pruebas de carga y métricas (benchmarks, monitorización), medición por mensaje con timestamps del cliente-servidor y optimizaciones de I/O.

6. Seguridad: manejo adecuado de errores y validación de mensajes.
   - Evidencia: `onMessage` valida mensajes nulos/ vacíos, límites de longitud (500 chars), y responde con mensajes tipo `ERROR`. `onError` intenta cerrar sesión y registrar.
   - Evaluación: Parcial. Existe validación básica y logging. Falta:
     - Autenticación/autorización (no hay login seguro).
     - Cifrado (no se usa TLS / wss por defecto).
     - Protección contra ataques de denegación de servicio, inyección o flooding (no hay rate limiting ni sandboxing).

Resumen de cumplimiento
----------------------
- Funcionales: la mayoría de los requisitos funcionales están implementados (conexiones múltiples, difusión de mensajes, notificaciones de join/leave, campos de mensaje, cierre de sesión por desconexión).
- No funcionales: Java 17 y WebSocket están correctamente usados; concurrencia básica está considerada. La escalabilidad y seguridad son parciales y se recomendaron mejoras para producción.

Gaps y recomendaciones (priorizadas)
------------------------------------
1. Mejorar el broadcast para no bloquear: usar `session.getAsyncRemote().sendText(...)` o un ExecutorService para enviar en paralelo.
2. Añadir TLS (wss) para cifrar tráfico en despliegues reales.
3. Implementar autenticación (ej. token JWT) o un simple mecanismo de login para identificar a usuarios de forma segura.
4. Añadir medidas anti-flood/rate-limiting por sesión y validación adicional del contenido (sanitize) para prevenir abusos.
5. Añadir pruebas de carga (stress tests) para medir latencia en escenarios de N usuarios y ajustar la arquitectura (p. ej. clustering, Redis pub/sub para múltiples instancias).
6. Añadir endpoint/acción de "logout" para que el cliente pueda desconectarse limpiamente y notificar al servidor sin cerrar la ventana.
7. Añadir métricas y monitorización (Prometheus, logs estructurados) y pruebas automáticas unitarias/integración.

Evidencias técnicas (líneas clave)
---------------------------------
- Puerto y contexto: `WebSocketServer.java` crea `new Server("localhost", 8025, "/websocket", null, ChatEndpoint.class);`.
- Endpoint chat: `@ServerEndpoint(value = "/chat")` (archivo `ChatEndpoint.java`).
- Mensaje: `Message` con `usuario`, `contenido`, `hora` y `tipo`.
- Concurrencia: `CopyOnWriteArraySet<Session>` y `ConcurrentHashMap<Session,String>`.

Cómo compilar y ejecutar (Windows PowerShell)
-------------------------------------------

1. Empaqueta el proyecto con Maven (desde la raíz del repositorio):

```powershell
mvn clean package
```

2. Ejecutar el servidor (arranca en `localhost:8025` y monta el contexto `/websocket`):

```powershell
java -cp target/classes server.WebSocketServer
```

3. Ejecutar el cliente (puedes lanzar varias instancias para simular varios usuarios):

```powershell
java -cp target/classes client.ClientGUI
```

Conclusión
----------
El proyecto implementa correctamente las funcionalidades esenciales de un chat en tiempo real usando WebSockets y Java 17. Cumple los requisitos funcionales principales. Para cumplir completamente los requerimientos no funcionales en un entorno de producción (latencia garantizada, escalabilidad a gran escala y seguridad fuerte), se recomiendan las mejoras listadas arriba.

¿Quieres que implemente alguna de las recomendaciones (por ejemplo, cambiar a `getAsyncRemote()` para el broadcast, añadir un comando `LOGOUT`, o preparar un pequeño script PowerShell para ejecutar servidor/cliente)?
# Sistema de Mensajería en Tiempo Real con WebSockets

Proyecto: Sistema de Mensajería en Tiempo Real con WebSockets

NRC: 30066 - APLICACIONES DISTRIBUIDAS

Integrantes:
- Cesar Arico
- Alan Herrera
- Fernando Suquillo

---

Descripción general
-------------------
Este proyecto implementa un sistema de chat en tiempo real usando WebSockets. Consta de dos partes principales:

- Servidor: expone un endpoint WebSocket que gestiona conexiones, difusión de mensajes y la lista de usuarios conectados.
- Cliente: aplicación de escritorio (GUI Swing) que se conecta al servidor WebSocket, envía y recibe mensajes, y muestra la lista de usuarios.

Estructura relevante
--------------------

- `src/main/java/server/`:
  - `WebSocketServer.java` — Arranca un servidor WebSocket (Tyrus) en `ws://localhost:8025/websocket` y registra el endpoint `ChatEndpoint`.
  - `ChatEndpoint.java` — Gestión de conexiones, recepción/broadcast de mensajes y mantenimiento de la lista de usuarios.
  - `Message.java` — Modelo de datos usado para serializar/deserializar mensajes entre cliente y servidor.

- `src/main/java/client/`:
  - `ClientGUI.java` — Interfaz gráfica Swing del cliente. Permite escribir mensajes, ver la conversación y la lista de usuarios.
  - `ChatClient.java` — Cliente WebSocket que se conecta a `ws://localhost:8025/websocket/chat`, envía/recibe mensajes y actualiza la GUI.

Qué hace el servidor
---------------------

El servidor arranca un contenedor WebSocket (Tyrus) y expone un endpoint en `ws://localhost:8025/websocket/chat`.

Responsabilidades principales:

- Aceptar conexiones WebSocket de clientes.
- Registrar el nombre de usuario enviado por cada cliente (comando `SET_NAME`).
- Mantener y distribuir la lista de usuarios conectados.
- Recibir mensajes tipo `MENSAJE` y retransmitirlos a todos los clientes conectados.
- Enviar mensajes de sistema/información cuando un usuario se conecta o desconecta.

Qué hace el cliente
--------------------

El cliente es una aplicación Swing con la siguiente funcionalidad:

- Al iniciarse pide un nombre de usuario y conecta al servidor WebSocket en `ws://localhost:8025/websocket/chat`.
- Permite enviar mensajes de texto; los mensajes se serializan como objetos `Message` y se envían al servidor.
- Muestra mensajes entrantes en un panel con burbujas (mensajes propios alineados a la derecha).
- Muestra mensajes de sistema (conexiones, desconexiones, errores) y actualiza la lista de usuarios conectados.

Cómo compilar y ejecutar (Windows PowerShell)
-------------------------------------------

1. Empaqueta el proyecto con Maven (desde la raíz del repositorio):

```powershell
mvn clean package
```

2. Ejecutar el servidor (arranca en `localhost:8025` y monta el contexto `/websocket`):

```powershell
java -cp target/classes server.WebSocketServer
```

3. Ejecutar el cliente (puedes lanzar varias instancias para simular varios usuarios):

```powershell
java -cp target/classes client.ClientGUI
```

Notas y recomendaciones
-----------------------

- El servidor usa el puerto 8025 y monta el contexto `/websocket`; el endpoint de chat es `/chat`, por lo que la URL completa es:
  `ws://localhost:8025/websocket/chat`.
- El proyecto requiere las dependencias declaradas en `pom.xml` (Tyrus, Jakarta WebSocket, Gson, etc.). Usar `mvn package` para descargar dependencias y compilar.
- Si prefieres ejecutar desde IDE (IntelliJ/NetBeans/Eclipse), importa el proyecto como un proyecto Maven y ejecuta las clases `server.WebSocketServer` y `client.ClientGUI`.
- Si deseas empaquetar en un JAR ejecutable, añade el plugin correspondiente en `pom.xml` o usa `maven-assembly-plugin` / `maven-shade-plugin`.

Contacto / Créditos
-------------------
Integrantes:

- Cesar Arico
- Alan Herrera
- Fernando Suquillo

Proyecto: Sistema de Mensajería en Tiempo Real con WebSockets
NRC: 30066 - APLICACIONES DISTRIBUIDAS
