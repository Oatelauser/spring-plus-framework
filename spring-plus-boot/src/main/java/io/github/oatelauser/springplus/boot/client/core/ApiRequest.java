package io.github.oatelauser.springplus.boot.client.core;

import io.github.oatelauser.springplus.boot.client.interceptor.ClientFilter;
import lombok.Getter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * 通用 HTTP 请求对象
 * <p>
 * 支持五种 Body 类型：
 * <ul>
 *   <li><b>json</b>  — 任意对象，序列化为 JSON</li>
 *   <li><b>form</b>  — application/x-www-form-urlencoded</li>
 *   <li><b>multipart</b> — multipart/form-data（文件上传）</li>
 *   <li><b>raw</b>   — byte[] / String，自定义 Content-Type</li>
 *   <li><b>empty</b> — 无请求体（GET / DELETE）</li>
 * </ul>
 *
 * <pre>
 * // JSON
 * ApiRequest.post("/users").json(user).build();
 *
 * // Form
 * ApiRequest.post("/login")
 *     .form(f -> f.add("user", "admin").add("pass", "123"))
 *     .build();
 *
 * // 文件上传
 * ApiRequest.post("/upload")
 *     .multipart(mp -> mp
 *         .file("file", new File("test.pdf"))
 *         .field("desc", "report")
 *     )
 *     .build();
 *
 * // 复杂查询
 * ApiRequest.get("/users/{id}/orders")
 *     .uriVar("id", 42)
 *     .queryParam("status", "paid")
 *     .queryParam("page", 1)
 *     .header("X-Trace-Id", traceId)
 *     .build();
 * </pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
@Getter
public class ApiRequest {

    /**
     * 请求唯一ID
     */
    private final String requestId;

    private final HttpMethod method;
    private final String uri;
    private final Object body;
    private final MediaType contentType;
    private final MediaType accept;
    private final Map<String, String> headers;
    private final Map<String, Object> queryParams;
    private final Map<String, Object> uriVariables;

    /**
     * 请求级过滤器（仅本次请求生效）
     */
    private final List<ClientFilter> filters;

    private ApiRequest(Builder builder) {
        this.uri = builder.uri;
        this.body = builder.body;
        this.method = builder.method;
        this.accept = builder.accept;
        this.contentType = builder.contentType;
        this.headers = Map.copyOf(builder.headers);
        this.queryParams = Map.copyOf(builder.queryParams);
        this.uriVariables = Map.copyOf(builder.uriVariables);
        this.requestId = StringUtils.hasText(builder.requestId) ?
                builder.requestId : UUID.randomUUID().toString();
        this.filters = List.copyOf(builder.filters);
    }

    public boolean hasBody() {return body != null;}

    public boolean hasUriVars() {return !uriVariables.isEmpty();}

    public boolean hasQueryParams() {return !queryParams.isEmpty();}

    /**
     * 创建一个预填充当前值的 Builder，用于修改后生成新实例
     */
    public Builder toBuilder() {
        Builder b = new Builder(this.method, this.uri);
        b.requestId = this.requestId;
        b.body = this.body;
        b.contentType = this.contentType;
        b.accept = this.accept;
        b.headers.putAll(this.headers);
        b.queryParams.putAll(this.queryParams);
        b.uriVariables.putAll(this.uriVariables);
        return b;
    }

    // ============ 快捷工厂 ============

    public static Builder get(String uri) {return new Builder(HttpMethod.GET, uri);}

    public static Builder post(String uri) {return new Builder(HttpMethod.POST, uri);}

    public static Builder put(String uri) {return new Builder(HttpMethod.PUT, uri);}

    public static Builder patch(String uri) {return new Builder(HttpMethod.PATCH, uri);}

    public static Builder delete(String uri) {return new Builder(HttpMethod.DELETE, uri);}

    public static Builder head(String uri) {return new Builder(HttpMethod.HEAD, uri);}

    public static Builder method(HttpMethod method, String uri) {
        return new Builder(method, uri);
    }

    // ============ Builder ============

    public static class Builder {
        private String requestId;
        private final HttpMethod method;
        private final String uri;
        private Object body;
        private MediaType contentType;
        private MediaType accept = MediaType.APPLICATION_JSON;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, Object> queryParams = new LinkedHashMap<>();
        private final Map<String, Object> uriVariables = new LinkedHashMap<>();
        private final List<ClientFilter> filters = new ArrayList<>();

        private Builder(HttpMethod method, String uri) {
            this.method = Objects.requireNonNull(method, "method must not be null");
            this.uri = Objects.requireNonNull(uri, "uri must not be null");
        }

        // ============ Body 类型方法（互斥，最后调用的生效） ============

        /**
         * JSON Body —— 对象自动序列化为 JSON
         */
        public Builder json(Object body) {
            this.body = body;
            this.contentType = MediaType.APPLICATION_JSON;
            return this;
        }

        /**
         * Form 表单 —— application/x-www-form-urlencoded
         */
        public Builder form(Consumer<FormBody> consumer) {
            FormBody formBody = new FormBody();
            consumer.accept(formBody);
            this.body = formBody.getData();
            this.contentType = MediaType.APPLICATION_FORM_URLENCODED;
            return this;
        }

        /**
         * Form 表单 —— 直接传 Map
         */
        public Builder form(Map<String, String> formData) {
            FormBody formBody = new FormBody();
            formBody.addAll(formData);
            this.body = formBody.getData();
            this.contentType = MediaType.APPLICATION_FORM_URLENCODED;
            return this;
        }

        /**
         * Multipart —— multipart/form-data（文件上传）
         */
        public Builder multipart(Consumer<MultipartBody> consumer) {
            MultipartBody mp = new MultipartBody();
            consumer.accept(mp);
            this.body = mp.getData();
            this.contentType = MediaType.MULTIPART_FORM_DATA;
            return this;
        }

        /**
         * 原始字节
         */
        public Builder raw(byte[] data, MediaType mediaType) {
            this.body = data;
            this.contentType = mediaType;
            return this;
        }

        /**
         * 原始字符串
         */
        public Builder raw(String data, MediaType mediaType) {
            this.body = data;
            this.contentType = mediaType;
            return this;
        }

        /**
         * XML Body
         */
        public Builder xml(Object body) {
            this.body = body;
            this.contentType = MediaType.APPLICATION_XML;
            return this;
        }

        // ============ 请求元数据 ============

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        /**
         * 添加 Bearer Token
         */
        public Builder bearerAuth(String token) {
            this.headers.put("Authorization", "Bearer " + token);
            return this;
        }

        /**
         * 添加 Basic Auth
         */
        public Builder basicAuth(String username, String password) {
            String encoded = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes());
            this.headers.put("Authorization", "Basic " + encoded);
            return this;
        }

        public Builder queryParam(String name, Object value) {
            this.queryParams.put(name, value);
            return this;
        }

        public Builder queryParams(Map<String, Object> params) {
            this.queryParams.putAll(params);
            return this;
        }

        public Builder uriVar(String name, Object value) {
            this.uriVariables.put(name, value);
            return this;
        }

        public Builder uriVars(Map<String, Object> vars) {
            this.uriVariables.putAll(vars);
            return this;
        }

        public Builder accept(MediaType accept) {
            this.accept = accept;
            return this;
        }

        public Builder contentType(MediaType contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        // ===== 请求级过滤器 =====

        /**
         * 添加仅本次请求生效的过滤器
         *
         * <pre>
         * // 示例：支付接口额外加签
         * apiClient.execute(
         *     ApiRequest.post("/payment/create")
         *         .json(payReq)
         *         .filter(new SignatureFilter(merchantKey))
         *         .filter(new IdempotentFilter())
         *         .build(),
         *     PayResult.class
         * );
         * </pre>
         */
        public Builder filter(ClientFilter filter) {
            this.filters.add(Objects.requireNonNull(filter));
            return this;
        }

        /**
         * 添加多个请求级过滤器
         */
        public Builder filters(ClientFilter... filters) {
            Collections.addAll(this.filters, filters);
            return this;
        }

        public Builder clearFilters() {
            this.filters.clear();
            return this;
        }

        public ApiRequest build() {
            return new ApiRequest(this);
        }
    }

    /**
     * 表单数据构建器 (application/x-www-form-urlencoded)
     *
     * <pre>
     * ApiRequest.post("/login")
     *     .form(form -> form
     *         .add("username", "admin")
     *         .add("password", "secret")
     *         .add("remember", "true")
     *     )
     *     .build();
     * </pre>
     */
    @SuppressWarnings("LombokGetterMayBeUsed")
    public static class FormBody {

        private final MultiValueMap<String, String> data = new LinkedMultiValueMap<>();

        public FormBody add(String name, String value) {
            data.add(name, value);
            return this;
        }

        public FormBody addAll(java.util.Map<String, String> values) {
            values.forEach(data::add);
            return this;
        }

        public MultiValueMap<String, String> getData() {
            return data;
        }
    }

    /**
     * Multipart 数据构建器 (multipart/form-data)
     *
     * <pre>
     * ApiRequest.post("/upload")
     *     .multipart(mp -> mp
     *         .file("avatar", new File("/path/to/image.jpg"))
     *         .file("doc", "report.pdf", pdfBytes, MediaType.APPLICATION_PDF)
     *         .field("username", "john")
     *         .field("tags", "java")
     *         .field("tags", "spring")
     *     )
     *     .build();
     * </pre>
     */
    @SuppressWarnings("LombokGetterMayBeUsed")
    public static class MultipartBody {

        private final MultiValueMap<String, Object> data = new LinkedMultiValueMap<>();

        // ============ 普通字段 ============

        /**
         * 添加文本字段
         */
        public MultipartBody field(String name, String value) {
            data.add(name, value);
            return this;
        }

        /**
         * 添加任意值字段
         */
        public MultipartBody field(String name, Object value) {
            data.add(name, value);
            return this;
        }

        // ============ 文件上传 ============

        /**
         * 上传 File
         */
        public MultipartBody file(String name, File file) {
            data.add(name, new FileSystemResource(file));
            return this;
        }

        /**
         * 上传 Path
         */
        public MultipartBody file(String name, Path path) {
            data.add(name, new FileSystemResource(path));
            return this;
        }

        /**
         * 上传 Resource（Spring 统一资源）
         */
        public MultipartBody file(String name, Resource resource) {
            data.add(name, resource);
            return this;
        }

        /**
         * 上传 byte[] 并指定文件名和类型
         */
        public MultipartBody file(String name, String filename, byte[] content, MediaType mediaType) {
            // ByteArrayResource 不带文件名，需要包装
            ByteArrayResource resource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            HttpEntity<Resource> entity = new HttpEntity<>(resource, headers);

            data.add(name, entity);
            return this;
        }

        /**
         * 上传 byte[]（自动推断类型）
         */
        public MultipartBody file(String name, String filename, byte[] content) {
            return file(name, filename, content, MediaType.APPLICATION_OCTET_STREAM);
        }

        /**
         * 上传 InputStream
         */
        public MultipartBody file(String name, String filename, InputStream inputStream, MediaType mediaType) {
            InputStreamResource resource = new InputStreamResource(inputStream) {
                @Override
                public String getFilename() {
                    return filename;
                }

                @Override
                public long contentLength() {
                    return -1; // 未知长度
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            HttpEntity<Resource> entity = new HttpEntity<>(resource, headers);

            data.add(name, entity);
            return this;
        }

        // ============ 构建 ============

        public MultiValueMap<String, Object> getData() {
            return data;
        }
    }

    @Override
    public String toString() {
        return method + " " + uri +
                (hasBody() ? " [" + contentType + "]" : "") +
                (hasQueryParams() ? " params=" + queryParams.keySet() : "");
    }

}
