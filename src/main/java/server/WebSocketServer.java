package server;

import org.glassfish.tyrus.server.Server;

public class WebSocketServer {
    public static void main(String[] args) {
        Server server = new Server("localhost", 8025, "/websocket", null, ChatEndpoint.class);

        try {
            server.start();
            System.out.println("Servidor WebSocket iniciado en ws://localhost:8025/websocket/chat");
            System.out.println("Presiona ENTER para detener el servidor...");
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }
}