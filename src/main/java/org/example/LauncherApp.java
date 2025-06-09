package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

public class LauncherApp {
    private static final String[] SUPPORTED_VERSIONS = {"1.21.5", "1.21.4", "1.21.3"};
    private static final Map<String, String> MC_URLS = new HashMap<>();
    static {
        MC_URLS.put("1.21.5", "https://piston-data.mojang.com/v1/objects/b88808bbb3da8d9f453694b5d8f74a3396f1a533/client.jar");
        MC_URLS.put("1.21.4", "https://piston-data.mojang.com/v1/objects/a7e5a6024bfd3cd614625aa05629adf760020304/client.jar");
        MC_URLS.put("1.21.3", "https://piston-data.mojang.com/v1/objects/6f67d19b4467240639cb2c368ffd4b94ba889705/client.jar");
    }
    private String selectedVersion = SUPPORTED_VERSIONS[0];
    private File getJarFile() {
        return new File("minecraft-" + selectedVersion + "-client.jar");
    }

    private static final String MC_VERSION_MANIFEST_INDEX = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final File LIBS_DIR = new File("libraries");

    private static final String FABRIC_INSTALLER_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar";
    private static final File FABRIC_DIR = new File("fabric");
    private static final File FABRIC_INSTALLER_FILE = new File(FABRIC_DIR, "fabric-installer-1.0.3.jar");

    private JFrame frame;
    private JButton button;
    private JProgressBar progress;
    private JLabel status;
    private JButton openFolderBtn;
    private JTextArea logArea;
    private JScrollPane logScroll;
    private JComboBox<String> versionBox;
    private JCheckBox fabricCheckBox;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LauncherApp().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Minecraft Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        versionBox = new JComboBox<>(SUPPORTED_VERSIONS);
        versionBox.setSelectedIndex(0);
        versionBox.addActionListener(e -> {
            selectedVersion = (String) versionBox.getSelectedItem();
            openFolderBtn.setEnabled(getJarFile().exists());
            button.setText("Завантажити та запустити Minecraft " + selectedVersion);
            frame.setTitle("Minecraft Launcher " + selectedVersion);
            fabricCheckBox.setVisible("1.21.5".equals(selectedVersion));
        });

        button = new JButton("Завантажити та запустити Minecraft " + selectedVersion);
        progress = new JProgressBar(0, 100);
        progress.setStringPainted(true);
        status = new JLabel("Готово");
        openFolderBtn = new JButton("Відкрити папку з jar");
        openFolderBtn.setEnabled(getJarFile().exists());
        openFolderBtn.addActionListener(e -> openJarFolder());

        logArea = new JTextArea(8, 40);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logScroll = new JScrollPane(logArea);

        fabricCheckBox = new JCheckBox("Завантажити Fabric (1.21.5)");
        fabricCheckBox.setVisible("1.21.5".equals(selectedVersion));

        button.addActionListener(this::onLaunchClicked);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(Box.createVerticalStrut(10));
        panel.add(new JLabel("Оберіть версію Minecraft:"));
        panel.add(versionBox);
        panel.add(Box.createVerticalStrut(10));
        panel.add(fabricCheckBox);
        panel.add(Box.createVerticalStrut(10));
        panel.add(button);
        panel.add(Box.createVerticalStrut(10));
        panel.add(progress);
        panel.add(Box.createVerticalStrut(10));
        panel.add(status);
        panel.add(Box.createVerticalStrut(10));
        panel.add(openFolderBtn);

        frame.add(panel, BorderLayout.NORTH);
        frame.add(logScroll, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private void onLaunchClicked(ActionEvent e) {
        button.setEnabled(false);
        openFolderBtn.setEnabled(false);
        status.setText("Перевірка Java...");
        progress.setValue(0);
        logArea.setText("");

        new Thread(() -> {
            try {
                JavaInfo javaInfo = findJava();
                if (!javaInfo.found) {
                    SwingUtilities.invokeLater(() -> {
                        status.setText("Java 21+ не знайдено! Встановіть Java 21 та задайте JAVA_HOME.");
                        button.setEnabled(true);
                    });
                    return;
                }
                SwingUtilities.invokeLater(() -> logArea.append("Java знайдено: " + javaInfo.path + "\nВерсія: " + javaInfo.versionLine + "\n\n"));

                File jarFile = getJarFile();
                boolean useFabric = "1.21.5".equals(selectedVersion) && fabricCheckBox.isSelected();

                if (useFabric) {
                    statusSet("Завантаження Fabric...");
                    if (!FABRIC_DIR.exists() && !FABRIC_DIR.mkdirs()) {
                        SwingUtilities.invokeLater(() -> logArea.append("Не вдалося створити папку Fabric!\n"));
                        button.setEnabled(true);
                        return;
                    }
                    if (!FABRIC_INSTALLER_FILE.exists()) {
                        downloadFile(FABRIC_INSTALLER_URL, FABRIC_INSTALLER_FILE);
                    }
                    SwingUtilities.invokeLater(() -> logArea.append("Fabric installer завантажено: " + FABRIC_INSTALLER_FILE.getAbsolutePath() + "\n"));

                    statusSet("Інсталяція Fabric...");
                    boolean fabricInstalled = installFabricInBackground(javaInfo.path);
                    if (!fabricInstalled) {
                        SwingUtilities.invokeLater(() -> {
                            status.setText("Помилка інсталяції Fabric!");
                            logArea.append("Fabric не встановлено!\n");
                        });
                        button.setEnabled(true);
                        return;
                    }
                    SwingUtilities.invokeLater(() -> logArea.append("Fabric встановлено!\n"));
                }

                if (!jarFile.exists()) {
                    statusSet("Завантаження Minecraft...");
                    String mcUrl = MC_URLS.get(selectedVersion);
                    downloadFile(mcUrl, jarFile);
                    SwingUtilities.invokeLater(() -> openFolderBtn.setEnabled(true));
                } else {
                    SwingUtilities.invokeLater(() -> openFolderBtn.setEnabled(true));
                }

                statusSet("Запуск Minecraft...");
                boolean started;
                if (useFabric) {
                    started = runFabric(javaInfo.path, selectedVersion);
                } else {
                    Map<String, Object> index = downloadJsonMap(MC_VERSION_MANIFEST_INDEX);
                    String manifestUrl = findManifestUrl(index, selectedVersion);
                    if (manifestUrl == null) {
                        throw new IOException("Не знайдено manifest для версії " + selectedVersion);
                    }
                    Map<String, Object> manifest = downloadJsonMap(manifestUrl);
                    java.util.List<File> libs = downloadLibraries(manifest);
                    started = runMinecraft(javaInfo.path, libs, jarFile, selectedVersion);
                }
                SwingUtilities.invokeLater(() -> {
                    if (started) {
                        status.setText("Minecraft запущено!");
                    } else {
                        status.setText("Не вдалося запустити Minecraft!");
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    status.setText("Помилка: " + ex.getMessage());
                    logArea.append("Помилка: " + ex + "\n");
                });
            } finally {
                SwingUtilities.invokeLater(() -> button.setEnabled(true));
            }
        }).start();
    }

    private void statusSet(String msg) {
        SwingUtilities.invokeLater(() -> status.setText(msg));
    }

    @SuppressWarnings("deprecation")
    private Map<String, Object> downloadJsonMap(String url) throws IOException {
        // Використання deprecated конструктора URL(String) для сумісності з поточним JDK
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (InputStream in = conn.getInputStream()) {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            String line;
            do {
                line = r.readLine();
                if (line != null) sb.append(line);
            } while (line != null);
            return parseJsonObject(sb.toString());
        }
    }

    private Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        int i = 0;
        while (i < json.length()) {
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) break;
            if (json.charAt(i) == ',') { i++; continue; }
            if (json.charAt(i) == '"') {
                int keyStart = ++i;
                while (i < json.length() && json.charAt(i) != '"') i++;
                String key = json.substring(keyStart, i);
                i++; // skip "
                while (i < json.length() && (json.charAt(i) == ':' || Character.isWhitespace(json.charAt(i)))) i++;
                if (i < json.length() && json.charAt(i) == '[') {
                    // array
                    int arrStart = i;
                    int depth = 0;
                    do {
                        if (json.charAt(i) == '[') depth++;
                        else if (json.charAt(i) == ']') depth--;
                        i++;
                    } while (i < json.length() && depth > 0);
                    String arrStr = json.substring(arrStart, i);
                    map.put(key, parseJsonArray(arrStr));
                } else if (i < json.length() && json.charAt(i) == '{') {
                    // object
                    int objStart = i;
                    int depth = 0;
                    do {
                        if (json.charAt(i) == '{') depth++;
                        else if (json.charAt(i) == '}') depth--;
                        i++;
                    } while (i < json.length() && depth > 0);
                    String objStr = json.substring(objStart, i);
                    map.put(key, parseJsonObject(objStr));
                } else {
                    // value
                    int valStart = i;
                    if (json.charAt(i) == '"') {
                        valStart++;
                        i++;
                        while (i < json.length() && json.charAt(i) != '"') i++;
                        String val = json.substring(valStart, i);
                        map.put(key, val);
                        i++;
                    } else {
                        while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                        String val = json.substring(valStart, i).trim();
                        map.put(key, val);
                    }
                }
            } else {
                i++;
            }
        }
        return map;
    }

    private java.util.List<Object> parseJsonArray(String json) {
        java.util.List<Object> list = new java.util.ArrayList<>();
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        int i = 0;
        while (i < json.length()) {
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) break;
            if (json.charAt(i) == ',') { i++; continue; }
            if (json.charAt(i) == '{') {
                int objStart = i;
                int depth = 0;
                do {
                    if (json.charAt(i) == '{') depth++;
                    else if (json.charAt(i) == '}') depth--;
                    i++;
                } while (i < json.length() && depth > 0);
                String objStr = json.substring(objStart, i);
                list.add(parseJsonObject(objStr));
            } else if (json.charAt(i) == '"') {
                int valStart = ++i;
                while (i < json.length() && json.charAt(i) != '"') i++;
                String val = json.substring(valStart, i);
                list.add(val);
                i++;
            } else {
                int valStart = i;
                while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != ']') i++;
                String val = json.substring(valStart, i).trim();
                if (!val.isEmpty()) list.add(val);
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<File> downloadLibraries(Map<String, Object> manifest) throws IOException {
        java.util.List<File> libs = new java.util.ArrayList<>();
        java.util.List<Object> libraries = (java.util.List<Object>) manifest.get("libraries");
        int total = libraries.size();
        for (int i = 0; i < total; i++) {
            Map<String, Object> lib = (Map<String, Object>) libraries.get(i);
            if (!lib.containsKey("downloads")) continue;
            Map<String, Object> downloads = (Map<String, Object>) lib.get("downloads");
            if (!downloads.containsKey("artifact")) continue;
            Map<String, Object> artifact = (Map<String, Object>) downloads.get("artifact");
            String path = (String) artifact.get("path");
            String url = (String) artifact.get("url");
            File out = new File(LIBS_DIR, path.replace("/", File.separator));
            if (!out.exists()) {
                File parent = out.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Не вдалося створити директорію для бібліотеки: " + parent);
                }
                statusSet("Завантаження бібліотеки " + (i+1) + "/" + total + ": " + out.getName());
                downloadFile(url, out);
            }
            libs.add(out);
        }
        return libs;
    }

    private static class JavaInfo {
        boolean found;
        String path;
        String versionLine;
    }

    private JavaInfo findJava() {
        JavaInfo info = new JavaInfo();
        String javaHome = System.getenv("JAVA_HOME");
        String[] candidates;
        if (javaHome != null && !javaHome.isEmpty()) {
            candidates = new String[] {
                javaHome + File.separator + "bin" + File.separator + "java",
                javaHome + File.separator + "bin" + File.separator + "java.exe"
            };
        } else {
            candidates = new String[] { "java" };
        }
        for (String javaPath : candidates) {
            try {
                Process process = new ProcessBuilder(javaPath, "-version")
                        .redirectErrorStream(true)
                        .start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                String versionLine = null;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase(Locale.ROOT).contains("version")) {
                        versionLine = line;
                        break;
                    }
                }
                process.waitFor();
                if (versionLine != null && isJava21OrHigher(versionLine)) {
                    info.found = true;
                    info.path = javaPath;
                    info.versionLine = versionLine;
                    return info;
                }
            } catch (Exception ignored) {}
        }
        info.found = false;
        return info;
    }

    private boolean isJava21OrHigher(String versionLine) {
        int idx = versionLine.indexOf("\"");
        if (idx < 0) return false;
        String ver = versionLine.substring(idx + 1);
        idx = ver.indexOf("\"");
        if (idx < 0) return false;
        ver = ver.substring(0, idx);
        try {
            String[] parts = ver.split("\\.");
            int major = Integer.parseInt(parts[0]);
            return major >= 21;
        } catch (Exception e) {
            return false;
        }
    }

    private void downloadFile(String url, File dest) throws IOException {
        URL u = new URL(url);
        long contentLength = u.openConnection().getContentLengthLong();
        try (InputStream in = u.openStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            long start = System.currentTimeMillis();
            DecimalFormat df = new DecimalFormat("#.##");
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                int percent = contentLength > 0 ? (int) (totalRead * 100 / contentLength) : 0;
                double mb = totalRead / 1024.0 / 1024.0;
                double totalMb = contentLength / 1024.0 / 1024.0;
                long elapsed = System.currentTimeMillis() - start;
                double speed = elapsed > 0 ? (totalRead / 1024.0 / (elapsed / 1000.0)) : 0;
                String msg = String.format(
                    "Завантажено: %s / %s МБ (%d%%) | %s КБ/с",
                    df.format(mb), df.format(totalMb), percent, df.format(speed)
                );
                SwingUtilities.invokeLater(() -> {
                    progress.setValue(percent);
                    status.setText(msg);
                });
            }
        }
        SwingUtilities.invokeLater(() -> {
            progress.setValue(100);
            status.setText("Завантаження завершено!");
            logArea.append("Файл збережено: " + dest.getAbsolutePath() + "\n");
        });
    }

    private void openJarFolder() {
        try {
            Desktop.getDesktop().open(getJarFile().getParentFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Не вдалося відкрити папку: " + ex.getMessage());
        }
    }

    private boolean runMinecraft(String javaPath, java.util.List<File> libs, File jarFile, String version) {
        try {
            copyIconIfMissing("icon_16x16.png");
            copyIconIfMissing("icon_32x32.png");

            StringBuilder cp = new StringBuilder();
            for (File lib : libs) {
                cp.append(lib.getAbsolutePath()).append(File.pathSeparator);
            }
            cp.append(jarFile.getAbsolutePath());

            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(javaPath);
            cmd.add("-cp");
            cmd.add(cp.toString());
            cmd.add("net.minecraft.client.main.Main");
            cmd.add("--username");
            cmd.add("Player");
            cmd.add("--accessToken");
            cmd.add("12345");
            cmd.add("--version");
            cmd.add(version);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(System.getProperty("user.dir")));
            Process proc = pb.start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    do {
                        line = reader.readLine();
                        if (line != null) {
                            String ln = line + "\n";
                            SwingUtilities.invokeLater(() -> logArea.append(ln));
                        }
                    } while (line != null);
                } catch (IOException ignored) {}
            }).start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                    String line;
                    do {
                        line = reader.readLine();
                        if (line != null) {
                            String ln = "[ERR] " + line + "\n";
                            SwingUtilities.invokeLater(() -> logArea.append(ln));
                        }
                    } while (line != null);
                } catch (IOException ignored) {}
            }).start();

            return true;
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> logArea.append("Помилка запуску: " + ex + "\n"));
            return false;
        }
    }

    private void collectJars(File dir, StringBuilder cp) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectJars(f, cp);
            } else if (f.getName().endsWith(".jar")) {
                cp.append(f.getAbsolutePath()).append(File.pathSeparator);
            }
        }
    }

    private void copyIconIfMissing(String iconName) {
        File iconsDir = new File("icons");
        if (!iconsDir.exists() && !iconsDir.mkdirs()) return;
        File iconFile = new File(iconsDir, iconName);
        if (!iconFile.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("icons/" + iconName)) {
                if (in != null) {
                    Files.copy(in, iconFile.toPath());
                }
            } catch (IOException ignored) {}
        }
    }

    private String findManifestUrl(Map<String, Object> index, String version) {
        Object versionsObj = index.get("versions");
        if (!(versionsObj instanceof java.util.List<?> versions)) return null;
        for (Object v : versions) {
            if (!(v instanceof Map<?, ?> ver)) continue;
            Object idObj = ver.get("id");
            if (idObj != null && idObj.toString().equals(version)) {
                Object urlObj = ver.get("url");
                if (urlObj != null) return urlObj.toString();
            }
        }
        return null;
    }

    private boolean installFabricInBackground(String javaPath) {
        try {
            // Fabric installer створює все у.minecraft (або вказаній папці)
            File mcDir = new File(System.getProperty("user.dir"), ".minecraft");
            if (!mcDir.exists() && !mcDir.mkdirs()) {
                SwingUtilities.invokeLater(() -> logArea.append("Не вдалося створити .minecraft!\n"));
                return false;
            }
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(javaPath);
            cmd.add("-jar");
            cmd.add(FABRIC_INSTALLER_FILE.getAbsolutePath());
            cmd.add("client");
            cmd.add("-dir");
            cmd.add(mcDir.getAbsolutePath());
            cmd.add("-mcversion");
            cmd.add("1.21.5");
            cmd.add("-noprofile"); // не створювати профіль у launcher_profiles.json

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(FABRIC_DIR);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            do {
                line = reader.readLine();
                if (line != null) {
                    String ln = "[Fabric] " + line + "\n";
                    SwingUtilities.invokeLater(() -> logArea.append(ln));
                }
            } while (line != null);
            int exitCode = proc.waitFor();

            return exitCode == 0;
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> logArea.append("Помилка інсталяції Fabric: " + ex + "\n"));
            return false;
        }
    }

    private boolean runFabric(String javaPath, String mcVersion) {
        try {
            copyIconIfMissing("icon_16x16.png");
            copyIconIfMissing("icon_32x32.png");

            File mcDir = new File(System.getProperty("user.dir"), ".minecraft");
            File libsDir = new File(mcDir, "libraries");
            // Знаходимо останню встановлену версію fabric-loader у libraries/net/fabricmc/fabric-loader/
            File fabricLoaderDir = new File(libsDir, "net/fabricmc/fabric-loader");
            if (!fabricLoaderDir.exists() || !fabricLoaderDir.isDirectory()) {
                SwingUtilities.invokeLater(() -> logArea.append("Fabric loader не знайдено у " + fabricLoaderDir.getAbsolutePath() + "\n"));
                return false;
            }
            File[] versions = fabricLoaderDir.listFiles(File::isDirectory);
            if (versions == null || versions.length == 0) {
                SwingUtilities.invokeLater(() -> logArea.append("Fabric loader версії не знайдено у " + fabricLoaderDir.getAbsolutePath() + "\n"));
                return false;
            }
            Arrays.sort(versions, Comparator.comparing(File::getName));
            File latestVersionDir = versions[versions.length - 1];
            File[] jars = latestVersionDir.listFiles((dir, name) -> name.endsWith(".jar"));
            File fabricJar = null;
            if (jars != null && jars.length > 0) {
                fabricJar = jars[0];
            }
            if (fabricJar == null || !fabricJar.exists()) {
                SwingUtilities.invokeLater(() -> logArea.append("Fabric loader jar не знайдено у: " + latestVersionDir.getAbsolutePath() + "\n"));
                return false;
            }

            // Додаємо всі бібліотеки з.minecraft/libraries/
            StringBuilder cp = new StringBuilder();
            collectJars(libsDir, cp);

            // Додаємо сам майнкрафт jar (client)
            File mcJar = getJarFile();
            if (!mcJar.exists()) {
                SwingUtilities.invokeLater(() -> logArea.append("Minecraft jar не знайдено: " + mcJar.getAbsolutePath() + "\n"));
                return false;
            }
            if (cp.length() > 0) cp.append(File.pathSeparator);
            cp.append(mcJar.getAbsolutePath());

            // Додаємо fabric loader jar
            cp.append(File.pathSeparator).append(fabricJar.getAbsolutePath());

            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(javaPath);
            cmd.add("-cp");
            cmd.add(cp.toString());
            cmd.add("net.fabricmc.loader.impl.launch.knot.KnotClient");
            cmd.add("--username");
            cmd.add("Player");
            cmd.add("--accessToken");
            cmd.add("12345");
            cmd.add("--version");
            cmd.add(mcVersion);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(mcDir);
            Process proc = pb.start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String ln = "[Fabric] " + line + "\n";
                        SwingUtilities.invokeLater(() -> logArea.append(ln));
                    }
                } catch (IOException ignored) {}
            }).start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String ln = "[Fabric-ERR] " + line + "\n";
                        SwingUtilities.invokeLater(() -> logArea.append(ln));
                    }
                } catch (IOException ignored) {}
            }).start();

            return true;
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> logArea.append("Помилка запуску Fabric: " + ex + "\n"));
            return false;
        }
    }
}