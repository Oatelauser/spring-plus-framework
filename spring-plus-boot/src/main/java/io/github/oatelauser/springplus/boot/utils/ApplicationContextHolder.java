package io.github.oatelauser.springplus.boot.utils;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Spring ApplicationContext 工具类
 * <p>
 * 通过多重机制确保尽可能早地获取到 ApplicationContext：
 * <ul>
 *   <li>1. ApplicationContextInitializer —— 在 refresh() 之前即可拿到上下文</li>
 *   <li>2. ApplicationContextAware —— Bean 初始化阶段的标准回调</li>
 *   <li>3. @Component 注解 —— 确保被 Spring 扫描到</li>
 * </ul>
 * <p>
 * 使用方式一（推荐，最早生效）：在启动类中注册 Initializer
 * <pre>
 * SpringApplication app = new SpringApplication(Application.class);
 * app.addInitializers(new ApplicationContextUtils());
 * app.run(args);
 * </pre>
 * <p>
 * 使用方式二：依赖组件扫描自动注册（稍晚，但无需额外配置）
 * 只要包路径能扫描到即可。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-22
 * @since 1.0
 */
public class ApplicationContextHolder implements ApplicationContextAware, ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static volatile ApplicationContext applicationContext;

    // ======================== 上下文设置（多重保障） ========================

    /**
     * ApplicationContextInitializer 回调 —— 最早时机
     * 在 ApplicationContext refresh() 之前调用
     */
    @Override
    public void initialize(@NonNull ConfigurableApplicationContext context) {
        setContext(context);
    }

    /**
     * ApplicationContextAware 回调 —— 标准时机
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
        setContext(context);
    }

    /**
     * 手动设置（兜底方案）
     * <p>
     * 允许新上下文覆盖旧引用：devtools 等场景重启后旧上下文已销毁，
     * 若保持 set-if-null 会一直持有失效上下文导致取 Bean 异常。
     */
    public static void setContext(ApplicationContext context) {
        if (context != null) {
            ApplicationContextHolder.applicationContext = context;
        }
    }

    // ======================== 上下文获取 ========================

    /**
     * 获取 ApplicationContext
     *
     * @return ApplicationContext
     * @throws IllegalStateException 如果上下文尚未初始化
     */
    public static ApplicationContext getApplicationContext() {
        assertContextInjected();
        return applicationContext;
    }

    /**
     * 安全获取 ApplicationContext，未初始化时返回 null
     */
    public static ApplicationContext getApplicationContextOrNull() {
        return applicationContext;
    }

    /**
     * 判断上下文是否已经就绪
     */
    public static boolean isReady() {
        return applicationContext != null;
    }

    private static void assertContextInjected() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext 尚未初始化！" +
                    "请确认：1) 已在启动类中注册 ApplicationContextUtils 为 Initializer；" +
                    "或 2) ApplicationContextUtils 在组件扫描范围内且 Spring 容器已启动完成。"
            );
        }
    }

}
