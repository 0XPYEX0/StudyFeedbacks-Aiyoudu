package me.xpyex.software.feedback.packet.in;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
// 学习内容的详细信息，包括文章ID、难度等
public class StudyContentInfo {
    private int articleId;  //文章ID
    private int articleLength;  //文章长度
    private int difficulty;  //文章难度
    private String printStudyUrl = "";  //学案下载链接，不一定存在，有时没有生成纸质格式
    private Integer printId;  // 打印的学案ID，不一定存在，有时没有生成纸质格式
}
