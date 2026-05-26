package me.xpyex.software.feedback.packet.in;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class FinishedTaskPanel {
    private int studyWord;  //学习词汇
    private int checkWord;  //测试词汇
    private int increaseWord;  //增长词汇
    private int revieWord;  //复习词汇。原JSON就拼错了，我只能将错就错
    private List<SinglePanel> wordAndReadList = null;
    private List<SinglePanel> listeningAndList = null;

    public int getReviewWord() {
        return revieWord;
    }
}
