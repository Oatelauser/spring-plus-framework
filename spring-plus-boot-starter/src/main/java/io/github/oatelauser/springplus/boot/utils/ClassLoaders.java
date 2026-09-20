package io.github.oatelauser.springplus.boot.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * 类加载器工具类
 * <p>
 * 提供功能：1.获取类加载器（线程上下文类加载器、按类 协商出的类加载器、全部类加载器层级）；
 * 2.类的加载与存在性检测；3.资源查找（单个资源与多个资源枚举）。
 * 适用于不确定运行环境类加载器拓扑（应用服务器、fat-jar、自定义加载器）的场景，
 * 参考 log4j2 的 {@code Loader} 实现
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-19
 * @since 1.0
 */
@Slf4j
@SuppressWarnings("SpellCheckingInspection")
public final class ClassLoaders {

    private static final ClassLoader[] EMPTY_CLASS_LOADER_ARRAY = {};
    private static final PrivilegedAction<ClassLoader> TCCL_GETTER = new ThreadContextClassLoaderGetter();

    /**
     * 获取当前线程的上下文类加载器（TCCL）
     * <p>
     * 取不到（或为 bootstrap 加载器）时依次回退到本类类加载器、系统类加载器
     *
     * @return 线程上下文类加载器，不会返回 null
     */
    public static ClassLoader getThreadContextClassLoader() {
        ClassLoader cl = TCCL_GETTER.run();
        if (cl == null) {
            cl = ClassLoaders.class.getClassLoader();
        }
        return cl;
    }

    /**
     * 获取默认类加载器：在线程上下文类加载器与本类类加载器之间协商
     *
     * @return 协商出的类加载器，详见 {@link #getClassLoader(Class, Class)}
     */
    public static ClassLoader getClassLoader() {
        return getClassLoader(ClassLoaders.class, null);
    }

    /**
     * 在线程上下文类加载器与两个类的加载器之间，协商出一个"最下层"（最具体）的子加载器
     * <p>
     * 双亲委派模型下子加载器能看到父加载器的类，因此返回子加载器可以同时访问到三者可见的类；
     * 这在多加载器环境（如应用服务器）下能避免 {@link ClassNotFoundException}
     *
     * @param class1 第一个类，可为 null（表示不参与协商）
     * @param class2 第二个类，可为 null（表示不参与协商）
     * @return 协商出的类加载器，可能为 null（表示 bootstrap 加载器）
     */
    public static ClassLoader getClassLoader(Class<?> class1, Class<?> class2) {
        final ClassLoader threadContextClassLoader = getThreadContextClassLoader();
        final ClassLoader loader1 = class1 == null ? null : class1.getClassLoader();
        final ClassLoader loader2 = class2 == null ? null : class2.getClassLoader();

        if (isChild(threadContextClassLoader, loader1)) {
            return isChild(threadContextClassLoader, loader2) ? threadContextClassLoader : loader2;
        }
        return isChild(loader1, loader2) ? loader1 : loader2;
    }

    /**
     * 判断 {@code loader1} 是否为 {@code loader2} 自身或其子加载器
     * <p>
     * {@code loader2} 为 null（表示 bootstrap 加载器）时，任何非空加载器都视为其子加载器
     *
     * @param loader1 待判断的类加载器
     * @param loader2 作为祖先参照的类加载器
     * @return true-是自身或子加载器
     */
    private static boolean isChild(ClassLoader loader1, ClassLoader loader2) {
        if (loader1 != null && loader2 != null) {
            ClassLoader parent = loader1.getParent();
            while (parent != null && parent != loader2) {
                parent = parent.getParent();
            }
            // once parent is null, we're at the system CL, which would indicate they have separate ancestry
            return parent != null;
        }
        return loader1 != null;
    }

    /**
     * 收集当前环境所有可用的类加载器
     * <p>
     * 包括：线程上下文类加载器、本类类加载器，并沿各自父链向上收集（含系统类加载器），按层级去重
     *
     * @return 类加载器数组，按从子到父的顺序排列
     */
    public static ClassLoader[] getClassLoaders() {
        final Collection<ClassLoader> classLoaders = new LinkedHashSet<>();
        final ClassLoader tcl = getThreadContextClassLoader();
        if (tcl != null) {
            classLoaders.add(tcl);
        }
        accumulateClassLoaders(ClassLoaders.class.getClassLoader(), classLoaders);
        accumulateClassLoaders(tcl == null ? null : tcl.getParent(), classLoaders);
        final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (systemClassLoader != null) {
            classLoaders.add(systemClassLoader);
        }
        return classLoaders.toArray(EMPTY_CLASS_LOADER_ARRAY);
    }


    private static void accumulateClassLoaders(ClassLoader loader, Collection<ClassLoader> loaders) {
        // Some implementations may use null to represent the bootstrap class loader.
        if (loader != null && loaders.add(loader)) {
            accumulateClassLoaders(loader.getParent(), loaders);
        }
    }

    /**
     * 判断类是否可加载（存在且可链接）
     * <p>
     * 找不到类、链接失败均视为不可用；其余未知异常记录日志并返回 false，保证探测不会中断启动流程
     *
     * @param className 类全限定名
     * @return true-类可用
     */
    public static boolean isClassAvailable(String className) {
        try {
            final Class<?> clazz = loadClass(className);
            return clazz != null;
        } catch (final ClassNotFoundException | LinkageError e) {
            return false;
        } catch (final Throwable e) {
            log.error("Unknown error checking for existence of class: " + className, e);
            return false;
        }
    }

    /**
     * 加载类：优先使用线程上下文类加载器，失败时回退到 {@link Class#forName(String)}（本类类加载器）
     *
     * @param className 类全限定名
     * @return 类对象
     * @throws ClassNotFoundException 两个加载器都找不到该类
     */
    public static Class<?> loadClass(String className) throws ClassNotFoundException {
        try {
            ClassLoader tccl = getThreadContextClassLoader();
            if (tccl != null) {
                return tccl.loadClass(className);
            }
        } catch (final Throwable ignored) {
        }
        return Class.forName(className);
    }

    /**
     * 查找指定名字的所有资源，合并去重后返回
     *
     * @param resource 资源名（如 {@code META-INF/spring.factories}）
     * @return 资源 URL 集合，详见 {@link #findUrlResources(String)}
     */
    public static Collection<URL> findResources(String resource) {
        final Collection<UrlResource> urlResources = findUrlResources(resource);
        final Collection<URL> resources = new LinkedHashSet<>(urlResources.size());
        for (final UrlResource urlResource : urlResources) {
            resources.add(urlResource.url);
        }
        return resources;
    }

    /**
     * 按优先级查找单个资源，返回第一个命中位置：
     * 1.线程上下文类加载器 → 2.本类类加载器 → 3.给定的默认类加载器 → 4.系统类加载器
     * <p>
     * 任一步骤抛出异常仅记录告警，不中断查找
     *
     * @param resource      资源名
     * @param defaultLoader 兜底类加载器，可为 null
     * @return 资源 URL，找不到返回 null
     */
    @SuppressWarnings("all")
    public static URL getResource(String resource, ClassLoader defaultLoader) {
        try {
            ClassLoader classLoader = getThreadContextClassLoader();
            if (classLoader != null) {
                log.trace("Trying to find [{}] using context class loader {}.", resource, classLoader);
                final URL url = classLoader.getResource(resource);
                if (url != null) {
                    return url;
                }
            }

            // We could not find resource. Let us now try with the classloader that loaded this class.
            classLoader = ClassLoaders.class.getClassLoader();
            if (classLoader != null) {
                log.trace("Trying to find [{}] using {} class loader.", resource, classLoader);
                final URL url = classLoader.getResource(resource);
                if (url != null) {
                    return url;
                }
            }
            // We could not find resource. Finally try with the default ClassLoader.
            if (defaultLoader != null) {
                log.trace("Trying to find [{}] using {} class loader.", resource, defaultLoader);
                final URL url = defaultLoader.getResource(resource);
                if (url != null) {
                    return url;
                }
            }
        } catch (final Throwable t) {
            //
            //  can't be InterruptedException or InterruptedIOException
            //    since not declared, must be error or RuntimeError.
            log.warn("Caught Exception while in Loader.getResource. This may be innocuous.", t);
        }

        // Last ditch attempt: get the resource from the class path. It
        // may be the case that clazz was loaded by the Extension class
        // loader which the parent of the system class loader. Hence the
        // code below.
        log.trace("Trying to find [{}] using ClassLoader.getSystemResource().", resource);
        return ClassLoader.getSystemResource(resource);
    }

    /**
     * 按优先级查找单个资源并返回输入流，查找顺序与 {@link #getResource(String, ClassLoader)} 一致
     *
     * @param resource      资源名
     * @param defaultLoader 兜底类加载器，可为 null
     * @return 资源输入流，找不到返回 null，由调用方负责关闭
     */
    @SuppressWarnings("all")
    public static InputStream getResourceAsStream(String resource, ClassLoader defaultLoader) {
        try {
            ClassLoader classLoader = getThreadContextClassLoader();
            InputStream is;
            if (classLoader != null) {
                log.trace("Trying to find [{}] using context class loader {}.", resource, classLoader);
                is = classLoader.getResourceAsStream(resource);
                if (is != null) {
                    return is;
                }
            }

            // We could not find resource. Let us now try with the classloader that loaded this class.
            classLoader = ClassLoaders.class.getClassLoader();
            if (classLoader != null) {
                log.trace("Trying to find [{}] using {} class loader.", resource, classLoader);
                is = classLoader.getResourceAsStream(resource);
                if (is != null) {
                    return is;
                }
            }

            // We could not find resource. Finally try with the default ClassLoader.
            if (defaultLoader != null) {
                log.trace("Trying to find [{}] using {} class loader.", resource, defaultLoader);
                is = defaultLoader.getResourceAsStream(resource);
                if (is != null) {
                    return is;
                }
            }
        } catch (final Throwable t) {
            //
            //  can't be InterruptedException or InterruptedIOException
            //    since not declared, must be error or RuntimeError.
            log.warn("Caught Exception while in Loader.getResource. This may be innocuous.", t);
        }

        // Last ditch attempt: get the resource from the class path. It
        // may be the case that clazz was loaded by the Extension class
        // loader which the parent of the system class loader. Hence the
        // code below.
        log.trace("Trying to find [{}] using ClassLoader.getSystemResource().", resource);
        return ClassLoader.getSystemResourceAsStream(resource);
    }

    /**
     * 在线程上下文、本类、系统三个类加载器中枚举指定名字的所有资源
     * <p>
     * 以「类加载器 + URL」二元组去重：不同加载器可能返回相同 URL，但代表不同的可见性来源
     *
     * @param resource 资源名
     * @return 资源二元组集合
     */
    static Collection<UrlResource> findUrlResources(String resource) {
        // @formatter:off
        final ClassLoader[] candidates = {getThreadContextClassLoader(), ClassLoaders.class.getClassLoader(), ClassLoader.getSystemClassLoader()};
        // @formatter:on
        final Collection<UrlResource> resources = new LinkedHashSet<>();
        for (final ClassLoader cl : candidates) {
            if (cl != null) {
                try {
                    final Enumeration<URL> resourceEnum = cl.getResources(resource);
                    while (resourceEnum.hasMoreElements()) {
                        resources.add(new UrlResource(cl, resourceEnum.nextElement()));
                    }
                } catch (final IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        return resources;
    }

    /**
     * 获取线程上下文类加载器的 {@link PrivilegedAction} 封装
     * <p>
     * 取不到时回退到本类类加载器，再取不到回退到系统类加载器
     */
    private static class ThreadContextClassLoaderGetter implements PrivilegedAction<ClassLoader> {
        @Override
        public ClassLoader run() {
            final ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                return cl;
            }
            final ClassLoader ccl = ClassLoaders.class.getClassLoader();
            return ccl == null ? ClassLoader.getSystemClassLoader() : ccl;
        }
    }

    /**
     * 资源 URL 及其来源类加载器的二元组，用于资源枚举时按「加载器 + URL」去重
     */
    private record UrlResource(ClassLoader classLoader, URL url) {
        @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }

                final UrlResource that = (UrlResource) o;

                if (!Objects.equals(classLoader, that.classLoader)) {
                    return false;
                }

                return Objects.equals(url, that.url);
            }

    }

}