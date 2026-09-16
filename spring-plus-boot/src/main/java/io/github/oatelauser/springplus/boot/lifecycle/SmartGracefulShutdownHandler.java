package io.github.oatelauser.springplus.boot.lifecycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 标准实现优雅关闭
 *
 * @author DearYang
 * @date 2022-08-01
 * @since 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class SmartGracefulShutdownHandler implements SmartLifecycle {

    private volatile boolean running = false;
    private final ObjectProvider<List<ShutdownHook>> shutdownHooks;

    @Override
    public void start() {
        this.running = true;
    }

    @Override
    public void stop() {
        this.invokeShutdown();
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE - 1;
    }

    /**
     * 关闭操作
     */
    protected void invokeShutdown() {
        List<ShutdownHook> shutdownHooks = this.shutdownHooks.getIfAvailable();
        if (!CollectionUtils.isEmpty(shutdownHooks)) {
            for (ShutdownHook shutdownHook : shutdownHooks) {
                try {
                    shutdownHook.shutdown();
                } catch (Exception e) {
                    log.error("服务关闭异常", e);
                }
            }
        }
    }

}
