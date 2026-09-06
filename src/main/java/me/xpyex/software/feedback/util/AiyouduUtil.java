package me.xpyex.software.feedback.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import me.xpyex.software.feedback.Main;
import org.slf4j.Logger;

public class AiyouduUtil {
    public static final Logger log = LogUtil.getLogger();
    public static final String rootUrl = "https://group.aiyoudu.cn/";
    public static final String apiUrl = rootUrl + "api2/";
    public static final String orgUrl = apiUrl + "organiztion/";  // 这草台班子拼错的
    public static String token = null;
    private static Map<String, String> savedCookies = new HashMap<>();  // 保存 cookies 用于 API 请求

    public static boolean hasToken() {
        return token != null && !token.isEmpty();
    }

    public static String getUrlWithToken(String apiUrl) {
        return accessUrlWithToken("GET", apiUrl, null);
    }

    public static String postUrlWithToken(String apiUrl, String postContent) {
        return accessUrlWithToken("POST", apiUrl, postContent);
    }

    public static String putUrlWithToken(String apiUrl, String putContent) {
        return accessUrlWithToken("PUT", apiUrl, putContent);
    }

    public static String postUrlWithToken(String apiUrl, Object postObj) {
        return postUrlWithToken(apiUrl, GsonUtil.toJsonStr(postObj, false));
    }

    public static String putUrlWithToken(String apiUrl, Object putObj) {
        return putUrlWithToken(apiUrl, GsonUtil.toJsonStr(putObj, false));
    }

    /**
     * 发送 GET/POST/PUT 请求并携带 Token 和 Cookie
     *
     * @param method  请求方法：GET / POST / PUT
     * @param apiUrl  API URL
     * @param content 请求体内容（JSON 格式），GET 传 null
     * @return 响应结果
     */
    private static String accessUrlWithToken(String method, String apiUrl, String content) {
        try {
            log.info("正在发送 {} 请求到：{}", method, apiUrl);
            if (Main.debug && content != null) {
                log.info("{} 内容：{}", method, content);
            }
            // 创建 HttpClient 实例
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = switch (method.toUpperCase()) {
                case "GET" -> createRequest(apiUrl).GET().build();
                case "POST" -> createRequest(apiUrl).POST(HttpRequest.BodyPublishers.ofString(content)).build();
                case "PUT" -> createRequest(apiUrl).PUT(HttpRequest.BodyPublishers.ofString(content)).build();
                default -> throw new IllegalArgumentException("不支持的请求方法：" + method);
            };
            // 发送请求并获取响应
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // 检查响应状态码
            int statusCode = response.statusCode();
            String responseBody = response.body();
            if (statusCode == 200) {
                log.info("√ {} 请求成功！", method);
                if (Main.debug) log.info("响应数据：{}", responseBody);
                return responseBody;
            } else {
                log.error("✗ {} 请求失败，状态码：{}", method, statusCode);
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

    private static HttpRequest.Builder createRequest(String apiUrl) {
        if (Main.debug) {
            log.info("使用的 Token: {}...", token.substring(0, Math.min(50, token.length())));
            log.info("Token Header Key: {}", "Authorization");
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
                                                 .header("Authorization", token)  // 使用配置的 Token key
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
}
