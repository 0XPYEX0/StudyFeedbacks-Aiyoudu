package me.xpyex.software.feedback.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.xpyex.software.feedback.Main;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.packet.in.DataInfo;
import me.xpyex.software.feedback.packet.in.FinishedTaskPanel;
import me.xpyex.software.feedback.packet.in.SinglePanel;
import me.xpyex.software.feedback.packet.out.SearchStudents;
import org.slf4j.Logger;

public class AiyouduUtil {
    public static final Logger log = LogUtil.getLogger();
    public static final String rootUrl = "https://group.aiyoudu.cn/";
    public static final String apiUrl = rootUrl + "api2/";
    private static final String getProfileDateUrl = apiUrl + "organiztion/student/myMonthData?studentId={$id}&startDate={$start}&endDate={$end}";
    private static final String dataInfoUrl = apiUrl + "organiztion/student/dataInfo?studentId={$id}";
    // API 认证配置
    private static final String TOKEN_HEADER_KEY = "Authorization";  // Token 在 HTTP 头中的 key 名称
    public static String token = null;
    // 保存 cookies 用于 API 请求
    private static Map<String, String> savedCookies = new HashMap<>();

    public static String getUrlWithToken(String apiUrl) {
        try {
            // 创建 HttpClient 实例
            HttpClient client = HttpClient.newHttpClient();

            log.info("正在发送 GET 请求到：{}", apiUrl);

            HttpRequest request = createRequest(apiUrl).GET().build();

            // 发送请求并获取响应
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 检查响应状态码
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode == 200) {
                log.info("√ API 请求成功！");
                if (Main.debug) log.info("响应数据：{}", responseBody);
                return responseBody;
                // 可以在这里解析 JSON 响应
            } else {
                log.error("✗ API 请求失败，状态码：{}", statusCode);
                log.error("响应内容：{}", responseBody);

                // 如果返回 token 过期，尝试使用 Cookie 认证
                if (responseBody.contains("token 过期") || responseBody.contains("unauthorized")) {
                    log.warn(" Token 可能已过期或无效");
                }
            }

        } catch (Exception e) {
            log.error("访问 API 时发生错误：", e);
        }
        return "";
    }

    /**
     * 发送 POST 请求并携带 Token 和 Cookie
     *
     * @param apiUrl      API URL
     * @param postContent POST 的请求体内容（JSON 格式）
     * @return 响应结果
     */
    public static String postUrlWithToken(String apiUrl, String postContent) {
        try {
            log.info("正在发送 POST 请求到：{}", apiUrl);
            if (Main.debug) {
                log.info("POST 内容：{}", postContent);
            }
            // 创建 HttpClient 实例
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = createRequest(apiUrl).POST(HttpRequest.BodyPublishers.ofString(postContent)).build();
            // 发送请求并获取响应
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // 检查响应状态码
            int statusCode = response.statusCode();
            String responseBody = response.body();
            if (statusCode == 200) {
                log.info("√ POST 请求成功！");
                if (Main.debug) log.info("响应数据：{}", responseBody);
                return responseBody;
            } else {
                log.error("✗ POST 请求失败，状态码：{}", statusCode);
                log.error("响应内容：{}", responseBody);

                // 如果返回 token 过期，尝试使用 Cookie 认证
                if (responseBody.contains("token 过期") || responseBody.contains("unauthorized")) {
                    log.warn(" Token 可能已过期或无效");
                }
            }

        } catch (Exception e) {
            log.error("访问 API 时发生错误：", e);
        }
        return "";
    }

    public static String putUrlWithToken(String apiUrl, String putContent) {
        try {
            log.info("正在发送 PUT 请求到：{}", apiUrl);
            if (Main.debug) {
                log.info("PUT 内容：{}", putContent);
            }
            // 创建 HttpClient 实例
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = createRequest(apiUrl).PUT(HttpRequest.BodyPublishers.ofString(putContent)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();
            if (statusCode == 200) {
                log.info("√ PUT 请求成功！");
                return responseBody;
            } else {
                log.error("✗ PUT 请求失败，状态码：{}", statusCode);
                log.error("响应内容：{}", responseBody);

                if (responseBody.contains("token 过期") || responseBody.contains("unauthorized")) {
                    log.warn(" Token 可能已过期或无效");
                }
            }
        } catch (Exception e) {
            log.error("访问 API 时发生错误：", e);
        }
        return "";
    }

    private static HttpRequest.Builder createRequest(String apiUrl) {
        if (Main.debug) {
            log.info("使用的 Token: {}...", token.substring(0, Math.min(50, token.length())));
            log.info("Token Header Key: {}", TOKEN_HEADER_KEY);
        }

        // 构建 Cookie 字符串（如果有保存的 cookies）
        StringBuilder cookieHeader = new StringBuilder();
        if (!savedCookies.isEmpty()) {
            savedCookies.forEach((key, value) ->
                                     cookieHeader.append(key).append("=").append(value).append("; ")
            );
            if (Main.debug)
                log.info("使用 Cookie: {}", cookieHeader.substring(0, Math.min(50, cookieHeader.length())) + "...");
        }

        // 构建 HTTP POST 请求 - 使用可配置的 Token header key
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                                 .uri(URI.create(apiUrl))
                                                 .header("Content-Type", "application/json")
                                                 .header(TOKEN_HEADER_KEY, token)  // 使用配置的 Token key
                                                 .header("Accept", "application/json, text/plain, */*")
                                                 .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0");

        // 添加 Cookie（如果有）
        if (!savedCookies.isEmpty()) {
            requestBuilder.header("Cookie", cookieHeader.toString());
        }
        return requestBuilder;
    }

    public static void loginUsingBrowser() {
        // 创建登录工具实例
        LoginUtil loginUtil = new LoginUtil(rootUrl);

        try {
            // 1. 初始化浏览器
            loginUtil.initBrowser();

            // 2. 打开登录页面
            loginUtil.openLoginPage();

            // 3. 等待用户手动完成登录
            boolean loginSuccess = loginUtil.waitForManualLogin();

            if (loginSuccess) {
                // 4. 获取 Token（多种方式）
                String tokenFromStorage = loginUtil.getToken();

                if (tokenFromStorage != null) {
                    LogUtil.line();
                    log.info("从 LocalStorage/Cookie 获取到 Token: {}", tokenFromStorage);
                    log.info("Token 长度：{}", tokenFromStorage.length());
                    LogUtil.line();

                    // 5. 获取所有 Cookies
                    Map<String, String> cookies = loginUtil.getCookies();
                    log.info("所有 Cookies:");
                    cookies.forEach((key, value) ->
                                        log.info("  {} = {}", key, value)
                    );

                    // 保存 cookies 用于后续请求
                    savedCookies = cookies;
                    token = tokenFromStorage;
                    TimeUtil.sleep(3000);
                    loginUtil.quit();
                } else {
                    log.warn("未能获取到 Token");
                }
            } else {
                log.error("登录失败或超时");
            }

        } catch (Exception e) {
            log.error("发生错误：", e);
        }
    }

    public static DataInfo getStudentData(int id) {
        AYDResponse obj = AYDResponse.of(getUrlWithToken(dataInfoUrl.replace("{$id}", "" + id)));
        if (obj.isSuccess() && "成功".equals(obj.getMessage())) {
            return GsonUtil.getGson().fromJson(obj.getData(), DataInfo.class);
        }
        return null;
    }

    public static List<StudentInfo> getAllStudents() {
        ArrayList<StudentInfo> list = new ArrayList<>();
        AYDResponse obj = AYDResponse.of(postUrlWithToken(SearchStudents.url, SearchStudents.of().setSize(100).toJsonStr(false)));
        if (obj.isSuccess()) {
            JsonArray students = obj.getDataAsJsonObject().getAsJsonArray("records");
            for (JsonElement student : students) {
                StudentInfo info = GsonUtil.getGson().fromJson(student, StudentInfo.class);
                log.info("{} {} {}", info.getStudentId(), info.getRealName(), info.getGroup());
                list.add(info.setDataInfo(getStudentData(info.getStudentId())));
                try {
                    Thread.sleep(1500);  //等1.5秒
                } catch (InterruptedException e) {
                    Thread.currentThread().stop();
                    return list;
                }
            }
        }
        return list;
    }

    public static FinishedTaskPanel getStudentFinished(int id, String startTime, String endTime) {
        String apiUrl = getProfileDateUrl
                            .replace("{$id}", "" + id)
                            .replace("{$start}", startTime)
                            .replace("{$end}", endTime);
        AYDResponse body = GsonUtil.parseObj(getUrlWithToken(apiUrl), AYDResponse.class);
        if (body.isSuccess()) {
            return GsonUtil.getGson().fromJson(body.getDataAsJsonObject().getAsJsonObject("myDataInfo"), FinishedTaskPanel.class)
                       .setWordAndReadList(body.getDataAsJsonObject()
                                               .getAsJsonArray("wordAndReadList").asList()
                                               .stream()
                                               .map(e -> GsonUtil.getGson().fromJson(e, SinglePanel.class))
                                               .toList()
                       ).setListeningAndList(body.getDataAsJsonObject()
                                                 .getAsJsonArray("listeningAndList")
                                                 .asList()
                                                 .stream()
                                                 .map(e -> GsonUtil.getGson().fromJson(e, SinglePanel.class))
                                                 .toList()
                );
        }
        return null;
    }
}
