package server;

import java.time.LocalDateTime;

public class Message {
    private String usuario;
    private String contenido;
    private LocalDateTime hora;
    private String tipo; // "MENSAJE", "CONEXION", "DESCONEXION"

    public Message(String usuario, String contenido, String tipo) {
        this.usuario = usuario;
        this.contenido = contenido;
        this.hora = LocalDateTime.now();
        this.tipo = tipo;
    }

    // Getters y setters
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public LocalDateTime getHora() { return hora; }
    public void setHora(LocalDateTime hora) { this.hora = hora; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
}