package io.github.oatelauser.springplus.web.response;

/**
 * 服务状态提供者
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-03-11
 * @since 1.0
 */
public interface ServerStatusProvider {

    /**
     * 获取服务状态
     */
    ServerStatus getServerStatus();

}
