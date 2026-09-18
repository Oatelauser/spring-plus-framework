package io.github.oatelauser.springplus.boot.lifecycle;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.CollectionUtils;

import io.github.oatelauser.springplus.boot.lifecycle.StartupProcess;

import java.util.List;

/**
 * Web容器启动之前的初始化执行器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-28
 * @since 1.0
 */
public class WebServerPostProcessor implements SmartLifecycle {

    private volatile boolean running = false;
    private final List<StartupProcess> startupProcesses;

    public WebServerPostProcessor(ObjectProvider<List<StartupProcess>> startupProcesses) {
        this.startupProcesses = startupProcesses.getIfAvailable();
    }

    @Override
    public void start() {
        this.postProcessBeforeWebServer();
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE - 1;
    }

    protected void postProcessBeforeWebServer() {
        if (!CollectionUtils.isEmpty(startupProcesses)) {
            for (StartupProcess startupProcess : startupProcesses) {
                try {
                    startupProcess.start();
                } catch (Exception e) {
                    throw new BeanInitializationException("对象[" +
                            startupProcess.getClass().getName() + "]初始化操作执行失败", e);
                }
            }
        }
    }

}
