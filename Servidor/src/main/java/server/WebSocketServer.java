package server;

import org.glassfish.tyrus.server.Server;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebSocketServer {
    public static void main(String[] args) {
        // Permitir host y puerto configurables vía argumentos.
        // Uso: java -cp target/classes server.WebSocketServer [host] [port]
        // Si host no se indica, por defecto bindea en 0.0.0.0 (todas las interfaces) para aceptar conexiones desde la LAN.
        String host = "0.0.0.0";
        int port = 8025;
        String context = "/websocket";
        if (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            host = args[0];
        }
        if (args != null && args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Puerto inválido en argumento, usando 8025");
            }
        }

        Server server = new Server(host, port, context, null, ChatEndpoint.class);

    // Iniciar un responder UDP simple para descubrimiento en LAN (puerto 9090)
    ExecutorService exec = Executors.newSingleThreadExecutor();
    final int discoveryPort = 9090;
    final int portForDiscovery = port; // capturar en una variable final para usar dentro de la lambda
    exec.submit(() -> {
            try (DatagramSocket ds = new DatagramSocket(discoveryPort)) {
                System.out.println("Discovery responder escuchando en UDP/" + discoveryPort);
                byte[] buf = new byte[256];
                while (!Thread.currentThread().isInterrupted()) {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    ds.receive(pkt);
                    String received = new String(pkt.getData(), 0, pkt.getLength()).trim();
                    if ("DISCOVER_CHAT_SERVER".equalsIgnoreCase(received)) {
                        String localIp = InetAddress.getLocalHost().getHostAddress();
                        String response = String.format("CHAT_SERVER:%s:%d", localIp, portForDiscovery);
                        byte[] respBytes = response.getBytes();
                        DatagramPacket resp = new DatagramPacket(respBytes, respBytes.length, pkt.getAddress(), pkt.getPort());
                        ds.send(resp);
                    }
                }
            } catch (Exception e) {
                System.out.println("Discovery responder detenido: " + e.getMessage());
            }
        });

        try {
            server.start();
            System.out.println("Servidor WebSocket iniciado (bind: " + host + ")");
            String displayIp = "localhost";
            try {
                displayIp = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                // fallback a localhost
            }
            System.out.println("Endpoint disponible en: ws://" + displayIp + ":" + port + context + "/chat");
            System.out.println("Si bindeas a 0.0.0.0, usa la IP de la máquina en la LAN para conectar clientes.");
            System.out.println("Presiona ENTER para detener el servidor...");
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }
}