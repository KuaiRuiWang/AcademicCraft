package com.wkr.aicode.core.thesis;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import de.undercouch.citeproc.csl.CSLType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** 检索学术索引，使用注册记录补全和核验 DOI。 */
@Service
@Slf4j
public class ThesisLiteratureSearch {
    private static final String OPEN_ALEX_WORKS = "https://api.openalex.org/works";
    private static final String CROSSREF_WORKS = "https://api.crossref.org/works";
    private static final String OPEN_ALEX_FIELDS = "id,doi,title,authorships,publication_year,type,language,"
            + "primary_location,biblio,abstract_inverted_index";
    private static final int RESULTS_PER_QUERY = 5;
    private static final Pattern DOI = Pattern.compile("10\\.\\d{4,9}/[!-~]+");
    private static final Pattern OPEN_ALEX_ID = Pattern.compile("https://openalex\\.org/W\\d+");
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");

    private final RestClient client;
    private final String openAlexApiKey;

    @Autowired
    public ThesisLiteratureSearch(RestClient.Builder builder, @Value("${thesis.openalex-api-key:${OPENALEX_API_KEY:}}") String openAlexApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(4_000);
        requestFactory.setReadTimeout(5_000);
        this.client = builder.requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "AcademicCraft (thesis references)").build();
        this.openAlexApiKey = openAlexApiKey;
    }

    ThesisLiteratureSearch(RestClient client, String openAlexApiKey) {
        this.client = client;
        this.openAlexApiKey = openAlexApiKey;
    }

    public record Result(String status, List<ThesisReference> references) { }

    public Result search(List<String> queries) {
        Map<String, ThesisReference> verified = new LinkedHashMap<>();
        Set<String> checkedDois = new HashSet<>();
        boolean searched = false;
        for (String query : queries) {
            // 1. 用 OpenAlex 发现论文，带 DOI 的候选逐条向 Crossref 核对。
            JsonNode works = searchOpenAlex(query);
            searched |= works.isArray();
            int before = verified.size();
            for (JsonNode work : works) {
                String doi = normalizeDoi(work.path("doi").asText(""));
                ThesisReference reference = null;
                if (doi.isEmpty() && work.path("doi").asText("").isBlank()) {
                    reference = fromOpenAlex(work);
                } else if (!doi.isEmpty() && checkedDois.add(doi)) {
                    JsonNode registration = fetch(client.get().uri(CROSSREF_WORKS + "/{prefix}/{suffix}",
                            doi.substring(0, doi.indexOf('/')), doi.substring(doi.indexOf('/') + 1)), "Crossref").path("message");
                    if (doi.equals(normalizeDoi(registration.path("DOI").asText("")))) {
                        reference = fromCrossref(registration, work);
                    }
                }
                if (reference != null) verified.putIfAbsent(reference.id(), reference);
            }

            // 2. 该检索词未带来可用条目时，直接搜索 Crossref 的注册记录。
            if (verified.size() == before) {
                JsonNode items = fetch(client.get().uri(CROSSREF_WORKS
                        + "?query.bibliographic={query}&rows={rows}", query, RESULTS_PER_QUERY), "Crossref")
                        .path("message").path("items");
                searched |= items.isArray();
                for (JsonNode item : items) {
                    ThesisReference reference = fromCrossref(item, MissingNode.getInstance());
                    if (reference != null) verified.putIfAbsent(reference.id(), reference);
                }
            }
        }
        String status = !verified.isEmpty() ? "ready" : searched ? "empty" : "unavailable";
        return new Result(status, List.copyOf(verified.values()));
    }

    private JsonNode searchOpenAlex(String query) {
        var request = client.get().uri(OPEN_ALEX_WORKS
                + "?search={query}&filter=is_retracted:false&per_page={rows}&select={fields}",
                query, RESULTS_PER_QUERY, OPEN_ALEX_FIELDS);
        if (!openAlexApiKey.isBlank()) request.headers(headers -> headers.setBearerAuth(openAlexApiKey));
        return fetch(request, "OpenAlex").path("results");
    }

    private JsonNode fetch(RestClient.RequestHeadersSpec<?> request, String provider) {
        try {
            JsonNode body = request.retrieve().body(JsonNode.class);
            return body == null ? MissingNode.getInstance() : body;
        } catch (RestClientResponseException error) {
            log.info("论文文献请求未成功，数据源={}，状态={}", provider, error.getStatusCode().value());
        } catch (RestClientException error) {
            log.info("论文文献数据源不可用，数据源={}，类型={}", provider, error.getClass().getSimpleName());
        }
        return MissingNode.getInstance();
    }

    private ThesisReference fromCrossref(JsonNode record, JsonNode openAlex) {
        String doi = normalizeDoi(record.path("DOI").asText(""));
        String title = text(record.path("title").path(0));
        CSLType type = type(text(record.path("type")));
        int year = record.path("published").path("date-parts").path(0).path(0).asInt();
        if (year == 0) year = record.path("issued").path("date-parts").path(0).path(0).asInt();
        List<ThesisReference.Author> authors = new ArrayList<>();
        for (JsonNode author : record.path("author")) {
            String family = text(author.path("family"));
            String name = text(author.path("name"));
            if (!family.isEmpty() || !name.isEmpty()) {
                authors.add(new ThesisReference.Author(text(author.path("given")), family, name));
            }
        }
        if (!complete(title, year, authors) || doi.isEmpty() || type == null) return null;

        String abstractText = text(record.path("abstract"));
        if (abstractText.isEmpty() && samePublication(record, openAlex, title, year, authors.getFirst())) {
            abstractText = openAlexAbstract(openAlex);
        }
        return new ThesisReference(id(doi), title, List.copyOf(authors), year, type,
                text(record.path("container-title").path(0)), text(record.path("publisher")), text(record.path("volume")),
                text(record.path("issue")), firstText(record.path("page"), record.path("article-number")),
                language(title, firstText(record.path("language"), openAlex.path("language"))), doi,
                "https://doi.org/" + UriUtils.encodePath(doi, StandardCharsets.UTF_8), abstractText);
    }

    private ThesisReference fromOpenAlex(JsonNode work) {
        String url = text(work.path("id"));
        String title = text(work.path("title"));
        CSLType type = type(text(work.path("type")));
        int year = work.path("publication_year").asInt();
        List<ThesisReference.Author> authors = new ArrayList<>();
        for (JsonNode authorship : work.path("authorships")) {
            String name = text(authorship.path("author").path("display_name"));
            if (!name.isEmpty()) authors.add(new ThesisReference.Author("", "", name));
        }
        if (!complete(title, year, authors) || !OPEN_ALEX_ID.matcher(url).matches() || type == null) return null;
        JsonNode source = work.path("primary_location").path("source");
        JsonNode biblio = work.path("biblio");
        String firstPage = text(biblio.path("first_page"));
        String lastPage = text(biblio.path("last_page"));
        String pages = firstPage;
        if (!firstPage.isEmpty() && !lastPage.isEmpty() && !firstPage.equals(lastPage)) {
            pages = firstPage + "-" + lastPage;
        }
        return new ThesisReference(id(url), title, List.copyOf(authors), year, type,
                text(source.path("display_name")), text(source.path("host_organization_name")),
                text(biblio.path("volume")), text(biblio.path("issue")), pages,
                language(title, text(work.path("language"))), "", url, openAlexAbstract(work));
    }

    private boolean samePublication(JsonNode crossref, JsonNode openAlex, String title, int year,
                                    ThesisReference.Author author) {
        String registeredTitle = comparable(title);
        String indexedTitle = comparable(text(openAlex.path("title")));
        String subtitle = comparable(text(crossref.path("subtitle").path(0)));
        String family = comparable(author.family().isEmpty() ? author.literal() : author.family());
        String indexedAuthor = comparable(text(openAlex.path("authorships").path(0).path("author").path("display_name")));
        return (indexedTitle.equals(registeredTitle) || indexedTitle.equals(registeredTitle + subtitle))
                && Math.abs(openAlex.path("publication_year").asInt() - year) <= 1
                && !family.isEmpty() && indexedAuthor.contains(family);
    }

    private String openAlexAbstract(JsonNode work) {
        Map<Integer, String> words = new TreeMap<>();
        work.path("abstract_inverted_index").fields().forEachRemaining(entry -> {
            for (JsonNode position : entry.getValue()) {
                int index = position.asInt(-1);
                if (index >= 0 && index < 250) words.put(index, entry.getKey());
            }
        });
        return limit(String.join(" ", words.values()));
    }

    private static boolean complete(String title, int year, List<ThesisReference.Author> authors) {
        return !title.isEmpty() && year >= 1000 && year <= 2999 && !authors.isEmpty();
    }

    private static String id(String identifier) {
        return "ref-" + DigestUtil.sha256Hex(identifier).substring(0, 16);
    }

    private static String normalizeDoi(String value) {
        String doi = value.strip().toLowerCase(Locale.ROOT).replaceFirst("^https?://(?:dx\\.)?doi\\.org/", "");
        return DOI.matcher(doi).matches() ? doi : "";
    }

    private static CSLType type(String value) {
        return switch (value) {
            case "journal-article", "article", "review" -> CSLType.ARTICLE_JOURNAL;
            case "proceedings-article", "conference-paper" -> CSLType.PAPER_CONFERENCE;
            case "book", "monograph", "edited-book" -> CSLType.BOOK;
            case "book-chapter", "book-section" -> CSLType.CHAPTER;
            case "dissertation" -> CSLType.THESIS;
            case "report" -> CSLType.REPORT;
            case "posted-content", "preprint" -> CSLType.ARTICLE;
            default -> null;
        };
    }

    private static String language(String title, String language) {
        return language.isBlank() ? (HAN.matcher(title).find() ? "zh" : "en") : language;
    }

    private static String comparable(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String firstText(JsonNode first, JsonNode second) {
        String value = text(first);
        return value.isEmpty() ? text(second) : value;
    }

    private static String text(JsonNode value) {
        return limit(value.asText("").replaceAll("<[^>]*>", " ").replaceAll("[\\s\\p{Cntrl}]+", " ").strip());
    }

    private static String limit(String value) {
        return value.substring(0, Math.min(value.length(), 1600));
    }
}
