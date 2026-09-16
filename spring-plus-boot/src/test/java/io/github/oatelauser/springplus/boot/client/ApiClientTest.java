package io.github.oatelauser.springplus.boot.client;

import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 手工冒烟测试：需要本地起 9000/9001 服务人工观察输出，无任何断言。
 * 模块接入 junit-jupiter 引擎后若不禁用会导致 CI 误报失败。
 */
@Disabled("手工冒烟测试：依赖本地 9000/9001 环境，请人工执行观察输出")
class ApiClientTest {

    @Test
    void get() throws Exception {
        ApiClientSettings properties = new ApiClientSettings();
        properties.setBaseUrl("http://localhost:9000");
        ApiClientSettings.Logging loggingProperties = new ApiClientSettings.Logging();
        loggingProperties.setEnabled(true);
        loggingProperties.setLevel(ApiClientSettings.Logging.LogLevel.BODY);
        properties.setLogging(loggingProperties);
        ApiResponse<String> response = ApiClient.create(properties)
                .get("http://localhost:9001/test", String.class);
        System.out.println(response.hasException());
        System.out.println(response.isSuccessful());
    }

}
