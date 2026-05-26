package me.xpyex.software.feedback.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.util.GsonUtil;
import me.xpyex.software.feedback.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeepSeek AI 对话分析器
 * 功能：读取 students 目录下的学生信息文件，与 DeepSeek AI 对话分析
 */
public class DeepSeekAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekAnalyzer.class.getSimpleName());

    // 配置文件路径
    private static final File FOLDER = new File("AI");
    private static final String API_URL_FILE = "api.txt";
    private static final String API_KEY_FILE = "key.txt";
    private static final String PROMPT_FILE = "prompt.txt";
    private static final String MODEL_FILE = "model.txt";

    // DeepSeek API 配置
    private static String apiUrl;
    private static String apiKey;
    private static String promptTemplate;
    private static String model;

    // GUI选中的学生列表
    private static List<StudentInfo> selectedStudents = null;

    public static void start() {
        startWithStudents(null);
    }

    /**
     * 启动分析流程（支持GUI选中学生）
     *
     * @param students GUI选中的学生列表，为null则处理所有学生
     */
    public static void startWithStudents(List<StudentInfo> students) {
        selectedStudents = students;

        log.info("========================================");
        log.info("   DeepSeek AI 对话分析器启动");
        log.info("========================================");

        try {
            // 1. 加载配置文件
            loadConfig();

            // 2. 读取 students 目录下的所有文件
            List<FileData> studentDataList = readStudentsDirectory();

            if (studentDataList.isEmpty()) {
                log.warn("students 目录下没有找到学生数据文件");
                return;
            }

            log.info("共找到 {} 个学生数据文件", studentDataList.size());

            // 3. 逐个与 DeepSeek 对话
            analyzeWithDeepSeek(studentDataList);

            log.info("========================================");
            log.info("   所有学生数据分析完成！");
            log.info("========================================");

        } catch (Exception e) {
            log.error("分析过程中发生错误：", e);
        } finally {
            selectedStudents = null;  // 清空
        }
    }

    /**
     * 加载配置文件
     */
    private static void loadConfig() throws IOException {
        log.info("正在加载配置文件...");

        // 读取 API 地址
        File apiFile = new File(FOLDER, API_URL_FILE);
        if (!apiFile.exists()) {
            throw new IOException("API 配置文件不存在：" + API_URL_FILE);
        }
        apiUrl = Files.readString(apiFile.toPath(), StandardCharsets.UTF_8).trim();
        log.info("API 地址：{}", apiUrl);

        // 读取 API 密钥
        File keyFile = new File(FOLDER, API_KEY_FILE);
        if (!keyFile.exists()) {
            throw new IOException("API 密钥文件不存在：" + API_KEY_FILE);
        }
        apiKey = Files.readString(keyFile.toPath(), StandardCharsets.UTF_8).trim();
        log.info("API 密钥：{}...", apiKey.substring(0, Math.min(10, apiKey.length())));

        // 读取 Prompt 模板
        File promptFile = new File(FOLDER, PROMPT_FILE);
        if (!promptFile.exists()) {
            throw new IOException("Prompt 模板文件不存在：" + PROMPT_FILE);
        }
        promptTemplate = Files.readString(promptFile.toPath(), StandardCharsets.UTF_8).trim();
        log.info("Prompt 模板长度：{} 字符", promptTemplate.length());

        //读取模型
        File modelFile = new File(FOLDER, MODEL_FILE);
        if (!modelFile.exists()) {
            throw new IOException("模型文件不存在：" + MODEL_FILE);
        }
        model = Files.readString(modelFile.toPath(), StandardCharsets.UTF_8).trim();
        log.info("模型：{}", model);
    }

    /**
     * 读取 students 目录下的所有文件
     */
    private static List<FileData> readStudentsDirectory() throws IOException {
        List<FileData> dataList = new ArrayList<>();

        File studentsDir = new File("students");
        if (!studentsDir.exists() || !studentsDir.isDirectory()) {
            log.warn("students 目录不存在");
            return dataList;
        }

        // 递归查找所有 .json 和 .txt 文件
        findDataFiles(studentsDir, dataList);

        // 如果GUI选中了学生，过滤只保留选中学生的文件
        if (selectedStudents != null && !selectedStudents.isEmpty()) {
            List<String> selectedFileNames = selectedStudents.stream().map(StudentInfo::getRealName).toList();

            dataList.removeIf(data -> selectedFileNames.stream().noneMatch(name -> data.fileName.contains(name)));
            log.info("GUI选中了 {} 个学生，过滤后剩余 {} 个文件", selectedStudents.size(), dataList.size());
        }

        return dataList;
    }

    /**
     * 递归查找数据文件
     */
    private static void findDataFiles(File dir, List<FileData> dataList) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归进入子目录
                findDataFiles(file, dataList);
            } else if (file.getName().endsWith(".json") || file.getName().endsWith(".txt")) {
                // 跳过分析结果文件
                if (file.getName().contains(".feedback.")) continue;

                FileData data = new FileData();
                data.content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                data.fileName = file.getName();
                data.filePath = file.getAbsolutePath();
                data.relativePath = file.getPath();

                log.info("读取文件：{}", data.relativePath);
                dataList.add(data);
            }
        }
    }

    /**
     * 获取最新的文件
     */
    private static File getLatestFile(File[] files) {
        if (files == null || files.length == 0) {
            return null;
        }

        File latest = files[0];
        for (File file : files) {
            if (file.lastModified() > latest.lastModified()) {
                latest = file;
            }
        }
        return latest;
    }

    /**
     * 与 DeepSeek AI 对话分析
     */
    private static void analyzeWithDeepSeek(List<FileData> fileDataList) {
        log.info(">>> 开始与 DeepSeek AI 对话...");

        int total = fileDataList.size();
        int current = 0;

        for (FileData data : fileDataList) {
            current++;
            log.info("----------------------------------------");
            log.info("[{}/{}] 正在分析文件：{}", current, total, data.relativePath);

            try {
                // 发送请求到 DeepSeek
                String response = sendToDeepSeek(promptTemplate, data.content);

                if (response != null && !response.isEmpty()) {
                    log.info("√ DeepSeek 响应成功");
                    log.info("AI 分析结果：\n{}", response);

                    // 保存分析结果
                    saveAnalysisResult(data, response);
                } else {
                    log.error("✗ DeepSeek 返回空响应");
                }

            } catch (Exception e) {
                log.error("  分析文件 {} 时发生错误：", data.relativePath, e);
            }

            // 如果不是最后一个，等待一下
            if (current < total) {
                log.info("  等待 2 秒后继续下一个文件...");
                if (TimeUtil.sleep(2000) != null)
                    Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 发送请求到 DeepSeek API
     */
    private static String sendToDeepSeek(String prompt, String fileContent) {
        try {
            log.info("正在发送请求到 DeepSeek API...");

            // 构建请求体 - prompt 作为 system，文件内容作为 user
            String requestBody = """
                {
                    "model": "{$model}",
                    "messages": [
                        {
                            "role": "system",
                            "content": "{$prompt}"
                        },
                        {
                            "role": "user",
                            "content": "{$fileContent}"
                        }
                    ],
                    "stream": false
                }
                """.replace("{$prompt}", prompt)
                                     .replace("{$fileContent}", fileContent)
                                     .replace("{$date}", TimeUtil.parseDate(new Date(), "yyyy-MM-dd"))
                                     .replace("{$model}", model);
            requestBody = toJsonStr(requestBody);

            // 创建 HttpClient
            HttpClient client = HttpClient.newHttpClient();

            // 构建 HTTP POST 请求
            HttpRequest request = HttpRequest.newBuilder()
                                      .uri(URI.create(apiUrl))
                                      .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                      .header("Content-Type", "application/json")
                                      .header("Authorization", "Bearer " + apiKey)
                                      .build();

            // 发送请求
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode == 200) {
                log.info("√ DeepSeek API 请求成功");

                // 解析响应，提取 content
                return getDeepSeekResponse(responseBody);

            } else {
                log.error("✗ DeepSeek API 请求失败，状态码：{}", statusCode);
                log.error("响应内容：{}", responseBody);
            }

        } catch (Exception e) {
            log.error("发送 DeepSeek 请求时发生错误：", e);
        }

        return "";
    }

    private static String toJsonStr(String prompt) {
        return GsonUtil.toJsonStr(GsonUtil.parseJsonObj(prompt), false);
    }

    /**
     * 从 DeepSeek 响应中提取 content
     */
    private static String getDeepSeekResponse(String jsonResponse) {
        try {
            JsonObject jsonObj = GsonUtil.parseJsonObj(jsonResponse);

            if (jsonObj.has("choices")) {
                JsonArray choices = jsonObj.getAsJsonArray("choices");
                if (!choices.isEmpty()) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (choice.has("message")) {
                        JsonObject message = choice.getAsJsonObject("message");
                        if (message.has("content")) {
                            return message.get("content").getAsString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析 DeepSeek 响应失败：", e);
            log.error(jsonResponse);
        }

        return "";
    }

    /**
     * 保存分析结果
     */
    private static void saveAnalysisResult(FileData data, String analysis) {
        try {
            File originalFile = new File(data.filePath);
            File parentDir = originalFile.getParentFile();

            // 保存分析结果到同名但带.feedback 后缀的文件
            String baseName = originalFile.getName();
            String analysisFileName = baseName.replace(".json", ".feedback.txt")
                                          .replace(".txt", ".feedback.txt");

            String originalData = Files.readString(originalFile.toPath(), StandardCharsets.UTF_8);

            File analysisFile = new File(parentDir, analysisFileName);

            Files.writeString(analysisFile.toPath(), originalData + "\n" + analysis, StandardCharsets.UTF_8);

            log.info("  结果已保存到：{}", analysisFile.getAbsolutePath());

        } catch (IOException e) {
            log.error("  保存结果失败：", e);
        }
    }

    /**
     * 文件数据内部类
     */
    private static class FileData {
        String content;        // 文件内容
        String fileName;       // 文件名
        String filePath;       // 文件绝对路径
        String relativePath;   // 相对路径（用于显示）
    }
}
