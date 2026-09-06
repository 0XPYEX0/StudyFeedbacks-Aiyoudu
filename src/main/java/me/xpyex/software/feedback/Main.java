package me.xpyex.software.feedback;

import java.util.Scanner;
import me.xpyex.software.feedback.tasks.basis.ApiTester;
import me.xpyex.software.feedback.tasks.feedback.DeepSeekAnalyzer;
import me.xpyex.software.feedback.tasks.studyPrepare.PrintStudentStudy;
import me.xpyex.software.feedback.tasks.studyPrepare.RenewStudentCard;
import me.xpyex.software.feedback.tasks.feedback.StudentInfoCollector;
import me.xpyex.software.feedback.tasks.basis.StudentReader;
import me.xpyex.software.feedback.tasks.basis.TokenGetter;
import me.xpyex.software.feedback.ui.MainWindow;
import me.xpyex.software.feedback.util.NetworkUtil;
import me.xpyex.software.feedback.util.TaskExecutor;
import me.xpyex.software.feedback.util.TimeUtil;

public class Main {
    private static final int waitSeconds = 3;
    public static boolean debug = false;

    public static void main(String[] args) throws Exception {
        // 检查启动参数
        for (String arg : args) {
            if ("gui=true".equalsIgnoreCase(arg)) {
                System.out.println("检测到有参数，自动启动图形化界面");
                MainWindow.showMainWindow();
            } else if ("debug=true".equalsIgnoreCase(arg)) {
                debug = true;
            }
        }

        // 启动CLI
        Scanner in = new Scanner(System.in);
        help();
        while (in.hasNext()) {
            String command = in.next();
            System.out.println(" ");
            if ("stop".equalsIgnoreCase(command)) {
                TaskExecutor.stopCurrentTask();
                continue;
            }
            if ("1".equals(command) || "getToken".equalsIgnoreCase(command)) {
                System.out.println(waitSeconds + " 秒后打开由本程序控制的 Edge 页面，请你手动登录");
                System.out.println("如果没有安装 Edge 浏览器，请安装，因为使用了 EdgeDriver");
                TimeUtil.sleep(waitSeconds * 1000L);
                String error = TaskExecutor.executeTask(TokenGetter::start, "TokenGetter-Thread");
                if (error != null) continue;
            } else if ("2".equals(command) || "readStudents".equalsIgnoreCase(command)) {
                String error = TaskExecutor.executeTask(StudentReader::start, "StudentReader-Thread");
                if (error != null) continue;
            } else if ("3".equals(command) || "collect".equalsIgnoreCase(command) || "collectProfiles".equalsIgnoreCase(command)) {
                // 让用户选择日期范围
                System.out.println("\n请选择采集的日期范围：");
                System.out.println("直接回车使用上周（上周一到周日）");
                System.out.print("请输入开始日期 (格式: yyyy-MM-dd): ");
                in.nextLine();
                String startDate = in.nextLine().trim();

                if (startDate.isEmpty()) {
                    // 使用默认的上周日期
                    String[] lastWeekRange = TimeUtil.getLastWeekRange();
                    StudentInfoCollector.setStart(lastWeekRange[0]);
                    StudentInfoCollector.setEnd(lastWeekRange[1]);
                    System.out.println("使用默认日期范围：" + lastWeekRange[0] + " ~ " + lastWeekRange[1]);
                } else {
                    System.out.print("请输入结束日期 (格式: yyyy-MM-dd): ");
                    String endDate = in.nextLine().trim();
                    StudentInfoCollector.setStart(startDate);
                    StudentInfoCollector.setEnd(endDate);
                    System.out.println("使用自定义日期范围：" + startDate + " ~ " + endDate);
                }

                String error = TaskExecutor.executeTask(StudentInfoCollector::start, "StudentInfoCollector-Thread");
                if (error != null) continue;
            } else if ("4".equals(command) || "feedback".equalsIgnoreCase(command)) {
                System.out.println("交由 AI 处理数据");
                String error = TaskExecutor.executeTask(DeepSeekAnalyzer::start, "DeepSeekAnalyzer-Thread");
                if (error != null) continue;
            } else if ("5".equals(command) || "print".equalsIgnoreCase(command) || "printStudy".equalsIgnoreCase(command)) {
                System.out.println("开始批量处理学生...");
                String error = TaskExecutor.executeTask(PrintStudentStudy::start, "PrintStudentStudy-Thread");
                if (error != null) continue;
            } else if ("6".equals(command) || "renew".equalsIgnoreCase(command) || "renewCard".equalsIgnoreCase(command)) {
                System.out.println("开始批量续费学生卡...");
                String error = TaskExecutor.executeTask(RenewStudentCard::start, "RenewStudentCard-Thread");
                if (error != null) continue;
            } else if ("G".equalsIgnoreCase(command) || "get".equalsIgnoreCase(command) || "getURL".equalsIgnoreCase(command)) {
                System.out.println("\n请输入要测试的 URL (或直接回车返回): ");
                in.nextLine();
                String url = in.nextLine().trim();
                String error = TaskExecutor.executeTask(() -> ApiTester.getFromUrlTest(url), "ApiTester-GET-Thread");
                if (error != null) continue;
            } else if ("P".equalsIgnoreCase(command) || "post".equalsIgnoreCase(command) || "postURL".equalsIgnoreCase(command)) {
                System.out.println("\n请输入要测试的 URL (或直接回车返回): ");
                in.nextLine();
                String url = in.nextLine().trim();

                if (!url.isEmpty()) {
                    System.out.println("\n请输入 POST 请求体 (JSON 格式，直接回车使用空字符串): ");
                    String requestBody = in.nextLine().trim();
                    String error = TaskExecutor.executeTask(() -> ApiTester.postToUrlTest(url, requestBody), "ApiTester-POST-Thread");
                    if (error != null) continue;
                }
            } else if ("GUI".equalsIgnoreCase(command) || "gui".equalsIgnoreCase(command)) {
                System.out.println("正在启动图形界面...");
                MainWindow.showMainWindow();
            } else if ("X".equalsIgnoreCase(command) || "exit".equals(command)) {
                break;
            } else if ("H".equalsIgnoreCase(command) || "help".equals(command)) {
                help();
            } else {
                System.out.println("无效的命令，请重新输入");
            }
        }
        in.close();
        exit();
    }

    private static void exit() {
        NetworkUtil.killEdgeDriver();
        System.out.println("Bye");
        System.exit(0);
    }

    public static void help() {
        System.out.println("===================================================================");
        System.out.println("此程序用于获得爱优读后台系统 (https://group.aiyoudu.cn) 的学生数据，并进行一系列操作");
        System.out.println("请输入要进行的操作:");
        System.out.println("【1】getToken             仅获取 Token（启动浏览器并登录）");
        System.out.println("【2】readStudents         读取学生信息并保存到内存（需先执行 1）");
        System.out.println("【3】collect[Profiles]    尝试登录，并收集所有信息，然后记录 [需先执行 2]");
        System.out.println("【4】feedback             将根据收集到的所有信息，交给 AI 生成一份点评 [需先执行 3]");
        System.out.println("【5】printStudy           批量打印学案（按月续费 -> 按学案设置分题型打印 -> 可选回收月卡）[需先执行 2，学生需已配置 config/study/*.json]");
        System.out.println("【6】renewCard            批量续费学生卡 [需先执行 2]");
        System.out.println("【G】getURL               测试 GET API（自动携带 Token）");
        System.out.println("【P】postURL              测试 POST API（自动携带 Token 和请求体）");
        System.out.println("【GUI】gui                打开图形界面窗口");
        System.out.println("     stop                 立即停止当前正在运行的任务");
        System.out.println("【H】help                 展示本帮助列表");
        System.out.println("【X】exit                 退出");
        System.out.println("===================================================================");
    }
}