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
// 打印学案
public class PrintStudy extends AYDPacket {
    public static final String url = AiyouduUtil.apiUrl + "student/study/printSaveStudy";
    private int printNum = 1;  //打印份数，就是生成新学案的旁边那个
    private int articleNum = 2;  //单份有几篇
    private int printType = 1;  //打印的类型，根据StudyType来
    private int studentId;

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor(staticName = "of")
    public static class PrintMerge extends AYDPacket {
        public static final String url = AiyouduUtil.apiUrl + "student/study/printMerge";
        private final List<Integer> printIds = new ArrayList<>();

        public PrintMerge add(int id) {
            printIds.add(id);
            return this;
        }

        public PrintMerge addAll(Collection<Integer> collection) {
            printIds.addAll(collection);
            return this;
        }
    }
}
