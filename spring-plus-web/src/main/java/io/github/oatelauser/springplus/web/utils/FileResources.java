package io.github.oatelauser.springplus.web.utils;

import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;

/**
 * 文件资源工具类
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-09-24
 * @since 1.0
 */
public class FileResources {

    public static final ApplicationHome HOME = new ApplicationHome(FileResources.class);

    /**
     * 查看具有优先级的文件：1.先从jar包目录查找；2.其次从classpath查找
     * <p>
     * 文件名经规范化后拒绝 {@code ..} 穿越、绝对路径与盘符路径（防任意文件读取，CWE-22）。
     *
     * @param filename 文件名（相对路径，不允许穿越）
     * @return 资源对象，可能资源对象指向的资源不存在
     * @throws IllegalArgumentException 文件名包含路径穿越或为绝对路径
     */
    public static Resource getResource(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename 不能为空");
        }
        String normalized = java.nio.file.Paths.get(filename).normalize().toString().replace('\\', '/');
        if (normalized.contains("..") || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("非法文件名（路径穿越防护）: " + filename);
        }
        File file = new File(HOME.getDir(), normalized);
        if (file.exists()) {
            return new FileSystemResource(file);
        }
        return new ClassPathResource(normalized);
    }

}
