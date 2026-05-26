package me.xpyex.software.feedback.util;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录工具类 - 使用 Selenium 控制浏览器自动登录并获取 Token
 */
public class LoginUtil {
    private static final Logger log = LoggerFactory.getLogger(LoginUtil.class.getSimpleName());
    private final String baseUrl;
    /**
     * -- GETTER --
     * 获取 WebDriver 实例（用于高级操作）
     */
    @Getter
    private WebDriver driver;
    private WebDriverWait wait;

    public LoginUtil(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 初始化 Edge 浏览器（Windows 自带）
     */
    public void initBrowser() {
        // 优先使用项目目录中的 EdgeDriver
        String driverPath = "driver" + File.separator + "msedgedriver.exe";
        File driverFile = new File(driverPath);

        if (driverFile.exists()) {
            log.info("使用本地 EdgeDriver: {}", driverPath);
            System.setProperty("webdriver.edge.driver", driverFile.getAbsolutePath());
        } else {
            // 尝试自动下载
            try {
                log.info("正在配置 Edge 驱动...");
                WebDriverManager.edgedriver().setup();
            } catch (Exception e) {
                log.warn("自动下载失败，尝试使用系统 Edge: {}", e.getMessage());
            }
        }

        // 创建 Edge 浏览器实例
        try {
            driver = new EdgeDriver();
            wait = new WebDriverWait(driver, 120);
        } catch (SessionNotCreatedException e) {
            if (e.getMessage().contains("version")) {
                System.out.println("当前EdgeWebDriver与Edge版本不匹配，已结束进程，尝试自动下载。若无效请尝试手动下载");
                NetworkUtil.killEdgeDriver();
                WebDriverManager.edgedriver().setup();
                driverFile.delete();  //下载成功再删除
                log.info("当前Edge已被更新，EdgeDriver自动更新中，请稍后重启本程序再试");
            }
        }

        log.info("浏览器已初始化");
    }

    /**
     * 打开登录页面
     */
    public void openLoginPage() {
        if (driver == null) {
            initBrowser();
        }

        log.info("正在打开登录页面：{}", baseUrl);
        driver.get(baseUrl);

        // 等待页面加载完成
        TimeUtil.sleep(3000); // 等待 3 秒确保页面基本加载
        log.info("页面已加载完成");

    }

    /**
     * 等待用户手动完成登录，并检测登录成功
     *
     * @return 登录成功后返回 true
     */
    public boolean waitForManualLogin() {
        log.info("请在浏览器中手动完成登录...");
        log.info("等待登录成功跳转...");

        String initialUrl = driver.getCurrentUrl();
        log.info("初始 URL: {}", initialUrl);

        try {
            // 先等待一小段时间，避免页面还在加载中
            Thread.sleep(2000);

            // 等待 URL 变化或者出现登录后的特定元素（需要根据实际页面调整）
            // 这里假设登录后 URL 会发生变化，或者出现登出按钮等元素
            wait.until(webDriver -> {
                String currentUrl = webDriver.getCurrentUrl();
                // 如果 URL 不再包含 login 或者发生了变化，认为登录成功
                boolean urlChanged = !currentUrl.equals(initialUrl) && !currentUrl.contains("login");

                if (urlChanged) {
                    log.info("检测到 URL 变化：{} -> {}", initialUrl, currentUrl);
                }

                return urlChanged;
            });

            log.info("检测到登录成功！");
            return true;

        } catch (TimeoutException e) {
            log.error("等待登录超时，请重试");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待被中断");
            return false;
        }
    }

    /**
     * 获取所有 Cookies
     *
     * @return Cookie Map
     */
    public Map<String, String> getCookies() {
        if (driver == null) {
            log.error("浏览器未初始化");
            return new HashMap<>();
        }

        Map<String, String> cookieMap = new HashMap<>();
        Set<Cookie> cookies = driver.manage().getCookies();

        for (Cookie cookie : cookies) {
            cookieMap.put(cookie.getName(), cookie.getValue());
            log.debug("Cookie: {} = {}", cookie.getName(), cookie.getValue());
        }

        return cookieMap;
    }

    /**
     * 获取指定名称的 Cookie/Token
     *
     * @param name Cookie 名称
     * @return Cookie 值
     */
    public String getCookie(String name) {
        Cookie cookie = driver.manage().getCookieNamed(name);
        if (cookie != null) {
            log.info("获取到 Cookie [{}]: {}", name, cookie.getValue());
            return cookie.getValue();
        }
        log.warn("未找到 Cookie: {}", name);
        return null;
    }

    /**
     * 获取 Token 的多种常见方式
     *
     * @return Token 值
     */
    public String getToken() {
        log.info("开始从 LocalStorage 查找 Token...");

        // 优先从 LocalStorage 获取（根据 F12 看到的实际名称调整）
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 常见的 Token 名称列表
            String[] possibleTokenNames = {
                "token",
                "accessToken",
                "access_token",
                "jwt",
                "auth_token",
                "Authorization",
                "Token",
                "ACCESS_TOKEN",
                "user_token",
                "login_token",
                "token3"
            };

            // 尝试从 LocalStorage 获取
            for (String name : possibleTokenNames) {
                String token = (String) js.executeScript(
                    "return localStorage.getItem('" + name + "');"
                );
                if (token != null && !token.isEmpty()) {
                    log.info("从 LocalStorage 找到 Token [{}]: {}", name, token);
                    return token;
                }
            }

            // 如果没找到，打印 LocalStorage 中的所有键名
            log.info("未找到常见 Token 名称，正在检查 LocalStorage 内容...");
            String allKeys = (String) js.executeScript(
                "var keys = []; for(var i=0; i<localStorage.length; i++) { " +
                    "keys.push(localStorage.key(i) + '=' + localStorage.getItem(localStorage.key(i))); } " +
                    "return keys.join(', ');"
            );
            log.info("LocalStorage 内容：{}", allKeys);

        } catch (Exception e) {
            log.warn("无法从 LocalStorage 获取 Token: {}", e.getMessage());
        }

        // LocalStorage 中没有，再尝试从 Cookie 获取
        log.info("LocalStorage 中未找到，尝试从 Cookie 查找...");
        String[] possibleTokenNames = {"token", "accessToken", "access_token", "jwt", "auth_token", "Authorization", "token3"};

        for (String name : possibleTokenNames) {
            String token = getCookie(name);
            if (token != null && !token.isEmpty()) {
                log.info("从 Cookie 中找到 Token: {} = {}", name, token);
                return token;
            }
        }

        log.error("未找到任何 Token");
        return null;
    }

    /**
     * 关闭浏览器
     */
    public void quit() {
        if (driver != null) {
            driver.quit();
            log.info("浏览器已关闭");
        }
    }

}
