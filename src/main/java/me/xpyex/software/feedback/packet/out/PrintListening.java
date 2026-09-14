package me.xpyex.software.feedback.packet.out;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.packet.both.AYDPacket;
import me.xpyex.software.feedback.util.AiyouduUtil;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class PrintListening extends AYDPacket {
    public static final String queryListeningUrl = AiyouduUtil.apiUrl + "student/listen/listenQuestionPrint";
    private int isScanCode = 1;  // 是否可以使用二维码扫描反馈，默认可以
    private String studentId;  // 它后台这里又用了String
    private int shortNum = 6;
    private int longNum = 8;

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor(staticName = "of")
    public static class Merge {
        public static final String url = "https://group.aiyoudu.cn/api2/student/study/printMergePdf";
        private List<String> urlPdfList = new ArrayList<>();

        public Merge add(String link) {
            urlPdfList.add(link);
            return this;
        }

        public Merge addAll(Collection<String> links) {
            urlPdfList.addAll(links);
            return this;
        }
    }
}
