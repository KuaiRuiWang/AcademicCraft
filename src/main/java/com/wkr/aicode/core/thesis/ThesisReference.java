package com.wkr.aicode.core.thesis;

import de.undercouch.citeproc.csl.CSLType;

import java.util.List;

/** 已核验且带可访问来源地址的文献元数据。 */
public record ThesisReference(String id, String title, List<Author> authors, int year, CSLType type,
                              String containerTitle, String publisher, String volume, String issue,
                              String page, String language, String doi, String url, String abstractText) {
    public record Author(String given, String family, String literal) { }
}
