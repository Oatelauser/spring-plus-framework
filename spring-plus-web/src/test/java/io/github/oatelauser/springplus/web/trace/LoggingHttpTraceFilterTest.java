package io.github.oatelauser.springplus.web.trace;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E1 回归：@RecordHttp 接口用 PathPattern 匹配真实请求路径，
 * 带 PathVariable 的接口（/user/{id} vs /user/42）不再漏记录；兼容 context-path。
 */
class LoggingHttpTraceFilterTest {

    static class Demo {
        public void handler() {
        }
    }

    private static LoggingHttpTraceFilter filterWith(String pattern, RequestMethod method) throws Exception {
        LoggingHttpTraceFilter filter = new LoggingHttpTraceFilter();
        RequestMappingInfo info = RequestMappingInfo.paths(pattern).methods(method).build();
        filter.handleMethod(Set.of(), info, new HandlerMethod(new Demo(), Demo.class.getMethod("handler")));
        return filter;
    }

    @Test
    void matchesPathVariableEndpoint() throws Exception {
        LoggingHttpTraceFilter filter = filterWith("/user/{id}", RequestMethod.GET);
        assertTrue(filter.shouldRecordBody(new MockHttpServletRequest("GET", "/user/42")));
    }

    @Test
    void methodMismatchDoesNotMatch() throws Exception {
        LoggingHttpTraceFilter filter = filterWith("/user/{id}", RequestMethod.GET);
        assertFalse(filter.shouldRecordBody(new MockHttpServletRequest("POST", "/user/42")));
    }

    @Test
    void unrelatedPathDoesNotMatch() throws Exception {
        LoggingHttpTraceFilter filter = filterWith("/user/{id}", RequestMethod.GET);
        assertFalse(filter.shouldRecordBody(new MockHttpServletRequest("GET", "/orders")));
    }

    @Test
    void contextPathIsStripped() throws Exception {
        LoggingHttpTraceFilter filter = filterWith("/user/{id}", RequestMethod.GET);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/42");
        request.setContextPath("/api");
        assertTrue(filter.shouldRecordBody(request));
    }

}
