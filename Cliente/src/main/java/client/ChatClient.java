package client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonElement; // <-- Añadir esta línea
import server.Message;

import jakarta.websocket.*;
import org.glassfish.tyrus.client.ClientManager;
import java.net.URI;
import java.time.LocalDateTime; // <-- Nueva importación
import java.time.format.DateTimeFormatter; // <-- Nueva importación
import java.util.Arrays;
import java.util.List;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

@ClientEndpoint
public class ChatClient {
    private Session session;
    private ClientGUI gui;
    private String username;
    private long connectedAt = 0;
    private String serverUri;
    // Modificamos la inicialización de Gson
    private static Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    /**
     * @param serverUri URI completa del servidor WebSocket (ej. ws://192.168.1.10:8025/websocket/chat)
     */
    public ChatClient(ClientGUI gui, String username, String serverUri) {
        this.gui = gui;
        this.username = username;
        this.serverUri = (serverUri == null || serverUri.trim().isEmpty()) ? "ws://localhost:8025/websocket/chat" : serverUri.trim();
        connect();
    }

    /**
     * Constructor que intenta descubrir el servidor automáticamente en la LAN.
     */
    public ChatClient(ClientGUI gui, String username) {
        this.gui = gui;
        this.username = username;
        this.serverUri = discoverServerUri();
        connect();
    }

    // Intenta descubrir el servidor mediante broadcast UDP en el puerto 9090
    private String discoverServerUri() {
        int discoveryPort = 9090;
        // Allow override via environment variable or system property
        String envUri = System.getenv("CHAT_SERVER_URI");
        if (envUri == null || envUri.trim().isEmpty()) {
            envUri = System.getProperty("chat.server.uri");
        }
        String defaultUri = (envUri != null && !envUri.trim().isEmpty()) ? envUri.trim() : "ws://10.40.48.104:8025/websocket/chat";
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(2));
            socket.setBroadcast(true);
            byte[] sendData = "DISCOVER_CHAT_SERVER".getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), discoveryPort);
            socket.send(sendPacket);

            // Esperar respuesta
            byte[] recvBuf = new byte[256];
            DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
            socket.receive(receivePacket);
            String message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
            if (message.startsWith("CHAT_SERVER:")) {
                String parts = message.substring("CHAT_SERVER:".length());
                String[] p = parts.split(":");
                if (p.length >= 2) {
                    String host = p[0];
                    String port = p[1];
                    return String.format("ws://%s:%s/websocket/chat", host, port);
                }
            }
        } catch (Exception e) {
            // Silenciar: fallback a localhost
        }
        return defaultUri;
    }

    private void connect() {
        try {
            WebSocketContainer container = null;
            try {
                container = ContainerProvider.getWebSocketContainer();
            } catch (Throwable ignored) {
                // ContainerProvider may fail to find a provider when running from a shaded jar.
            }
            if (container == null) {
                // Fallback: create a Tyrus ClientManager directly so we don't rely on ServiceLoader
                container = ClientManager.createClient();
            }
            container.connectToServer(this, new URI(serverUri));
        } catch (Exception e) {
            String now = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            // Show the full exception class and message in the GUI and print stacktrace to console for debugging
            gui.appendSystemMessage(String.format("[%s] Error al conectar: %s: %s", now, e.getClass().getName(), e.getMessage()));
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement el : e.getStackTrace()) {
                sb.append(el.toString()).append("\n");
            }
            // Optionally append the first part of the stacktrace to the GUI to aid debugging
            gui.appendSystemMessage(sb.toString().split("\\n")[0]);
            e.printStackTrace();
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        String now = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        gui.appendSystemMessage(String.format("[%s] Conectado al servidor de chat", now));
        // Enviar el nombre elegido al servidor para que lo registre y lo use en la lista de usuarios
        try {
            Message setName = new Message(username, "", "SET_NAME");
            session.getBasicRemote().sendText(gson.toJson(setName));
        } catch (Exception e) {
            gui.appendSystemMessage("Error al enviar nombre al servidor: " + e.getMessage());
        }
        // registrar el timestamp de conexión para filtrar mensajes temporales del servidor
        connectedAt = System.currentTimeMillis();
    }

    @OnMessage
    public void onMessage(String mensaje) {
        try {
            Message mensajeObj = gson.fromJson(mensaje, Message.class);
            String tipo = mensajeObj.getTipo();
            String time = "";
            if (mensajeObj.getHora() != null) {
                time = mensajeObj.getHora().format(DateTimeFormatter.ofPattern("HH:mm"));
            }

            // Filtrar mensajes temporales que el servidor pueda haber enviado antes de recibir SET_NAME
            // (p. ej. "Usuario1"). Si el mensaje llega muy pronto después de conectarse y contiene
            // nombres temporales del tipo Usuario<digitos>, lo ignoramos.
            boolean isTempName = false;
            if (mensajeObj.getUsuario() != null && mensajeObj.getUsuario().matches("Usuario\\d+")) {
                isTempName = true;
            }
            if (mensajeObj.getContenido() != null && mensajeObj.getContenido().matches(".*Usuario\\d+.*")) {
                isTempName = true;
            }

            if (("INFO".equalsIgnoreCase(tipo) || "CONEXION".equalsIgnoreCase(tipo) || "DESCONEXION".equalsIgnoreCase(tipo))
                    && isTempName && (System.currentTimeMillis() - connectedAt) < 2000) {
                // ignoramos mensaje temporal
                return;
            }

            if ("INFO".equalsIgnoreCase(tipo)) {
                // El servidor envía: "Usuarios conectados: user1, user2"
                String contenido = mensajeObj.getContenido();
                if (contenido != null && contenido.contains(":")) {
                    String after = contenido.substring(contenido.indexOf(":") + 1).trim();
                    List<String> users = Arrays.asList(after.split("\\s*,\\s*"));
                    gui.updateUserList(users);
                    gui.appendSystemMessage(String.format("[%s] %s", time, mensajeObj.getContenido()));
                } else {
                    gui.appendSystemMessage(String.format("[%s] %s", time, mensajeObj.getContenido()));
                }
            } else if ("CONEXION".equalsIgnoreCase(tipo) || "DESCONEXION".equalsIgnoreCase(tipo)) {
                gui.appendSystemMessage(String.format("[%s] %s %s", time, mensajeObj.getUsuario(), mensajeObj.getContenido()));
            } else if ("ERROR".equalsIgnoreCase(tipo)) {
                gui.appendSystemMessage(String.format("[%s] ERROR: %s", time, mensajeObj.getContenido()));
            } else {
                // MENSAJE u otros
                gui.appendMessage(mensajeObj.getUsuario(), time, mensajeObj.getContenido());
            }
        } catch (Exception e) {
            gui.appendSystemMessage("Error al procesar mensaje entrante: " + e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        String now = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        gui.appendSystemMessage(String.format("[%s] Desconectado del servidor", now));
    }

    public void sendMessage(String content) {
        try {
            // Validación de contenido
            if (content == null || content.trim().isEmpty()) {
                String now = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                gui.appendSystemMessage(String.format("[%s] Error: Mensaje vacío", now));
                return;
            }
            // Limitar longitud del mensaje
            if (content.length() > 500) {
                content = content.substring(0, 500);
                String now = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                gui.appendSystemMessage(String.format("[%s] Advertencia: Mensaje truncado a 500 caracteres", now));
            }
            
            Message message = new Message(username, content, "MENSAJE");
            session.getBasicRemote().sendText(gson.toJson(message));
        } catch (Exception e) {
            String now = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            gui.appendSystemMessage(String.format("[%s] Error al enviar mensaje: %s", now, e.getMessage()));
        }
    }

    public void disconnect() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Clase interna para manejar la serialización/deserialización de LocalDateTime
    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime localDateTime, java.lang.reflect.Type type, com.google.gson.JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(formatter.format(localDateTime));
        }

        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement jsonElement, java.lang.reflect.Type type, com.google.gson.JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return LocalDateTime.parse(jsonElement.getAsString(), formatter);
        }
    }
}
