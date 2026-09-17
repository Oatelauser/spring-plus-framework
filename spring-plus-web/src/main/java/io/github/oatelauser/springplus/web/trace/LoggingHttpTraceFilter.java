package io.github.oatelauser.springplus.web.trace;

import io.github.oatelauser.springplus.web.utils.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPattern;

import java.util.HashSet;
import java.util.Set;

import static io.github.oatelauser.springplus.web.utils.AnnotationUtils.findMergedMethodAnnotation;

/**
 * 基于日志输出的 HTTP 请求/响应记录器。
 * <p>
 * 默认记录所有状态码的请求，可通过 {@link #setExcludedStatusCodes} 排除特定状态码。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-24
 * @since 1.0
 */
@Slf4j
public class LoggingHttpTraceFilter extends AbstractHttpTraceFilter implements HandlerMethodProcessor {

    private static final String REQUEST_LOG_TEMPLATE = """
            ============================= HTTP Request  =============================
            URI         : {} {}
            Status      : {}
            Query       : {}
            Content-Type: {}
            Remote      : {}
            Request Body:
            {}
            ============================= HTTP Response =============================
            Response Body:
            {}
            """;

    /**
     * 需要排除（不记录）的状态码
     */
    @Setter
    private Set<Integer> excludedStatusCodes = Set.of();
    private final Set<HandleInfo> handleInfos = new HashSet<>();

    @Override
    protected boolean shouldRecordBody(HttpServletRequest request) {
        // 用 PathPattern 匹配真实路径：模式串（/user/{id}）与实际路径（/user/42）
        // 用 equals 永远不相等，带 PathVariable 的接口会全部漏记录
        String lookupPath = lookupPath(request);
        String method = request.getMethod().toUpperCase();
        return handleInfos.stream()
                .anyMatch(info -> info.method().equals(method)
                        && info.pattern().matches(PathContainer.parsePath(lookupPath)));
    }

    @Override
    protected boolean isCandidateStatus(int status) {
        return !excludedStatusCodes.contains(status);
    }

    @Override
    protected void recordBody(HttpServletRequest request, HttpServletResponse response,
            String requestPayload, String responsePayload) {
        // V10/CWE-532：旁录 payload 掩敏（password/token/phone 等）
        log.debug(REQUEST_LOG_TEMPLATE,
                request.getMethod(), request.getRequestURI(),
                response.getStatus(),
                request.getQueryString(),
                request.getContentType(),
                request.getRemoteAddr(),
                LogSanitizer.maskSensitiveValues(requestPayload),
                LogSanitizer.maskSensitiveValues(responsePayload));
    }

    @Override
    public boolean supports(Set<String> urls, HandlerMethod handlerMethod) {
        RecordHttp annotation = findMergedMethodAnnotation(handlerMethod.getMethod(),
                RecordHttp.class, handlerMethod.getBeanType());
        return annotation != null;
    }

    @Override
    public void handleMethod(Set<String> urls, RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
        var patternsCondition = requestMappingInfo.getPathPatternsCondition();
        if (patternsCondition == null) {
            return;
        }
        for (RequestMethod method : requestMappingInfo.getMethodsCondition().getMethods()) {
            for (PathPattern pattern : patternsCondition.getPatterns()) {
                handleInfos.add(new HandleInfo(pattern, method.name()));
            }
        }
    }

    /**
     * 取 servlet 相对、已解码的请求路径（兼容 context-path 与 "/"、"/*" 两种 servlet 映射）
     */
    private static String lookupPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (request.getPathInfo() != null) {
            path = path + request.getPathInfo();
        }
        if (!path.isEmpty()) {
            return path;
        }
        // servlet 映射为 "/*" 时 servletPath 为空，退化为 URI 去掉 context-path 前缀
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath.isEmpty() || !uri.startsWith(contextPath)) {
            return uri;
        }
        return uri.substring(contextPath.length());
    }

    private record HandleInfo(PathPattern pattern, String method) {
    }

}
