package io.github.oatelauser.springplus.calcite.memory.mybatis;

import io.github.oatelauser.springplus.calcite.memory.exception.RegistryException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

/**
 * 独立 MyBatis {@link SqlSessionFactory} 构建器：在 {@link CalciteDataSource}（或任意数据源）之上
 * 构建与生产 MySQL <b>完全隔离</b>的内存专用 SqlSessionFactory，供 MyBatis Mapper 以标准方式查询内存表。
 *
 * <p>隔离要点（设计文档 06）：
 * <ul>
 *   <li>独立 Environment ID {@value #ENVIRONMENT_ID}，独立 {@link Configuration}，
 *       不经生产 {@code @MapperScan("io.github.oatelauser.infra.**.mapper")} 自动扫描 —— 调用方显式注册需要的 Mapper。</li>
 *   <li>默认开启 {@code mapUnderscoreToCamelCase}：DB 下划线列自动映射驼峰属性。</li>
 *   <li>{@link JdbcTransactionFactory}：每个 SqlSession 一个连接，无连接池、无全局事务。</li>
 *   <li>仅支持只读查询：不支持事务回滚、生成主键、批量、存储过程（Calcite 内存快照不可变）。</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 * SqlSessionFactory sf = IsolatedSqlSessionFactoryFactory.over(dataSource)
 *         .addMapper(OrderMapper.class)
 *         .addMapperXml("mapper/OrderXmlMapper.xml")
 *         .build();
 * </pre>
 */
public final class IsolatedSqlSessionFactoryFactory {

    /** Environment ID，标识内存专用环境，区别于生产环境。 */
    public static final String ENVIRONMENT_ID = "calcite-memory";

    private final DataSource dataSource;
    private final List<Class<?>> mapperInterfaces = new ArrayList<>();
    private final List<String> xmlResources = new ArrayList<>();
    private boolean mapUnderscoreToCamelCase = true;

    private IsolatedSqlSessionFactoryFactory(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource 不能为 null");
        }
        this.dataSource = dataSource;
    }

    /** 在指定数据源上构建工厂。 */
    public static IsolatedSqlSessionFactoryFactory over(DataSource dataSource) {
        return new IsolatedSqlSessionFactoryFactory(dataSource);
    }

    /** 注册注解式 Mapper 接口（{@code @Select} / {@code @Insert} 等）。重复注册同一接口会被忽略。 */
    public IsolatedSqlSessionFactoryFactory addMapper(Class<?> mapperInterface) {
        if (mapperInterface == null) {
            throw new IllegalArgumentException("mapperInterface 不能为 null");
        }
        if (!mapperInterface.isInterface()) {
            throw new IllegalArgumentException(mapperInterface + " 必须是接口");
        }
        mapperInterfaces.add(mapperInterface);
        return this;
    }

    /** 加载 XML Mapper（classpath 资源），其 namespace 对应的接口在解析时自动绑定。 */
    public IsolatedSqlSessionFactoryFactory addMapperXml(String classpathResource) {
        if (classpathResource == null || classpathResource.isBlank()) {
            throw new IllegalArgumentException("classpathResource 不能为空");
        }
        xmlResources.add(classpathResource);
        return this;
    }

    /** 是否开启下划线列名 -&gt; 驼峰属性自动映射，默认 true。 */
    public IsolatedSqlSessionFactoryFactory mapUnderscoreToCamelCase(boolean enabled) {
        this.mapUnderscoreToCamelCase = enabled;
        return this;
    }

    /** 构建 SqlSessionFactory。XML 先于注解 Mapper 加载（其 namespace 接口自动绑定），避免重复注册冲突。 */
    public SqlSessionFactory build() {
        Environment environment = new Environment(ENVIRONMENT_ID, new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(mapUnderscoreToCamelCase);
        for (String resource : xmlResources) {
            loadXmlMapper(configuration, resource);
        }
        for (Class<?> mapperInterface : mapperInterfaces) {
            if (!configuration.hasMapper(mapperInterface)) {
                configuration.addMapper(mapperInterface);
            }
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void loadXmlMapper(Configuration configuration, String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader == null
                ? ClassLoader.getSystemResourceAsStream(resource)
                : loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new RegistryException("未找到 mapper XML 资源: " + resource);
            }
            XMLMapperBuilder builder =
                    new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments());
            builder.parse();
        } catch (RegistryException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryException("解析 mapper XML 失败: " + resource, e);
        }
    }
}
