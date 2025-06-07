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

    private static final String FORGE_1_21_5_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/1.21.5-55.0.21/forge-1.21.5-55.0.21-installer.jar";
    private static final File FORGE_DIR = new File("forge");
    private static final File FORGE_1_21_5_FILE = new File(FORGE_DIR, "forge-1.21.5-55.0.21-installer.jar");

    private JFrame frame;
    private JButton button;
    private JProgressBar progress;
    private JLabel status;
    private JButton openFolderBtn;
    private JTextArea logArea;
    private JScrollPane logScroll;
    private JComboBox<String> versionBox;
    private JCheckBox forgeCheckBox;

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
            forgeCheckBox.setVisible("1.21.5".equals(selectedVersion));
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

        forgeCheckBox = new JCheckBox("Завантажити Forge (1.21.5)");
        forgeCheckBox.setVisible("1.21.5".equals(selectedVersion));

        button.addActionListener(this::onLaunchClicked);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(Box.createVerticalStrut(10));
        panel.add(new JLabel("Оберіть версію Minecraft:"));
        panel.add(versionBox);
        panel.add(Box.createVerticalStrut(10));
        panel.add(forgeCheckBox);
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
                boolean useForge = "1.21.5".equals(selectedVersion) && forgeCheckBox.isSelected();

                File forgeClientJar = new File(FORGE_DIR, "libraries/net/minecraftforge/forge/1.21.5-55.0.21/forge-1.21.5-55.0.21-client.jar");
                File forgeUniversalJar = new File(FORGE_DIR, "forge-1.21.5-55.0.21.jar");

                if (useForge) {
                    statusSet("Завантаження Forge...");
                    if (!FORGE_DIR.exists() && !FORGE_DIR.mkdirs()) {
                        SwingUtilities.invokeLater(() -> logArea.append("Не вдалося створити папку Forge!\n"));
                        button.setEnabled(true);
                        return;
                    }
                    if (!FORGE_1_21_5_FILE.exists()) {
                        downloadFile(FORGE_1_21_5_URL, FORGE_1_21_5_FILE);
                    }
                    SwingUtilities.invokeLater(() -> logArea.append("Forge завантажено: " + FORGE_1_21_5_FILE.getAbsolutePath() + "\n"));

                    statusSet("Інсталяція Forge...");
                    if (!forgeClientJar.exists() && !forgeUniversalJar.exists()) {
                        boolean forgeInstalled = installForgeInBackground(javaInfo.path);
                        if (!forgeInstalled || (!forgeClientJar.exists() && !forgeUniversalJar.exists())) {
                            SwingUtilities.invokeLater(() -> {
                                status.setText("Помилка інсталяції Forge!");
                                logArea.append("Forge не встановлено!\n");
                            });
                            button.setEnabled(true);
                            return;
                        }
                        SwingUtilities.invokeLater(() -> logArea.append("Forge встановлено!\n"));
                    } else {
                        SwingUtilities.invokeLater(() -> logArea.append("Forge вже встановлено!\n"));
                    }
                }

                statusSet("Завантаження списку версій...");
                Map<String, Object> index = downloadJsonMap(MC_VERSION_MANIFEST_INDEX);
                String manifestUrl = findManifestUrl(index, selectedVersion);
                if (manifestUrl == null) {
                    throw new IOException("Не знайдено manifest для версії " + selectedVersion);
                }

                statusSet("Завантаження маніфесту версії...");
                Map<String, Object> manifest = downloadJsonMap(manifestUrl);

                statusSet("Завантаження бібліотек...");
                java.util.List<File> libs = downloadLibraries(manifest);

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
                if (useForge && (forgeClientJar.exists() || forgeUniversalJar.exists())) {
                    started = runForge(javaInfo.path, forgeClientJar.exists() ? forgeClientJar : forgeUniversalJar, selectedVersion);
                } else {
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

    /**
     * Запускає Forge jar як клієнт Minecraft.
     * Для нових версій Forge (1.17+), використовується BootstrapLauncher.
     * Якщо не знайдено bootstraplauncher, лаунчер спробує автоматично докачати всі потрібні бібліотеки з офіційного Forge Maven.
     * Параметр libs не використовується, але залишений для сумісності.
     */
    private boolean runForge(String javaPath, File forgeJar, String version) {
        try {
            copyIconIfMissing("icon_16x16.png");
            copyIconIfMissing("icon_32x32.png");

            File forgeLibs = new File(FORGE_DIR, "libraries");
            java.util.List<File> allForgeLibs = new java.util.ArrayList<>();
            if (forgeLibs.exists()) {
                collectJars(forgeLibs, allForgeLibs);
            }

            File bootstrapLauncherJar = null;
            for (File lib : allForgeLibs) {
                String path = lib.getAbsolutePath().replace("\\", "/").toLowerCase();
                if (path.contains("/bootstraplauncher/") && lib.getName().endsWith(".jar")) {
                    bootstrapLauncherJar = lib;
                    break;
                }
            }

            if ((bootstrapLauncherJar == null || !bootstrapLauncherJar.exists()) && forgeJar.getName().contains("1.21.5")) {
                String[][] forgeLibsToDownload = {
                    {
                        "net/minecraftforge/bootstraplauncher/1.2.1/bootstraplauncher-1.2.1.jar",
                        "https://maven.minecraftforge.net/net/minecraftforge/bootstraplauncher/1.2.1/bootstraplauncher-1.2.1.jar"
                    },
                    {
                        "cpw/mods/modlauncher/10.0.8/modlauncher-10.0.8.jar",
                        "https://maven.minecraftforge.net/cpw/mods/modlauncher/10.0.8/modlauncher-10.0.8.jar"
                    },
                    {
                        "cpw/mods/grossjava9hacks/1.4.6/grossjava9hacks-1.4.6.jar",
                        "https://maven.minecraftforge.net/cpw/mods/grossjava9hacks/1.4.6/grossjava9hacks-1.4.6.jar"
                    },
                    {
                        "org/ow2/asm/asm/9.7/asm-9.7.jar",
                        "https://maven.minecraftforge.net/org/ow2/asm/asm/9.7/asm-9.7.jar"
                    },
                    {
                        "org/ow2/asm/asm-commons/9.7/asm-commons-9.7.jar",
                        "https://maven.minecraftforge.net/org/ow2/asm/asm-commons/9.7/asm-commons-9.7.jar"
                    },
                    {
                        "org/ow2/asm/asm-tree/9.7/asm-tree-9.7.jar",
                        "https://maven.minecraftforge.net/org/ow2/asm/asm-tree/9.7/asm-tree-9.7.jar"
                    }
                };
                for (String[] libInfo : forgeLibsToDownload) {
                    File libFile = new File(forgeLibs, libInfo[0].replace("/", File.separator));
                    if (!libFile.exists()) {
                        File parent = libFile.getParentFile();
                        if (!parent.exists() && !parent.mkdirs()) continue;
                        try {
                            downloadFile(libInfo[1], libFile);
                        } catch (Exception ex) {
                            SwingUtilities.invokeLater(() -> logArea.append("[Forge-ERR] Не вдалося докачати " + libInfo[0] + ": " + ex + "\n"));
                        }
                    }
                }
                allForgeLibs.clear();
                collectJars(forgeLibs, allForgeLibs);
                for (File lib : allForgeLibs) {
                    String path = lib.getAbsolutePath().replace("\\", "/").toLowerCase();
                    if (path.contains("/bootstraplauncher/") && lib.getName().endsWith(".jar")) {
                        bootstrapLauncherJar = lib;
                        break;
                    }
                }
            }

            if (bootstrapLauncherJar == null || !bootstrapLauncherJar.exists()) {
                SwingUtilities.invokeLater(() -> logArea.append("""
                    [Forge-ERR] Не знайдено bootstraplauncher jar у forge/libraries!
                    Ймовірно, Forge installer не зміг завантажити всі бібліотеки (наприклад, через відсутність інтернету або блокування).
                    Спробуйте:
                    1. Запустити Forge installer вручну: відкрийте консоль, перейдіть у папку forge та виконайте:
                       java -jar forge-1.21.5-55.0.21-installer.jar --installClient
                    2. Переконайтесь, що у forge/libraries/net/minecraftforge/bootstraplauncher/ є jar-файл.
                    3. Якщо його немає, перевірте лог інсталятора Forge на помилки мережі.
                    """));
                return false;
            }

            StringBuilder cp = new StringBuilder();
            cp.append(bootstrapLauncherJar.getAbsolutePath()).append(File.pathSeparator);
            for (File lib : allForgeLibs) {
                if (!lib.equals(bootstrapLauncherJar)) {
                    cp.append(lib.getAbsolutePath()).append(File.pathSeparator);
                }
            }
            cp.append(forgeJar.getAbsolutePath());

            Process proc = createProcess(javaPath, cp.toString(), version);

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    do {
                        line = reader.readLine();
                        if (line != null) {
                            String ln = "[Forge] " + line + "\n";
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
                            String ln = "[Forge-ERR] " + line + "\n";
                            SwingUtilities.invokeLater(() -> logArea.append(ln));
                        }
                    } while (line != null);
                } catch (IOException ignored) {}
            }).start();

            return true;
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> logArea.append("Помилка запуску Forge: " + ex + "\n"));
            return false;
        }
    }

    private Process createProcess(String javaPath, String classpath, String version) throws IOException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javaPath);
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add("cpw.mods.bootstraplauncher.BootstrapLauncher");
        cmd.add("--username");
        cmd.add("Player");
        cmd.add("--accessToken");
        cmd.add("12345");
        cmd.add("--version");
        cmd.add(version);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(System.getProperty("user.dir")));
        return pb.start();
    }

    private boolean installForgeInBackground(String javaPath) {
        try {
            File origJar = new File("minecraft-1.21.5-client.jar");
            File forgeMcJar = new File(FORGE_DIR, "minecraft.jar");
            if (!forgeMcJar.exists()) {
                if (!origJar.exists()) {
                    SwingUtilities.invokeLater(() -> logArea.append("Не знайдено minecraft-1.21.5-client.jar для Forge!\n"));
                    return false;
                }
                Files.copy(origJar.toPath(), forgeMcJar.toPath());
            }

            File launcherProfiles = new File(FORGE_DIR, "launcher_profiles.json");
            if (!launcherProfiles.exists()) {
                try (FileWriter fw = new FileWriter(launcherProfiles)) {
                    fw.write("{\"profiles\":{}}");
                }
            }

            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(javaPath);
            cmd.add("-jar");
            cmd.add(FORGE_1_21_5_FILE.getAbsolutePath());
            cmd.add("--installClient");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(FORGE_DIR);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            do {
                line = reader.readLine();
                if (line != null) {
                    String ln = "[Forge] " + line + "\n";
                    SwingUtilities.invokeLater(() -> logArea.append(ln));
                }
            } while (line != null);
            int exitCode = proc.waitFor();

            if (!forgeMcJar.delete()) {
                // Не критично, якщо не видалилось
            }

            return exitCode == 0;
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> logArea.append("Помилка інсталяції Forge: " + ex + "\n"));
            return false;
        }
    }

    private void collectJars(File dir, java.util.List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectJars(f, out);
            } else if (f.getName().endsWith(".jar")) {
                out.add(f);
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
}
