package me.xpyex.software.feedback.tasks;

import me.xpyex.software.feedback.ui.MainWindow;
import me.xpyex.software.feedback.util.AiyouduUtil;
import me.xpyex.software.feedback.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token 获取器
 * 负责启动浏览器并等待用户登录以获取 Token
 */
public class TokenGetter {
    private static final Logger log = LoggerFactory.getLogger(TokenGetter.class.getSimpleName());

    /**
     * 启动浏览器并等待用户登录获取 Token
     */
    public static void start() {
        log.info("========================================");
        log.info("   Token 获取器启动");
        log.info("========================================");

        try {
            // 检查是否已有 Token
            if (AiyouduUtil.token != null && !AiyouduUtil.token.isEmpty()) {
                log.info("√ 已存在有效 Token，跳过登录");
                log.info("当前 Token: {}...", AiyouduUtil.token.substring(0, Math.min(20, AiyouduUtil.token.length())));
                return;
            }

            // 启动浏览器并等待登录
            log.info(">>> 正在启动浏览器，请登录...");
            log.info("提示：请在浏览器中完成登录，程序将一直等待直到登录成功或您关闭软件");

            AiyouduUtil.loginUsingBrowser();

            while (AiyouduUtil.token == null || AiyouduUtil.token.isEmpty()) {
                log.info("等待 Token...");
                if (TimeUtil.sleep(2000) != null) {
                    log.error("等待被中断");
                    return;
                }
            }

            if (MainWindow.current != null) {
                MainWindow.current.log("√ 登录成功！Token 已获取");
            }
            log.info("√ 登录成功！Token 已获取");
            log.info("Token: {}...", AiyouduUtil.token.substring(0, Math.min(20, AiyouduUtil.token.length())));

        } catch (Exception e) {
            log.error("获取 Token 过程中发生错误：", e);
        }
    }

    /**
     * 检查是否已有有效 Token
     *
     * @return 如果已有 Token 返回 true
     */
    public static boolean hasToken() {
        return AiyouduUtil.token != null && !AiyouduUtil.token.isEmpty();
    }
}
