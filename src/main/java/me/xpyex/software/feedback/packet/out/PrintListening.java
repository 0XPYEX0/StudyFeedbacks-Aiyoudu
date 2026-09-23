package me.xpyex.software.feedback.packet.out;

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
}
