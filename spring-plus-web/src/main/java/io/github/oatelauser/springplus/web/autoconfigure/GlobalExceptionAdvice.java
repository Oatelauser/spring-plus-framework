package io.github.oatelauser.springplus.web.autoconfigure;

import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.engine.ExceptionOutputEngine;
import io.github.oatelauser.springplus.web.response.BusinessStatus;
import io.github.oatelauser.springplus.web.response.ClientStatus;
import io.github.oatelauser.springplus.web.response.SystemStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * 统一异常响应处理器（v3.0，设计文档 5.1；更名自 {@code ExceptionHandlerExceptionProcessor}）。
 * <p>
 * 每个 {@link ExceptionHandler} 方法只做两件事：
 * <ol>
 *   <li>构造 {@link ErrorDescriptor}（确定 code/message/details/statusIntent）；</li>
 *   <li>{@code return engine.dispatch(ex, descriptor, request, response)}。</li>
 * </ol>
 * 不再自己打日志、不再自己包 {@code ResponseEntity}——所有协议探测、日志、注解覆盖、渲染
 * 全部由 {@link ExceptionOutputEngine} 收口。返回值统一为 {@link Object}（可能是
 * {@code ResponseEntity}，由处理器产出）。
 *
 * <h3>优先级语义</h3>
 * <p>
 * handler 构造的 descriptor 作为 {@code defaultDescriptor}（P1）传入引擎：若接口方法上贴了
 * {@code @ExceptionResponse}（P0），则注解覆盖 handler 的默认码（验收 UC-10）。
 * 兜底 {@code @ExceptionHandler(Exception.class)} 走 {@link ExceptionOutputEngine#dispatchFallback}，
 * 让 Mapper 链处理（ServiceException/ServerStatus/异常类注解/兜底）。
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2023-04-07
 * @since 1.0
 */
@RestControllerAdvice
public class GlobalExceptionAdvice {

    private final ExceptionOutputEngine engine;

    public GlobalExceptionAdvice(ExceptionOutputEngine engine) {
        this.engine = engine;
    }

    // ====================== 参数校验异常 ======================

    /**
     * 处理 @Validated 校验异常（方法参数级别）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolationException(ConstraintViolationException ex,
            HttpServletRequest request, HttpServletResponse response) {
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        String message = violations.stream()
                .map(ExceptionMessageUtils::formatConstraintViolation)
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        Map<String, Object> details = ExceptionMessageUtils.toConstraintViolationDetails(violations);
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.PARAMETER_VALIDATION_FAILED, ex)
                .message(message).details(details).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    /**
     * 处理 @Valid 校验异常（RequestBody、表单对象）。
     */
    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class })
    public Object handleBindException(BindException ex,
            HttpServletRequest request, HttpServletResponse response) {
        String message = ExceptionMessageUtils.formatBindingErrors(ex);
        Map<String, Object> details = ExceptionMessageUtils.toFieldDetails(ex);
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.PARAMETER_VALIDATION_FAILED, ex)
                .message(message).details(details).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    // ====================== 请求参数异常 ======================
    /**
     * 处理 Spring 6.1+ 内置方法级校验异常（v3.1 新增）。
     * <p>
     * {@code @RequestParam} / {@code @PathVariable} 等非 body 参数上直接贴 Bean Validation
     * 约束时，Spring 6.1 起抛本异常（而非 {@code ConstraintViolationException}）。
     * 缺少本处理器它会掉进兜底，被翻译成 C 档系统错误并按未知异常打 ERROR 堆栈——
     * 客户端的参数错误记到服务端头上。输出与 {@link #handleBindException} 同构
     * （A0430 + violations）；方法级校验聚合全部参数结果，不受 Spring 7 body 校验
     * fail-fast 影响。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Object handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpServletRequest request, HttpServletResponse response) {
        String message = ExceptionMessageUtils.formatHandlerMethodValidation(ex);
        Map<String, Object> details = ExceptionMessageUtils.toFieldDetails(ex);
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.PARAMETER_VALIDATION_FAILED, ex)
                .message(message).details(details).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingServletRequestParameterException(MissingServletRequestParameterException ex,
            HttpServletRequest request, HttpServletResponse response) {
        String detail = String.format("参数名: %s, 类型: %s", ex.getParameterName(), ex.getParameterType());
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.PARAMETER_MISSING, ex)
                .message(detail).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public Object handleMissingPathVariableException(MissingPathVariableException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.PARAMETER_MISSING, ex)
                .message("缺少路径变量: " + ex.getVariableName()).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public Object handleMissingRequestHeaderException(MissingRequestHeaderException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.HEADER_MISSING, ex).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public Object handleMissingServletRequestPartException(MissingServletRequestPartException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.PARAMETER_MISSING, ex)
                .message("缺少请求Part: " + ex.getRequestPartName()).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler({ TypeMismatchException.class, MethodArgumentTypeMismatchException.class })
    public Object handleTypeMismatchException(TypeMismatchException ex,
            HttpServletRequest request, HttpServletResponse response) {
        // 不回显 ex.getValue()：入参原值经响应反射给客户端属于信息泄露（CWE-209/117）
        String message = String.format("参数类型错误: %s (期望类型: %s)",
                ex.getPropertyName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "未知");
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.PARAMETER_TYPE_ERROR, ex)
                .message(message).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    // ====================== 请求体解析异常 ======================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleHttpMessageNotReadableException(HttpMessageNotReadableException ex,
            HttpServletRequest request, HttpServletResponse response) {
        String message = ExceptionMessageUtils.extractReadableMessage(ex);
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.JSON_PARSE_ERROR, ex)
                .message(message).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public Object handleHttpMessageNotWritableException(HttpMessageNotWritableException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(SystemStatus.INTERNAL_ERROR, ex)
                .message("响应数据处理失败").build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(ConversionNotSupportedException.class)
    public Object handleConversionNotSupportedException(ConversionNotSupportedException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.PARAMETER_TYPE_ERROR, ex).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    // ====================== HTTP请求方法异常 ======================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request, HttpServletResponse response) {
        String message = String.format("不支持的请求方法: %s，支持的方法: %s",
                ex.getMethod(),
                ex.getSupportedHttpMethods() != null ? ex.getSupportedHttpMethods() : "无");
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.METHOD_NOT_SUPPORTED, ex)
                .message(message).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Object handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request, HttpServletResponse response) {
        String message = String.format("不支持的Content-Type: %s，支持的类型: %s",
                ex.getContentType(), ex.getSupportedMediaTypes());
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.CONTENT_TYPE_NOT_SUPPORTED, ex)
                .message(message).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public Object handleHttpMediaTypeNotAcceptableException(HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.CONTENT_TYPE_NOT_SUPPORTED, ex)
                .message("不支持的响应类型").build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    // ====================== 资源不存在异常 ======================

    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNoHandlerFoundException(NoHandlerFoundException ex,
            HttpServletRequest request, HttpServletResponse response) {
        String message = String.format("接口不存在: %s %s", ex.getHttpMethod(), ex.getRequestURL());
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.NOT_FOUND, ex)
                .message(message).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFoundException(NoResourceFoundException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.NOT_FOUND, ex)
                .message("资源不存在: " + ex.getResourcePath()).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    // ====================== 文件上传异常 ======================

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpServletRequest request, HttpServletResponse response) {
        long maxSize = ex.getMaxUploadSize();
        String message = maxSize > 0
                ? String.format("上传文件大小超出限制，最大允许: %dMB", maxSize / 1024 / 1024)
                : "上传文件大小超出限制";
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.FILE_SIZE_EXCEED, ex)
                .message(message).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    // ====================== 超时异常 ======================

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public Object handleAsyncRequestTimeoutException(AsyncRequestTimeoutException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(ClientStatus.REQUEST_TIMEOUT, ex).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(TimeoutException.class)
    public Object handleTimeoutException(TimeoutException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(SystemStatus.SERVICE_TIMEOUT, ex).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    // ====================== 数据库异常 ======================

    @ExceptionHandler(DuplicateKeyException.class)
    public Object handleDuplicateKeyException(DuplicateKeyException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(BusinessStatus.DATA_DUPLICATE, ex)
                .message("数据已存在，请勿重复添加").build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrityViolationException(DataIntegrityViolationException ex,
            HttpServletRequest request, HttpServletResponse response) {
        BusinessStatus status = ex.getCause() instanceof DuplicateKeyException
                ? BusinessStatus.DATA_DUPLICATE
                : BusinessStatus.DATA_REFERENCED;
        String message = status == BusinessStatus.DATA_DUPLICATE ? "数据已存在，请勿重复添加" : "数据关联中，无法操作";
        ErrorDescriptor descriptor = ErrorDescriptor.of(status, ex).message(message).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(DataAccessException.class)
    public Object handleDataAccessException(DataAccessException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(SystemStatus.DATABASE_ERROR, ex).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    @ExceptionHandler(SQLException.class)
    public Object handleSQLException(SQLException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(SystemStatus.DATABASE_ERROR, ex).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    // ====================== 业务异常 ======================

    /**
     * 处理业务异常。
     * <p>
     * 用异常自带的 code/message，并把 {@link ServiceException#getResponseStatus()} 作为
     * 协议无关的 {@code statusIntent} 携带（v3.0：不再包 JsonErrorHint——状态码已升维），
     * 由各协议处理器在允许的时机读取。若方法上贴了 {@code @ExceptionResponse}（P0），
     * 则注解覆盖本默认描述。
     */
    @ExceptionHandler(ServiceException.class)
    public Object handleServiceException(ServiceException ex,
            HttpServletRequest request, HttpServletResponse response) {
        ErrorDescriptor descriptor = ErrorDescriptor.of(ex.getCode(), ex.getMessage(), ex)
                .statusIntent(ex.getResponseStatus()).build();
        return this.engine.dispatch(ex, descriptor, request, response);
    }

    // ====================== 兜底异常处理 ======================

    /**
     * 兜底异常处理：不构造默认描述，交给 Mapper 链（异常类注解 / ServerStatus / 兜底）。
     * <p>
     * 注意：{@code HandlerMethod} 在 Filter / 静态资源 / 请求映射阶段异常时可能为 null，
     * 引擎会从请求属性自行解析。
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception ex,
            HttpServletRequest request, HttpServletResponse response) {
        return this.engine.dispatchFallback(ex, request, response);
    }

}
