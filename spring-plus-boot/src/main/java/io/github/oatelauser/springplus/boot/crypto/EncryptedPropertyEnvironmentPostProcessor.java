package io.github.oatelauser.springplus.boot.crypto;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在 Environment 准备完成后、任何 Bean 创建之前，遍历所有 PropertySource，
 * 将形如 {@code ENC(...)} 的密文解密为明文，并以最高优先级的 PropertySource
 * 注入回 Environment，对 {@code @Value}、数据源自动配置等全部生效。
 *
 * <p>密钥读取顺序（找到即止）：</p>
 * <ol>
 *     <li>环境变量 {@value #KEY_ENV_VARIABLE}（生产推荐，由运维/脚本注入）</li>
 *     <li>属性 {@value #KEY_PROPERTY}（本地调试用 {@code -D} 或 {@code --} 指定）</li>
 * </ol>
 *
 * <p>fail-fast 策略：配置中存在 {@code ENC(...)} 但密钥缺失、或解密失败时，
 * 直接抛异常终止启动，不做静默降级。</p>
 */
public class EncryptedPropertyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 密钥优先读取的环境变量名。
     */
    public static final String KEY_ENV_VARIABLE = "APP_CONFIG_KEY";

    /**
     * 密钥其次读取的属性名（系统属性或启动参数）。
     */
    public static final String KEY_PROPERTY = "app.config-key";

    /**
     * 解密结果注入的 PropertySource 名称。
     */
    static final String DECRYPTED_PROPERTY_SOURCE_NAME = "decryptedConfig";

    @Override
    @SuppressWarnings("NullableProblems")
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, String> encryptedEntries = this.collectEncryptedEntries(environment);
        if (encryptedEntries.isEmpty()) {
            return;
        }
        String base64Key = resolveKey(environment);
        Map<String, Object> decrypted = new HashMap<>(encryptedEntries.size());
        encryptedEntries.forEach((name, encryptedValue) ->
                decrypted.put(name, decryptEntry(name, encryptedValue, base64Key)));
        environment.getPropertySources()
                .addFirst(new MapPropertySource(DECRYPTED_PROPERTY_SOURCE_NAME, decrypted) {
                    /**
                     * toString 脱敏（V11/CWE-214）：/actuator/env 等呈现处只暴露键名与数量，不泄露明文值
                     */
                    @Override
                    public String toString() {
                        return "MapPropertySource [name='" + getName() + "', keys="
                                + source.keySet() + "](values masked)";
                    }
                });
    }

    /**
     * 保证在 {@code ConfigDataEnvironmentPostProcessor} 之后执行，
     * 否则 application*.yml 尚未加载，扫描不到密文。
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private Map<String, String> collectEncryptedEntries(ConfigurableEnvironment environment) {
        Map<String, String> encryptedEntries = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (encryptedEntries.containsKey(name)) {
                    continue;
                }
                if (enumerable.getProperty(name) instanceof String value && ConfigCipher.isEncrypted(value)) {
                    encryptedEntries.put(name, value);
                }
            }
        }
        return encryptedEntries;
    }

    private String resolveKey(ConfigurableEnvironment environment) {
        String key = System.getenv(KEY_ENV_VARIABLE);
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        key = environment.getProperty(KEY_PROPERTY);
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        throw new IllegalStateException("检测到 ENC(...) 密文，但未配置解密密钥："
                + "请设置环境变量 " + KEY_ENV_VARIABLE
                + "，或通过 -D" + KEY_PROPERTY + "=xxx / --" + KEY_PROPERTY + "=xxx 指定");
    }

    private String decryptEntry(String name, String encryptedValue, String base64Key) {
        try {
            return ConfigCipher.decrypt(base64Key, encryptedValue);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("解密配置项 [" + name + "] 失败：密钥不匹配或密文已损坏", e);
        }
    }

}
