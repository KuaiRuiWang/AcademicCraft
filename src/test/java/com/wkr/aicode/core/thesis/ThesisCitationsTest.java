package com.wkr.aicode.core.thesis;

import com.wkr.aicode.model.entity.AppThesisChapter;
import com.wkr.aicode.model.vo.app.ThesisVO;
import de.undercouch.citeproc.csl.CSLType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThesisCitationsTest {
    @Test
    void formatsMixedLiteratureInProseOrderAndAssemblesWordSource() {
        var journal = new ThesisReference("ref-a", "图书管理研究",
                List.of(new ThesisReference.Author("三", "张", "")), 2024, CSLType.ARTICLE_JOURNAL,
                "图书馆研究", "", "12", "2", "50-55", "zh", "", "https://openalex.org/W123", "");
        var conference = new ThesisReference("ref-b", "An example conference paper",
                List.of(new ThesisReference.Author("Alice", "Smith", "")), 2021, CSLType.PAPER_CONFERENCE,
                "Example Proceedings", "ACM", "", "", "10-17", "en", "", "https://openalex.org/W456", "");
        String chapter = "代码 `[@ref-a]` 和转义 \\[@ref-a]。\r\n\r\n~~~text\r\n[@ref-a]\r\n~~~\r\n\r\n"
                + "已有链接 [@ref-a](https://example.com)。正文[@ref-b]，相同来源[@ref-b]。\r\n"
                + "另一项研究[@ref-a]。未核验[@missing]。";

        assertEquals(List.of("ref-b", "ref-b", "ref-a", "missing"), ThesisCitations.ids(chapter));
        var references = ThesisCitations.bibliography(List.of(chapter), List.of(journal, conference));
        assertEquals(List.of("ref-b", "ref-a"), references.stream().map(ThesisVO.Reference::id).toList());
        assertEquals("https://openalex.org/W456", references.getFirst().url());
        assertTrue(references.getFirst().text().contains("An example conference paper[C]"), references.toString());
        assertTrue(references.get(1).text().contains("图书管理研究[J]"), references.toString());
        assertTrue(references.get(1).text().contains("张三"), references.toString());

        AppThesisChapter introduction = new AppThesisChapter();
        introduction.setTitle("第一章 绪论");
        introduction.setContent(chapter);
        ThesisVO thesis = new ThesisVO();
        thesis.setTitle("论文题目");
        thesis.setChapters(List.of(introduction));
        thesis.setLiteratureStatus("ready");
        thesis.setReferences(references);
        String markdown = new ThesisExportService().markdown(thesis);
        assertTrue(markdown.contains("正文<sup data-thesis-citation>[1]</sup>，相同来源<sup data-thesis-citation>[1]</sup>"));
        assertTrue(markdown.contains("另一项研究<sup data-thesis-citation>[2]</sup>"));
        assertTrue(markdown.contains("代码 `[@ref-a]`"));
        assertTrue(markdown.contains("## 参考文献\n\n[1] "));
        assertTrue(markdown.contains("https://openalex.org/W456"));
    }
}
