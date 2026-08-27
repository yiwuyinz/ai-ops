package com.aops.agent.client;

import com.aops.agent.TestProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LokiClientTest {

    @Test
    void formatsLogLinesFromQueryRange() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LokiClient client = new LokiClient(builder, TestProps.defaultProps(), new ObjectMapper());

        String response = """
                {"status":"success","data":{"resultType":"streams","result":[
                  {"stream":{"job":"demo-app"},"values":[
                    ["1718000000000000000","ERROR connection refused to db"]
                  ]}
                ]}}
                """;

        // Loki's HTTP API takes start/end in NANOSECONDS — regression guard:
        // sending ms silently moves the window to 1970 and returns nothing.
        long startNs = Instant.parse("2024-06-10T00:00:00Z").toEpochMilli() * 1_000_000L;
        long endNs = Instant.parse("2024-06-10T00:10:00Z").toEpochMilli() * 1_000_000L;
        server.expect(requestTo(containsString("/loki/api/v1/query_range")))
                .andExpect(queryParam("limit", "500"))
                .andExpect(queryParam("start", String.valueOf(startNs)))
                .andExpect(queryParam("end", String.valueOf(endNs)))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        String result = client.queryRange("{job=\"demo-app\"}",
                Instant.parse("2024-06-10T00:00:00Z"),
                Instant.parse("2024-06-10T00:10:00Z"),
                null);

        assertTrue(result.contains("ERROR connection refused to db"), result);
        assertTrue(result.contains("demo-app"), result);
        server.verify();
    }

    @Test
    void reportsEmptyResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LokiClient client = new LokiClient(builder, TestProps.defaultProps(), new ObjectMapper());

        server.expect(requestTo(containsString("/loki/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"streams","result":[]}}
                        """, MediaType.APPLICATION_JSON));

        String result = client.queryRange("{job=\"x\"}",
                Instant.parse("2024-06-10T00:00:00Z"),
                Instant.parse("2024-06-10T00:10:00Z"),
                null);

        assertTrue(result.contains("No log lines matched"), result);
        server.verify();
    }

    @Test
    void listsLabelsForDiscovery() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LokiClient client = new LokiClient(builder, TestProps.defaultProps(), new ObjectMapper());

        server.expect(requestTo(containsString("/loki/api/v1/labels")))
                .andRespond(withSuccess("""
                        {"status":"success","data":["job","pod","pod_name","namespace"]}
                        """, MediaType.APPLICATION_JSON));

        String result = client.labels(
                Instant.parse("2024-06-10T00:00:00Z"),
                Instant.parse("2024-06-10T00:10:00Z"));

        assertTrue(result.contains("pod_name"), result);
        server.verify();
    }

    @Test
    void listsLabelValuesForDiscovery() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LokiClient client = new LokiClient(builder, TestProps.defaultProps(), new ObjectMapper());

        server.expect(requestTo(containsString("/loki/api/v1/label/pod_name/values")))
                .andRespond(withSuccess("""
                        {"status":"success","data":["inventory-service-7c9d4f"]}
                        """, MediaType.APPLICATION_JSON));

        String result = client.labelValues("pod_name",
                Instant.parse("2024-06-10T00:00:00Z"),
                Instant.parse("2024-06-10T00:10:00Z"));

        assertTrue(result.contains("inventory-service-7c9d4f"), result);
        server.verify();
    }
}
