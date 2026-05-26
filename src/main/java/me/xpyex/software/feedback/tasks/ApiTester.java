package me.xpyex.software.feedback.tasks;

import me.xpyex.software.feedback.util.AiyouduUtil;

/**
 * API 测试工具类
 * 用于测试 API 接口,自动携带 Token 进行请求
 */
public class ApiTester {

    /**
     * 测试 URL,自动携带 Token 访问 API
     *
     * @param url 要测试的 URL
     */
    public static void getFromUrlTest(String url) {
        // 检查 Token 是否存在
        if (AiyouduUtil.token == null || AiyouduUtil.token.isEmpty()) {
            System.out.println("⚠️  未检测到有效的 Token!");
            System.out.println("请先执行【0】getToken 操作获取 Token");
            return;
        }

        // 如果 URL 为空,取消测试
        if (url == null || url.isEmpty()) {
            System.out.println("已取消测试");
            return;
        }

        // 如果 URL 不包含协议,自动添加 https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        System.out.println("\n========== API 测试工具 ==========");
        System.out.println("当前 Token: " + AiyouduUtil.token.substring(0, Math.min(20, AiyouduUtil.token.length())) + "...");
        System.out.println("\n正在请求: " + url);
        System.out.println("----------------------------------------");

        try {
            long startTime = System.currentTimeMillis();
            String response = AiyouduUtil.getUrlWithToken(url);
            long endTime = System.currentTimeMillis();

            System.out.println("响应时间: " + (endTime - startTime) + " ms");
            System.out.println("----------------------------------------");

            if (response != null && !response.isEmpty()) {
                System.out.println("响应内容:");
                System.out.println(response);
            } else {
                System.out.println("⚠️  响应为空");
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("========================================\n");
    }

    /**
     * 测试 POST URL,自动携带 Token 和请求体访问 API
     *
     * @param url         要测试的 URL
     * @param requestBody POST 请求体内容（JSON 格式）
     */
    public static void postToUrlTest(String url, String requestBody) {
        // 检查 Token 是否存在
        if (AiyouduUtil.token == null || AiyouduUtil.token.isEmpty()) {
            System.out.println("⚠️  未检测到有效的 Token!");
            System.out.println("请先执行【0】getToken 操作获取 Token");
            return;
        }

        // 如果 URL 为空,取消测试
        if (url == null || url.isEmpty()) {
            System.out.println("已取消测试");
            return;
        }

        // 如果 URL 不包含协议,自动添加 https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        System.out.println("\n========== API POST 测试工具 ==========");
        System.out.println("当前 Token: " + AiyouduUtil.token.substring(0, Math.min(20, AiyouduUtil.token.length())) + "...");
        System.out.println("\n正在 POST 请求: " + url);
        System.out.println("请求体: " + requestBody);
        System.out.println("----------------------------------------");

        try {
            long startTime = System.currentTimeMillis();
            String response = AiyouduUtil.postUrlWithToken(url, requestBody);
            long endTime = System.currentTimeMillis();

            System.out.println("响应时间: " + (endTime - startTime) + " ms");
            System.out.println("----------------------------------------");

            if (response != null && !response.isEmpty()) {
                System.out.println("响应内容:");
                System.out.println(response);
            } else {
                System.out.println("⚠️  响应为空");
            }
        } catch (Exception e) {
            System.out.println("❌ 请求失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("========================================\n");
    }
}
