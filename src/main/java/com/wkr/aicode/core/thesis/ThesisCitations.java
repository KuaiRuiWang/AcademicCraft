package com.wkr.aicode.core.thesis;

import com.wkr.aicode.model.vo.app.ThesisVO;
import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.ListItemDataProvider;
import de.undercouch.citeproc.csl.CSLDateBuilder;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLNameBuilder;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 以正文首次引用顺序为准，统一生成预览和导出所用的文献表。 */
public final class ThesisCitations {
    private static final String STYLE = "china-national-standard-gb-t-7714-2025-numeric";
    private static final Pattern CITATION = Pattern.compile("\\[@([A-Za-z0-9][A-Za-z0-9_.:-]{0,63})]");
    private static final Parser MARKDOWN = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();

    private ThesisCitations() { }

    public record Snapshot(String sourceVersion, String status, List<ThesisReference> references) { }

    private record Marker(String id, int start, int end) { }

    public static List<String> ids(String markdown) {
        return markers(markdown).stream().map(Marker::id).toList();
    }

    public static String replace(String markdown, Function<String, String> replacement) {
        List<Marker> markers = markers(markdown);
        if (markers.isEmpty()) return markdown;
        StringBuilder result = new StringBuilder(markdown.length());
        int previousEnd = 0;
        for (Marker marker : markers) {
            result.append(markdown, previousEnd, marker.start());
            result.append(replacement.apply(marker.id()));
            previousEnd = marker.end();
        }
        return result.append(markdown, previousEnd, markdown.length()).toString();
    }

    private static List<Marker> markers(String markdown) {
        List<Marker> markers = new ArrayList<>();
        if (markdown == null || !markdown.contains("[@")) return markers;
        MARKDOWN.parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                for (SourceSpan span : text.getSourceSpans()) {
                    int start = span.getInputIndex();
                    Matcher matcher = CITATION.matcher(markdown).region(start, start + span.getLength());
                    while (matcher.find()) {
                        if (!escaped(markdown, matcher.start())) {
                            markers.add(new Marker(matcher.group(1), matcher.start(), matcher.end()));
                        }
                    }
                }
            }

            // 链接文字和图片描述不参与参考文献编号。
            @Override
            public void visit(Link link) { }

            @Override
            public void visit(Image image) { }
        });
        return markers;
    }

    private static boolean escaped(String markdown, int start) {
        int backslashes = 0;
        for (int i = start - 1; i >= 0 && markdown.charAt(i) == '\\'; i--) backslashes++;
        return backslashes % 2 == 1;
    }

    public static List<ThesisVO.Reference> bibliography(List<String> chapterContents, List<ThesisReference> candidates) {
        // 1. 按正文中第一次出现的位置收集已核验文献，重复出现沿用同一编号。
        Map<String, ThesisReference> candidatesById = new LinkedHashMap<>();
        for (ThesisReference candidate : candidates) candidatesById.put(candidate.id(), candidate);
        Map<String, ThesisReference> cited = new LinkedHashMap<>();
        for (String content : chapterContents) {
            for (String id : ids(content)) {
                ThesisReference source = candidatesById.get(id);
                if (source != null) cited.putIfAbsent(id, source);
            }
        }
        if (cited.isEmpty()) return List.of();

        // 2. 用 CSL 排版文献表，正文上标与条目共用这一顺序。
        CSLItemData[] data = cited.values().stream().map(ThesisCitations::cslItem).toArray(CSLItemData[]::new);
        try {
            CSL formatter = new CSL(new ListItemDataProvider(data), STYLE);
            formatter.setOutputFormat("text");
            formatter.registerCitationItems(cited.keySet().toArray(String[]::new));
            String[] entries = formatter.makeBibliography().getEntries();
            List<ThesisVO.Reference> references = new ArrayList<>();
            int index = 0;
            for (ThesisReference source : cited.values()) {
                String text = entries[index].strip().replaceFirst("^\\[\\d+]\\s*", "");
                references.add(new ThesisVO.Reference(source.id(), index + 1, text, source.url()));
                index++;
            }
            return references;
        } catch (IOException error) {
            throw new IllegalStateException("无法加载论文参考文献样式", error);
        }
    }

    private static CSLItemData cslItem(ThesisReference reference) {
        CSLName[] authors = reference.authors().stream().map(author -> {
            CSLNameBuilder name = new CSLNameBuilder();
            if (author.literal().isEmpty()) return name.family(author.family()).given(author.given()).build();
            return name.literal(author.literal()).build();
        }).toArray(CSLName[]::new);
        CSLItemDataBuilder item = new CSLItemDataBuilder().id(reference.id()).type(reference.type())
                .title(reference.title()).language(reference.language()).author(authors)
                .issued(new CSLDateBuilder().dateParts(reference.year()).build());
        if (!reference.containerTitle().isEmpty()) item.containerTitle(reference.containerTitle());
        if (!reference.publisher().isEmpty()) item.publisher(reference.publisher());
        if (!reference.volume().isEmpty()) item.volume(reference.volume());
        if (!reference.issue().isEmpty()) item.issue(reference.issue());
        if (!reference.page().isEmpty()) item.page(reference.page());
        if (!reference.doi().isEmpty()) item.DOI(reference.doi());
        return item.build();
    }
}
