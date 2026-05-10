package com.wibudungeon.core.util;

import com.wibudungeon.core.WibuDungeon;
import org.bukkit.Bukkit;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoUpdater {

    private final WibuDungeon plugin;
    private final String repoOwner = "TanNoWibuOKE-DEV";
    private final String repoName = "WibuDungeon";

    public AutoUpdater(WibuDungeon plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + repoOwner + "/" + repoName + "/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (conn.getResponseCode() == 200) {
                    Scanner scanner = new Scanner(conn.getInputStream());
                    StringBuilder response = new StringBuilder();
                    while (scanner.hasNext()) {
                        response.append(scanner.nextLine());
                    }
                    scanner.close();

                    String json = response.toString();
                    
                    // Lấy version từ tag_name
                    String latestVersion = extractFromJson(json, "\"tag_name\":\\s*\"([^\"]+)\"");
                    if (latestVersion != null) {
                        latestVersion = latestVersion.replace("v", "");
                        String currentVersion = plugin.getDescription().getVersion().replace("v", "");

                        // So sánh phiên bản
                        if (!currentVersion.equals(latestVersion)) {
                            plugin.getLogger().info("==========================================");
                            plugin.getLogger().info("Da tim thay phien ban moi cua WibuDungeon!");
                            plugin.getLogger().info("Hien tai: " + currentVersion + " | Phien ban moi: " + latestVersion);
                            plugin.getLogger().info("Dang tien hanh tai ve thu muc plugins/update...");
                            plugin.getLogger().info("==========================================");
                            
                            // Lấy link tải jar
                            String downloadUrl = extractFromJson(json, "\"browser_download_url\":\\s*\"([^\"]+\\.jar)\"");
                            if (downloadUrl != null) {
                                downloadUpdate(downloadUrl);
                            } else {
                                plugin.getLogger().warning("Khong tim thay file .jar trong Release tren GitHub.");
                            }
                        } else {
                            plugin.getLogger().info("WibuDungeon dang o phien ban moi nhat (" + currentVersion + ").");
                        }
                    }
                } else if (conn.getResponseCode() == 404) {
                    plugin.getLogger().info("Chua co ban Release nao tren GitHub.");
                } else {
                    plugin.getLogger().warning("Khong the kiem tra ban cap nhat. Ma loi HTTP: " + conn.getResponseCode());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Loi khi kiem tra cap nhat WibuDungeon: " + e.getMessage());
            }
        });
    }

    private String extractFromJson(String json, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void downloadUpdate(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "WibuDungeon-Updater");
            
            // Create update folder in plugins directory
            java.io.File updateFolder = new java.io.File(plugin.getDataFolder().getParentFile(), "update");
            if (!updateFolder.exists()) {
                updateFolder.mkdirs();
            }

            java.io.File pluginFile = new java.io.File(updateFolder, "WibuDungeon.jar");
            
            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(pluginFile)) {
                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }
            plugin.getLogger().info("Da tai thanh cong WibuDungeon ban moi! Ban hay khoi dong lai server (Restart) de ap dung cap nhat.");
        } catch (Exception e) {
            plugin.getLogger().severe("Loi khi tai ban cap nhat: " + e.getMessage());
        }
    }
}
