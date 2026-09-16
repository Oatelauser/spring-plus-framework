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
     *
     * @param filename 文件名
     * @return 资源对象，可能资源对象指向的资源不存在
     */
    public static Resource getResource(String filename) {
        File file = new File(HOME.getDir(), filename);
        if (file.exists()) {
            return new FileSystemResource(file);
        }
        return new ClassPathResource(filename);
    }

}
