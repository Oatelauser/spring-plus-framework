package io.github.oatelauser.springplus.boot.lifecycle;

/**
 * 应用初始化器：在Web容器接受请求之前触发
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-28
 * @since 1.0
 */
public interface StartupProcess {

    /**
     * 执行初始化操作
     *
     * @throws Exception 初始化异常
     */
    void start() throws Exception;

}
