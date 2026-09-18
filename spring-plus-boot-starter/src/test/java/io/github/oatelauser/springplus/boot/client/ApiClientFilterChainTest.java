package io.github.oatelauser.springplus.boot.client;

import io.github.oatelauser.springplus.boot.client.metrics.ApiClientMetrics;
import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * P0-1 回归：同 order 的过滤器不再被 Set 去重静默丢弃；
 * P2 指标回归：每次调用都经过 {@code metrics.record}。
 */
class ApiClientFilterChainTest {

    @Test
    void sameOrderFiltersAreNotDropped() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/ping")).andRespond(withSuccess("pong", MediaType.TEXT_PLAIN));
        ApiClient client = ApiClient.create(builder.build());

        List<String> executed = new ArrayList<>();
        client.addFilter((request, chain) -> {
            executed.add("first");
            return chain.doFilter(request);
        });
        client.addFilter((request, chain) -> {
            executed.add("second");
            return chain.doFilter(request);
        });

        ApiResponse<String> response = client.execute(ApiRequest.get("/ping").build(), String.class);

        assertTrue(response.isSuccessful());
        assertEquals(List.of("first", "second"), executed);
        server.verify();
    }

    @Test
    void metricsRecordsEveryCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/ping")).andRespond(withSuccess("pong", MediaType.TEXT_PLAIN));
        ApiClient client = ApiClient.create(builder.build());

        AtomicInteger recorded = new AtomicInteger();
        client.setMetrics(new ApiClientMetrics() {
            @Override
            public void record(ApiRequest request, ApiResponse<?> response) {
                if (request != null && response != null) {
                    recorded.incrementAndGet();
                }
            }
        });

        client.execute(ApiRequest.get("/ping").build(), String.class);

        assertEquals(1, recorded.get());
    }

}
