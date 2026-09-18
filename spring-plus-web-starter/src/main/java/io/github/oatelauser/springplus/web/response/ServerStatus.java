package io.github.oatelauser.springplus.web.response;

import io.github.oatelauser.springplus.web.error.ServiceException;

import java.text.MessageFormat;

/**
 * 服务状态码接口
 * <p>
 * 状态码规范（参考阿里巴巴错误码规范）：
 * <ul>
 *   <li>00000: 成功</li>
 *   <li>A0xxx: 客户端错误</li>
 *   <li>B0xxx: 业务错误</li>
 *   <li>C0xxx: 系统错误</li>
 * </ul>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-29
 * @since 1.0
 */
public interface ServerStatus {

    String SUCCESS_CODE = "00000";
    String SUCCESS_MSG = "操作成功";
    String SERVER_INTERNAL_CODE = "C0101";
    String SERVER_INTERNAL_MSG = "系统内部错误";

    /**
     * 获取状态码
     */
    String getCode();

    /**
     * 获取状态消息
     */
    String getMessage();

    /**
     * 抛出异常
     */
    default void throwException() {
        throw new ServiceException(this);
    }

    /**
     * 判断是否成功
     */
    default boolean isSuccess() {
        return SUCCESS_CODE.equals(getCode());
    }

    /**
     * 判断是否为客户端错误
     */
    default boolean isClientError() {
        return getCode() != null && getCode().startsWith("A");
    }

    /**
     * 判断是否为业务错误
     */
    default boolean isBusinessError() {
        return getCode() != null && getCode().startsWith("B");
    }

    /**
     * 判断是否为系统错误
     */
    default boolean isSystemError() {
        return getCode() != null && getCode().startsWith("C");
    }

    /**
     * 格式化消息（支持占位符 {0}, {1}...）
     */
    default String format(Object... args) {
        if (args == null || args.length == 0) {
            return getMessage();
        }
        return MessageFormat.format(getMessage(), args);
    }

    /**
     * 创建带格式化消息的新状态
     */
    default ServerStatus withArgs(Object... args) {
        if (args == null || args.length == 0) {
            return this;
        }
        String formattedMsg = format(args);
        String statusCode = getCode();
        return new ServerStatus() {
            @Override
            public String getCode() {
                return statusCode;
            }

            @Override
            public String getMessage() {
                return formattedMsg;
            }

            @Override
            public String toString() {
                return "ServerStatus{code='" + statusCode + "', msg='" + formattedMsg + "'}";
            }
        };
    }

    /**
     * 包装服务状态进行替换
     *
     * @param code    替换的状态码
     * @param message 替换的状态消息
     */
    default ServerStatus wrap(String code, String message) {
        return new Wrapper(code, message, this);
    }

    /**
     * 包装服务状态进行替换
     *
     * @param message 替换的状态消息
     */
    default ServerStatus wrap(String message) {
        return this.wrap(this.getCode(), message);
    }

    /**
     * 创建自定义状态
     */
    static ServerStatus of(String code, String msg) {
        return new ServerStatus() {
            @Override
            public String getCode() {
                return code;
            }

            @Override
            public String getMessage() {
                return msg;
            }

            @Override
            public String toString() {
                return "ServerStatus{code='" + code + "', msg='" + msg + "'}";
            }
        };
    }

    /**
     * 包装类
     */
    class Wrapper implements ServerStatus {

        private final String code;
        private final String message;
        private final ServerStatus serverStatus;

        public Wrapper(String code, String message, ServerStatus serverStatus) {
            this.code = code;
            this.message = message;
            this.serverStatus = serverStatus;
        }


        @Override
        public String getCode() {
            return code == null ? serverStatus.getCode() : code;
        }

        @Override
        public String getMessage() {
            return message == null ? serverStatus.getMessage() : message;
        }
    }

}
