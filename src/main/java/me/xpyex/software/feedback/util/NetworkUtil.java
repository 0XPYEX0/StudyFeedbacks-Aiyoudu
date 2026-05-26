package me.xpyex.software.feedback.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkUtil {
    private static final Logger log = LoggerFactory.getLogger(NetworkUtil.class);

    public static void killEdgeDriver() {
        System.out.println("尝试关闭 msedgedriver.exe...");
        try {
            Runtime.getRuntime().exec("taskkill /F /IM msedgedriver.exe").waitFor();
            System.out.println("已关闭 msedgedriver.exe");
        } catch (Exception e) {
            System.out.println("关闭 msedgedriver.exe 时出错: " + e.getMessage());
        }
    }

    public static void downloadFile(String url, File folder, String fileName) throws IOException, InterruptedException {
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File outputFile = new File(folder, fileName);

        log.info("  → 正在下载文件到：{}", outputFile.getPath());

        // 下载文件
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                                  .uri(URI.create(url))
                                  .GET()
                                  .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 200) {
            Files.copy(response.body(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("  √ 文件下载成功：{}", outputFile.getName());
        } else {
            log.error("  ✗ 文件下载失败，状态码：{}", response.statusCode());
        }
    }
}
