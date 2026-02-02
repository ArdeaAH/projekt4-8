package schooldb_java;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.io.File;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class App {

    // ====== CONFIG ======
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/school_db?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    // UI style
    private static final Color ROYAL_BLUE = new Color(65, 105, 225);
    private static final Font MAIN_FONT = new Font("Times New Roman", Font.PLAIN, 16);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                ensureTables();
                ensureDefaultAdmin(); // admin / admin123
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "DB init error: " + e.getMessage());
                e.printStackTrace();
            }
            new LoginFrame().setVisible(true);
        });
    }

    // ========= DB helpers =========
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private static void ensureTables() throws Exception {
        String users = """
                CREATE TABLE IF NOT EXISTS users (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  username VARCHAR(50) NOT NULL UNIQUE,
                  password_hash CHAR(64) NOT NULL,
                  role ENUM('ADMIN','STAFF') NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        String students = """
                CREATE TABLE IF NOT EXISTS students (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  emer VARCHAR(60) NOT NULL,
                  atesia VARCHAR(60) NULL,
                  mbiemer VARCHAR(60) NOT NULL,
                  klasa VARCHAR(30) NOT NULL,
                  mesuesi_kujdestar VARCHAR(80) NOT NULL,
                  foto LONGBLOB NULL,
                  foto_mime VARCHAR(50) NULL,
                  foto_filename VARCHAR(255) NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (Connection c = getConnection();
             Statement st = c.createStatement()) {
            st.execute(users);
            st.execute(students);
        }
    }

    private static void ensureDefaultAdmin() throws Exception {
        String check = "SELECT COUNT(*) FROM users WHERE role='ADMIN'";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(check);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            if (rs.getInt(1) == 0) {
                String ins = "INSERT INTO users(username,password_hash,role) VALUES(?,?, 'ADMIN')";
                try (PreparedStatement insPs = c.prepareStatement(ins)) {
                    insPs.setString(1, "admin");
                    insPs.setString(2, Security.sha256("admin123"));
                    insPs.executeUpdate();
                }
            }
        }
    }

    // ========= Security =========
    static class Security {
        public static String sha256(String text) throws Exception {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        }
    }

    // ========= Models =========
    static class User {
        int id;
        String username;
        String role;
        User(int id, String username, String role) {
            this.id = id; this.username = username; this.role = role;
        }
    }

    static class Student {
        int id;
        String emer, atesia, mbiemer, klasa, mesuesikujdestar;
        byte[] fotoBytes;
        String fotoMime, fotoFilename;
    }

    // ========= DAO =========
    static class UserDAO {
        static User login(String username, String passwordPlain, String role) throws Exception {
            String sql = """
                    SELECT id, username, role
                    FROM users
                    WHERE username=? AND password_hash=? AND role=?
                    """;
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, Security.sha256(passwordPlain));
                ps.setString(3, role);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return new User(rs.getInt("id"), rs.getString("username"), rs.getString("role"));
                return null;
            }
        }

        static void addStaff(String username, String passwordPlain) throws Exception {
            String sql = "INSERT INTO users(username, password_hash, role) VALUES(?,?, 'STAFF')";
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, Security.sha256(passwordPlain));
                ps.executeUpdate();
            }
        }
    }

    static class StudentDAO {
        static List<Student> getAll() throws Exception {
            List<Student> list = new ArrayList<>();
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM students ORDER BY id DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        }

        static Student getById(int id) throws Exception {
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM students WHERE id=?")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return map(rs);
                return null;
            }
        }

        static int insert(Student s) throws Exception {
            String sql = """
                    INSERT INTO students(emer, atesia, mbiemer, klasa, mesuesi_kujdestar, foto, foto_mime, foto_filename)
                    VALUES(?,?,?,?,?,?,?,?)
                    """;
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, s.emer);
                if (s.atesia == null || s.atesia.isBlank()) ps.setNull(2, Types.VARCHAR);
                else ps.setString(2, s.atesia);

                ps.setString(3, s.mbiemer);
                ps.setString(4, s.klasa);
                ps.setString(5, s.mesuesikujdestar);

                if (s.fotoBytes != null) ps.setBytes(6, s.fotoBytes);
                else ps.setNull(6, Types.BLOB);

                if (s.fotoMime != null) ps.setString(7, s.fotoMime);
                else ps.setNull(7, Types.VARCHAR);

                if (s.fotoFilename != null) ps.setString(8, s.fotoFilename);
                else ps.setNull(8, Types.VARCHAR);

                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) return keys.getInt(1);
                return -1;
            }
        }

        static void update(Student s) throws Exception {
            String sql = """
                    UPDATE students
                    SET emer=?, atesia=?, mbiemer=?, klasa=?, mesuesi_kujdestar=?, foto=?, foto_mime=?, foto_filename=?
                    WHERE id=?
                    """;
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {

                ps.setString(1, s.emer);
                if (s.atesia == null || s.atesia.isBlank()) ps.setNull(2, Types.VARCHAR);
                else ps.setString(2, s.atesia);

                ps.setString(3, s.mbiemer);
                ps.setString(4, s.klasa);
                ps.setString(5, s.mesuesikujdestar);

                if (s.fotoBytes != null) ps.setBytes(6, s.fotoBytes);
                else ps.setNull(6, Types.BLOB);

                if (s.fotoMime != null) ps.setString(7, s.fotoMime);
                else ps.setNull(7, Types.VARCHAR);

                if (s.fotoFilename != null) ps.setString(8, s.fotoFilename);
                else ps.setNull(8, Types.VARCHAR);

                ps.setInt(9, s.id);

                ps.executeUpdate();
            }
        }

        static void delete(int id) throws Exception {
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM students WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        }

        private static Student map(ResultSet rs) throws Exception {
            Student s = new Student();
            s.id = rs.getInt("id");
            s.emer = rs.getString("emer");
            s.atesia = rs.getString("atesia");
            s.mbiemer = rs.getString("mbiemer");
            s.klasa = rs.getString("klasa");
            s.mesuesikujdestar = rs.getString("mesuesi_kujdestar");
            s.fotoBytes = rs.getBytes("foto");
            s.fotoMime = rs.getString("foto_mime");
            s.fotoFilename = rs.getString("foto_filename");
            return s;
        }
    }

    // ========= UI =========

    static class LoginFrame extends JFrame {
        private JTextField tfUser = new JTextField();
        private JPasswordField tfPass = new JPasswordField();
        private JComboBox<String> cbRole = new JComboBox<>(new String[]{"ADMIN", "STAFF"});

        LoginFrame() {
            setTitle("Login");
            setSize(420, 220);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);

            JPanel p = new JPanel(new GridLayout(4, 2, 10, 10));
            p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

            p.add(new JLabel("Username:")); p.add(tfUser);
            p.add(new JLabel("Password:")); p.add(tfPass);
            p.add(new JLabel("Role:")); p.add(cbRole);

            JButton btn = new JButton("Login");
            p.add(new JLabel()); p.add(btn);
            setContentPane(p);

            btn.addActionListener(e -> doLogin());
        }

        private void doLogin() {
            try {
                String u = tfUser.getText().trim();
                String p = new String(tfPass.getPassword());
                String role = (String) cbRole.getSelectedItem();

                if (u.isEmpty() || p.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Plotëso username + password");
                    return;
                }

                User user = UserDAO.login(u, p, role);
                if (user == null) {
                    JOptionPane.showMessageDialog(this, "Login i pasaktë");
                    return;
                }

                if ("ADMIN".equals(user.role)) new AdminDashboard(user).setVisible(true);
                else new StaffDashboard(user).setVisible(true);

                dispose();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gabim: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    static class AdminDashboard extends JFrame {
        private final User user;

        AdminDashboard(User user) {
            this.user = user;

            setTitle("ADMIN Dashboard - " + user.username);
            setSize(420, 220);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);

            JButton btnAddStaff = new JButton("Shto Personel");
            JButton btnView = new JButton("Shiko të dhëna");
            JButton btnEdit = new JButton("Edito të dhëna");

            JPanel p = new JPanel(new GridLayout(3, 1, 10, 10));
            p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            p.add(btnAddStaff);
            p.add(btnView);
            p.add(btnEdit);

            setContentPane(p);

            btnAddStaff.addActionListener(e -> addStaff());
            btnView.addActionListener(e -> new StudentListFrame("ADMIN").setVisible(true));
            btnEdit.addActionListener(e -> new StudentFormFrame(null).setVisible(true));
        }

        private void addStaff() {
            JTextField u = new JTextField();
            JPasswordField p = new JPasswordField();
            Object[] msg = {"Username:", u, "Password:", p};

            int ok = JOptionPane.showConfirmDialog(this, msg, "Shto Personel", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;

            try {
                String user = u.getText().trim();
                String pass = new String(p.getPassword());
                if (user.isEmpty() || pass.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Plotëso të dyja fushat");
                    return;
                }
                UserDAO.addStaff(user, pass);
                JOptionPane.showMessageDialog(this, "Personeli u shtua!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gabim: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    static class StaffDashboard extends JFrame {
        private final User user;

        StaffDashboard(User user) {
            this.user = user;

            setTitle("STAFF Dashboard - " + user.username);
            setSize(420, 200);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);

            JButton btnView = new JButton("Shiko");
            JButton btnEdit = new JButton("Edito");

            JPanel p = new JPanel(new GridLayout(2, 1, 10, 10));
            p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            p.add(btnView);
            p.add(btnEdit);

            setContentPane(p);

            btnView.addActionListener(e -> new StudentListFrame("STAFF").setVisible(true));
            btnEdit.addActionListener(e -> new StudentFormFrame(null).setVisible(true));
        }
    }

    static class StudentListFrame extends JFrame {
        private DefaultTableModel model = new DefaultTableModel(
                new Object[]{"ID", "Emër", "Atësia", "Mbiemër", "Klasa", "Mesuesi"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        private JTable table = new JTable(model);

        StudentListFrame(String role) {
            setTitle("Mirë se erdhe - Lista e Nxënësve (" + role + ")");
            setSize(900, 420);
            setLocationRelativeTo(null);

            JButton btnRefresh = new JButton("Refresh");
            JButton btnView = new JButton("Shiko");
            JButton btnEdit = new JButton("Ndrysho");
            JButton btnDelete = new JButton("Fshi");

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(btnRefresh);
            top.add(btnView);
            top.add(btnEdit);
            top.add(btnDelete);

            add(top, BorderLayout.NORTH);
            add(new JScrollPane(table), BorderLayout.CENTER);

            btnRefresh.addActionListener(e -> loadData());
            btnView.addActionListener(e -> viewSelected());
            btnEdit.addActionListener(e -> editSelected());
            btnDelete.addActionListener(e -> deleteSelected());

            loadData();
        }

        private void loadData() {
            try {
                model.setRowCount(0);
                for (Student s : StudentDAO.getAll()) {
                    model.addRow(new Object[]{s.id, s.emer, s.atesia, s.mbiemer, s.klasa, s.mesuesikujdestar});
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gabim: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        private Integer selectedId() {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Zgjidh një rresht");
                return null;
            }
            return (Integer) model.getValueAt(row, 0);
        }

        private void viewSelected() {
            try {
                Integer id = selectedId();
                if (id == null) return;
                Student s = StudentDAO.getById(id);
                if (s == null) return;

                JOptionPane.showMessageDialog(this,
                        "ID: " + s.id +
                                "\nEmër: " + s.emer +
                                "\nAtësia: " + s.atesia +
                                "\nMbiemër: " + s.mbiemer +
                                "\nKlasa: " + s.klasa +
                                "\nMesuesi: " + s.mesuesikujdestar,
                        "Detaje", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gabim: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        private void editSelected() {
            try {
                Integer id = selectedId();
                if (id == null) return;
                Student s = StudentDAO.getById(id);
                if (s == null) return;
                new StudentFormFrame(s).setVisible(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gabim: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        private void deleteSelected() {
            try {
                Integer id = selectedId();
                if (id == null) return;
                int ok = JOptionPane.showConfirmDialog(this, "Je i sigurt?", "Delete", JOptionPane.YES_NO_OPTION);
                if (ok != JOptionPane.YES_OPTION) return;

                StudentDAO.delete(id);
                loadData();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gabim: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    static class StudentFormFrame extends JFrame {
        private Student editing;

        private JTextField tfEmer = new JTextField();
        private JTextField tfAtesia = new JTextField();
        private JTextField tfMbiemer = new JTextField();
        private JTextField tfKlasa = new JTextField();
        private JTextField tfMesuesi = new JTextField();

        private JLabel lblImagePreview = new JLabel("Pa foto", SwingConstants.CENTER);

        private byte[] photoBytes = null;
        private String photoMime = null;
        private String photoFilename = null;

        StudentFormFrame(Student editing) {
            this.editing = editing;

            setTitle(editing == null ? "Shto Nxënës" : "Ndrysho Nxënës (ID " + editing.id + ")");
            setSize(760, 460);
            setLocationRelativeTo(null);

            JPanel root = new JPanel(new BorderLayout(12, 12));
            root.setBorder(new EmptyBorder(12, 12, 12, 12));
            root.setBackground(ROYAL_BLUE);
            setContentPane(root);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBackground(ROYAL_BLUE);

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 6, 6, 6);
            c.fill = GridBagConstraints.HORIZONTAL;

            addRow(form, c, 0, "Emër:", tfEmer);
            addRow(form, c, 1, "Atësia:", tfAtesia);
            addRow(form, c, 2, "Mbiemër:", tfMbiemer);
            addRow(form, c, 3, "Klasa:", tfKlasa);
            addRow(form, c, 4, "Mesuesi Kujdestar:", tfMesuesi);

            root.add(form, BorderLayout.CENTER);

            JPanel imagePanel = new JPanel(new BorderLayout(8, 8));
            imagePanel.setPreferredSize(new Dimension(260, 0));
            imagePanel.setBackground(ROYAL_BLUE);

            lblImagePreview.setBorder(BorderFactory.createLineBorder(Color.WHITE));
            lblImagePreview.setPreferredSize(new Dimension(240, 240));
            lblImagePreview.setOpaque(true);
            lblImagePreview.setBackground(Color.WHITE);
            imagePanel.add(lblImagePreview, BorderLayout.CENTER);

            JButton btnChoose = new JButton("Zgjidh Foto");
            JButton btnSave = new JButton("Save");
            JPanel btns = new JPanel(new GridLayout(2, 1, 8, 8));
            btns.setBackground(ROYAL_BLUE);
            btns.add(btnChoose);
            btns.add(btnSave);

            imagePanel.add(btns, BorderLayout.SOUTH);
            root.add(imagePanel, BorderLayout.EAST);

            btnChoose.addActionListener(e -> choosePhoto());
            btnSave.addActionListener(e -> save());

            if (editing != null) {
                tfEmer.setText(editing.emer);
                tfAtesia.setText(editing.atesia == null ? "" : editing.atesia);
                tfMbiemer.setText(editing.mbiemer);
                tfKlasa.setText(editing.klasa);
                tfMesuesi.setText(editing.mesuesikujdestar);

                photoBytes = editing.fotoBytes;
                photoMime = editing.fotoMime;
                photoFilename = editing.fotoFilename;

                if (photoBytes != null) {
                    lblImagePreview.setText("Foto e ruajtur");
                }
            }
        }

        private void addRow(JPanel panel, GridBagConstraints c, int row, String label, JTextField field) {
            JLabel l = new JLabel(label);
            l.setFont(MAIN_FONT);
            l.setForeground(Color.WHITE);

            field.setFont(MAIN_FONT);

            c.gridy = row;
            c.gridx = 0; c.weightx = 0;
            panel.add(l, c);

            c.gridx = 1; c.weightx = 1;
            field.setColumns(20);
            panel.add(field, c);
        }

        private void choosePhoto() {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Zgjidh një foto (JPG/PNG)");
            chooser.setFileFilter(new FileNameExtensionFilter("Images (JPG, PNG)", "jpg", "jpeg", "png"));

            int res = chooser.showOpenDialog(this);
            if (res != JFileChooser.APPROVE_OPTION) return;

            File file = chooser.getSelectedFile();
            try {
                BufferedImage original = ImageIO.read(file);
                if (original == null) throw new Exception("File nuk është imazh valid.");

                BufferedImage resized = new BufferedImage(240, 240, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = resized.createGraphics();
                g.drawImage(original, 0, 0, 240, 240, null);
                g.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(resized, "jpg", baos);
                photoBytes = baos.toByteArray();
                photoFilename = file.getName();
                photoMime = "image/jpeg";

                lblImagePreview.setText("");
                lblImagePreview.setIcon(new ImageIcon(resized));

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gabim foto: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        private void save() {
            try {
                String emer = tfEmer.getText().trim();
                String atesia = tfAtesia.getText().trim();
                String mbiemer = tfMbiemer.getText().trim();
                String klasa = tfKlasa.getText().trim();
                String mesuesi = tfMesuesi.getText().trim();

                if (emer.isEmpty() || mbiemer.isEmpty() || klasa.isEmpty() || mesuesi.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Mbush (Emër, Mbiemër, Klasa, Mesuesi)");
                    return;
                }

                Student s = (editing == null) ? new Student() : editing;

                s.emer = emer;
                s.atesia = atesia.isBlank() ? null : atesia;
                s.mbiemer = mbiemer;
                s.klasa = klasa;
                s.mesuesikujdestar = mesuesi;

                s.fotoBytes = photoBytes;
                s.fotoMime = photoMime;
                s.fotoFilename = photoFilename;

                if (editing == null) {
                    int id = StudentDAO.insert(s);
                    JOptionPane.showMessageDialog(this, "U ruajt! ID=" + id);
                } else {
                    StudentDAO.update(s);
                    JOptionPane.showMessageDialog(this, "U ndryshua me sukses!");
                }

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gabim ruajtje: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }
}




## Java Code
