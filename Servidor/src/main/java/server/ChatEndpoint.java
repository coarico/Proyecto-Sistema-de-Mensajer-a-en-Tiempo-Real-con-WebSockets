package server;

import jakarta.websocket.*; // Cambiado de javax.websocket.*
import jakarta.websocket.server.ServerEndpoint; // Cambiado de javax.websocket.server.ServerEndpoint
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonElement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import jakarta.annotation.PreDestroy; // Cambiado de javax.annotation.PreDestroy

@ServerEndpoint(value = "/chat")
public class ChatEndpoint {
    private static Set<Session> sessions = new CopyOnWriteArraySet<>();
    private static Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    private String usuario;

    private static Map<Session, String> usuariosConectados = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        // Añadimos la sesión a la lista, pero NO asignamos ni difundimos un nombre temporal.
        // El nombre real se registrará cuando el cliente envíe un mensaje tipo SET_NAME.
        sessions.add(session);
        logEvent("OPEN", "Sesión abierta: " + session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        String usuarioDesconectado = usuariosConectados.remove(session);
        broadcastMessage(new Message(usuarioDesconectado, "se ha desconectado", "DESCONEXION"));
        logEvent("CLOSE", "Usuario desconectado: " + usuarioDesconectado);
    }

    @OnMessage
    public void onMessage(String mensaje, Session session) {
        try {
            // Validación básica del mensaje
            if (mensaje == null || mensaje.trim().isEmpty()) {
                session.getBasicRemote().sendText(gson.toJson(
                    new Message("Sistema", "Mensaje inválido", "ERROR")));
                return;
            }

            Message mensajeObj = gson.fromJson(mensaje, Message.class);
            // Manejar petición para establecer nombre de usuario
            if (mensajeObj != null && "SET_NAME".equalsIgnoreCase(mensajeObj.getTipo())) {
                String nuevo = mensajeObj.getUsuario();
                if (nuevo == null || nuevo.trim().isEmpty()) {
                    session.getBasicRemote().sendText(gson.toJson(
                        new Message("Sistema", "Nombre inválido", "ERROR")));
                    return;
                }
                String anterior = usuariosConectados.get(session);
                usuariosConectados.put(session, nuevo);
                // Log en servidor
                logEvent("CONEXION", "Usuario conectado: " + nuevo + " (sesión " + session.getId() + ")");
                // Notificar a todos la lista actualizada y la conexión
                Message listaUsuarios = new Message("Sistema",
                    "Usuarios conectados: " + String.join(", ", usuariosConectados.values()),
                    "INFO");
                broadcastMessage(listaUsuarios);
                broadcastMessage(new Message(nuevo, "se ha conectado", "CONEXION"));
                // Si el nombre anterior existía, era distinto y NO era un nombre temporal (Usuario\d+), notificar cambio de nombre
                if (anterior != null && !anterior.equals(nuevo) && !anterior.matches("Usuario\\d+")) {
                    broadcastMessage(new Message("Sistema", anterior + " ahora es " + nuevo, "INFO"));
                }
                return;
            }
            
            // Validación del objeto mensaje
            if (mensajeObj.getUsuario() == null || mensajeObj.getContenido() == null) {
                session.getBasicRemote().sendText(gson.toJson(
                    new Message("Sistema", "Formato de mensaje inválido", "ERROR")));
                return;
            }

            // Limitar longitud del mensaje
            if (mensajeObj.getContenido().length() > 500) {
                mensajeObj.setContenido(mensajeObj.getContenido().substring(0, 500));
            }

            broadcastMessage(mensajeObj);
        } catch (Exception e) {
            try {
                session.getBasicRemote().sendText(gson.toJson(
                    new Message("Sistema", "Error en el procesamiento del mensaje", "ERROR")));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static final Logger logger = Logger.getLogger(ChatEndpoint.class.getName());

    private void logEvent(String evento, String detalles) {
        logger.log(Level.INFO, String.format("[%s] %s: %s", 
            LocalDateTime.now(), evento, detalles));
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        try {
            logEvent("ERROR", "Sesión: " + session.getId() + " - " + throwable.getMessage());
            session.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error al cerrar sesión", e);
        }
    }

    private void broadcastMessage(Message mensaje) {
        String mensajeJson = gson.toJson(mensaje);
        long startTime = System.currentTimeMillis();
        
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText(mensajeJson);
                
                // Verificar latencia
                long latency = System.currentTimeMillis() - startTime;
                if (latency > 1000) { // más de 1 segundo
                    System.out.println("Advertencia: Alta latencia detectada: " + latency + "ms");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @PreDestroy
        public void cleanup() {
            for (Session session : sessions) {
                try {
                    session.close();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Error al cerrar sesión durante la limpieza", e);
                }
            }
            sessions.clear();
            usuariosConectados.clear();
        }

    // Clase interna para manejar la serialización/deserialización de LocalDateTime
    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime localDateTime, java.lang.reflect.Type type, com.google.gson.JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(formatter.format(localDateTime));
        }

        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement jsonElement, java.lang.reflect.Type type, com.google.gson.JsonDeserializationContext jsonDeserializationContext) throws com.google.gson.JsonParseException {
            return LocalDateTime.parse(jsonElement.getAsString(), formatter);
        }
    }
}