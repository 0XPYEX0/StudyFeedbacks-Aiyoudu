package me.xpyex.software.feedback.packet.out;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class MergePdf {
    public static final String url = "https://group.aiyoudu.cn/api2/student/study/printMergePdf";
    private List<String> urlPdfList = new ArrayList<>();

    public MergePdf add(String link) {
        urlPdfList.add(link);
        return this;
    }

    public MergePdf addAll(Collection<String> links) {
        urlPdfList.addAll(links);
        return this;
    }
}
