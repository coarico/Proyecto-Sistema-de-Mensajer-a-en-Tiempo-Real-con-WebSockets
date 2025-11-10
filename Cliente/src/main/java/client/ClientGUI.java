package client;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;

public class ClientGUI extends JFrame {
    private JPanel messagePanel;
    private JScrollPane chatScroll;
    private JTextArea messageArea;
    private JButton sendButton;
    private ChatClient client;
    private String username;
    private DefaultListModel<String> userListModel;
    private JList<String> userList;

    // Colores y paleta
    private static final Color BG_WINDOW = new Color(0xEDEFF1);
    private static final Color BG_ME = new Color(0xDCF8C6); // verde claro
    private static final Color BG_OTHER = Color.WHITE;
    private static final Color SYSTEM_GRAY = new Color(0xEEEEEE);

    public ClientGUI() {
        username = JOptionPane.showInputDialog("Ingrese su nombre de usuario:");
        if (username == null || username.trim().isEmpty()) {
            username = "Usuario" + System.currentTimeMillis();
        }

        // Intentaremos conectar automáticamente al servidor en la LAN mediante discovery UDP.
        // Si no se encuentra servidor, se conectará a localhost por defecto.

        setTitle("Chat - " + username);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(720, 520);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_WINDOW);

        // (Top bar with change-name removed — username is set at startup and sent to server)

    // Panel donde se añadirán las burbujas de mensaje
        messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setBackground(BG_WINDOW);
    // reducir márgenes del panel de mensajes
    messagePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        chatScroll = new JScrollPane(messagePanel);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        add(chatScroll, BorderLayout.CENTER);

        // Panel lateral para lista de usuarios
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFixedCellWidth(160);
        userList.setBackground(Color.WHITE);
        JScrollPane usersScroll = new JScrollPane(userList);
        usersScroll.setPreferredSize(new Dimension(180, 0));
        usersScroll.setBorder(BorderFactory.createTitledBorder("Usuarios"));
        add(usersScroll, BorderLayout.EAST);

        // Panel inferior con área de texto y botón enviar
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        bottomPanel.setBackground(BG_WINDOW);
        messageArea = new JTextArea(3, 40);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        JScrollPane messageScroll = new JScrollPane(messageArea);
        sendButton = new JButton("Enviar");
        sendButton.setBackground(new Color(0x34B7F1));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);

        bottomPanel.add(messageScroll, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // Auto-connect: ChatClient will use discovery, an environment variable (CHAT_SERVER_URI) or
        // the fallback hardcoded server IP (10.40.48.104). No user prompt required.
        client = new ChatClient(this, username);

        // Enter = enviar, Shift+Enter = nueva línea
        InputMap im = messageArea.getInputMap();
        ActionMap am = messageArea.getActionMap();
        im.put(KeyStroke.getKeyStroke("ENTER"), "send");
        am.put("send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
        im.put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
        am.put("insert-break", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                messageArea.append("\n");
            }
        });

        sendButton.addActionListener(e -> sendMessage());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.disconnect();
            }
        });
    }

    private void sendMessage() {
        String message = messageArea.getText().trim();
        if (!message.isEmpty()) {
            client.sendMessage(message);
            messageArea.setText("");
        }
    }

    // Añade una burbuja de mensaje al panel
    public void appendMessage(String user, String time, String content) {
        SwingUtilities.invokeLater(() -> {
            boolean isMine = user != null && user.equals(username);
            // Agrupación simple: si el último mensaje fue del mismo usuario, no mostrar nombre de nuevo
            boolean showName = true;
            if (messagePanel.getComponentCount() > 0) {
                Component last = messagePanel.getComponent(messagePanel.getComponentCount() - 1);
                if (last instanceof JComponent) {
                    Object lastSender = ((JComponent) last).getClientProperty("sender");
                    if (lastSender instanceof String && ((String) lastSender).equals(user)) {
                        showName = false;
                    }
                }
            }

            JPanel bubble = new RoundedPanel(isMine ? BG_ME : BG_OTHER, 12);
            bubble.setLayout(new BorderLayout(6, 0));
            // smaller inner padding to make bubbles compact
            bubble.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

            // Texto y nombre (solo para otros y si corresponde)
            if (!isMine && showName) {
                JLabel nameLabel = new JLabel(user);
                nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
                bubble.add(nameLabel, BorderLayout.NORTH);
            }

            JTextArea contentArea = new JTextArea(content);
            contentArea.setLineWrap(true);
            contentArea.setWrapStyleWord(true);
            contentArea.setEditable(false);
            contentArea.setOpaque(false);
            bubble.add(contentArea, BorderLayout.CENTER);

            JLabel timeLabel = new JLabel(time);
            timeLabel.setFont(timeLabel.getFont().deriveFont(10f));
            timeLabel.setForeground(Color.DARK_GRAY);
            bubble.add(timeLabel, BorderLayout.SOUTH);

            // Avatar
            JLabel avatar = new JLabel(createAvatarIcon(user));

            // Usar FlowLayout para un alineado más natural
            // Use minimal hgap/vgap to reduce vertical spacing between messages
            JPanel container = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 6, 0));
            container.setOpaque(false);
            if (isMine) {
                container.add(bubble);
                container.add(avatar);
            } else {
                container.add(avatar);
                container.add(bubble);
            }
            // marcar el panel con el sender para agrupación
            container.putClientProperty("sender", user);
            // permitir que el contenedor ocupe todo el ancho disponible y evitar espacios extras
            container.setMaximumSize(new Dimension(Integer.MAX_VALUE, container.getPreferredSize().height));
            container.setAlignmentX(Component.LEFT_ALIGNMENT);

            messagePanel.add(container);
            messagePanel.revalidate();
            JScrollBar v = chatScroll.getVerticalScrollBar();
            v.setValue(v.getMaximum());
        });
    }

    // Mensaje del sistema (centrado y gris)
    public void appendSystemMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            row.setOpaque(false);
            JLabel lbl = new JLabel(message);
            lbl.setFont(lbl.getFont().deriveFont(Font.ITALIC, 12f));
            lbl.setOpaque(true);
            lbl.setBackground(SYSTEM_GRAY);
            lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            row.add(lbl);
            messagePanel.add(row);
            messagePanel.revalidate();
            JScrollBar v = chatScroll.getVerticalScrollBar();
            v.setValue(v.getMaximum());
        });
    }

    // Actualizar lista de usuarios conectados
    public void updateUserList(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            if (users != null) {
                for (String u : users) {
                    if (u != null && !u.trim().isEmpty()) {
                        userListModel.addElement(u.trim());
                    }
                }
            }
        });
    }

    // Genera un icono circular con iniciales
    private Icon createAvatarIcon(String user) {
        int size = 36;
        String initials = "?";
        if (user != null && !user.trim().isEmpty()) {
            String[] parts = user.trim().split("\\s+");
            if (parts.length == 1) initials = parts[0].substring(0, 1).toUpperCase();
            else initials = (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x90CAF9));
        g.fillOval(0, 0, size, size);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(initials);
        int h = fm.getAscent();
        g.drawString(initials, (size - w) / 2, (size + h) / 2 - 3);
        g.dispose();
        return new ImageIcon(img);
    }

    // Panel con fondo redondeado
    private static class RoundedPanel extends JPanel {
        private final Color color;
        private final int radius;

        public RoundedPanel(Color color, int radius) {
            this.color = color;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public boolean isOpaque() {
            return false;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientGUI().setVisible(true);
        });
    }
}
