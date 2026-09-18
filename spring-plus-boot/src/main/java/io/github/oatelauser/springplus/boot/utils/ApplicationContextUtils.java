package io.github.oatelauser.springplus.boot.utils;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 上下文工具类
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-26
 * @since 1.0
 */
public abstract class ApplicationContextUtils {

    @Nullable
    public static DefaultListableBeanFactory getDefaultListableBeanFactory(ApplicationContext applicationContext) {
        return (DefaultListableBeanFactory) getConfigurableListableBeanFactory(applicationContext);
    }

    private static ConfigurableListableBeanFactory getConfigurableListableBeanFactory(ApplicationContext applicationContext) {
        if (applicationContext instanceof ConfigurableListableBeanFactory listableBeanFactory) {
            return listableBeanFactory;
        }
        if (applicationContext instanceof ConfigurableApplicationContext configurableApplicationContext) {
            return configurableApplicationContext.getBeanFactory();
        }
        return null;
    }

}
