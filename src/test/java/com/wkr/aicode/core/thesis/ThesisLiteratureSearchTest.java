package com.wkr.aicode.core.thesis;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ThesisLiteratureSearchTest {
    @Test
    void verifiesDoiAndDeduplicatesTwoIndexRecords() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
                    assertEquals("api.openalex.org", request.getURI().getHost());
                    assertTrue(request.getURI().getQuery().contains("search=分布式 数据处理"));
                })
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andRespond(withSuccess("""
                        {"results":[
                           {"id":"https://openalex.org/W2173213060","doi":"https://doi.org/10.1145/1327452.1327492",
                            "title":"MapReduce","publication_year":2008,"language":"en","type":"article",
                            "authorships":[{"author":{"display_name":"Jeffrey Dean"}}],
                            "abstract_inverted_index":{"data":[1],"distributed":[0]}},
                           {"id":"https://openalex.org/W2173213061","doi":"10.1145/1327452.1327492"},
                           {"id":"https://openalex.org/W456","doi":null,"title":"分布式系统研究","publication_year":2023,"type":"article",
                            "authorships":[{"author":{"display_name":"李四"}}],"primary_location":{"source":{"display_name":"软件研究"}}}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.crossref.org/works/10.1145/1327452.1327492"))
                .andRespond(withSuccess("""
                        {"message":{"DOI":"10.1145/1327452.1327492","title":["MapReduce"],"published":{"date-parts":[[2008]]},
                          "author":[{"family":"Dean","given":"Jeffrey"}],"type":"journal-article",
                          "container-title":["Communications of the ACM"],"volume":"51","issue":"1","page":"107-113"}}
                        """, MediaType.APPLICATION_JSON));

        var search = new ThesisLiteratureSearch(builder.build(), "test-key");
        var result = search.search(List.of("分布式 数据处理"));
        server.verify();
        assertEquals("ready", result.status());
        assertEquals(2, result.references().size());
        assertEquals("distributed data", result.references().getFirst().abstractText());
        assertEquals("https://doi.org/10.1145/1327452.1327492", result.references().getFirst().url());
        assertEquals("https://openalex.org/W456", result.references().get(1).url());
    }

    @Test
    void searchesCrossrefWhenOpenAlexIsUnavailableAndSkipsIncompleteMetadata() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertEquals("api.openalex.org", request.getURI().getHost()))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(request -> {
                    assertEquals("api.crossref.org", request.getURI().getHost());
                    assertTrue(request.getURI().getQuery().contains("query.bibliographic=MapReduce"));
                })
                .andRespond(withSuccess("""
                        {"message":{"items":[
                          {"DOI":"10.1145/1327452.1327492","title":["MapReduce"],"issued":{"date-parts":[[2008]]},
                           "author":[{"family":"Dean","given":"Jeffrey"}],"type":"journal-article"},
                          {"DOI":"10.9999/incomplete","title":["Incomplete"]}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        var result = new ThesisLiteratureSearch(builder.build(), "").search(List.of("MapReduce"));
        server.verify();
        assertEquals("ready", result.status());
        assertEquals(List.of("MapReduce"), result.references().stream().map(ThesisReference::title).toList());
    }
}
