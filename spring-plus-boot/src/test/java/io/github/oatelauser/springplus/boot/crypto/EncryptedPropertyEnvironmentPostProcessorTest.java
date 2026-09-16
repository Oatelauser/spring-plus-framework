package io.github.oatelauser.springplus.boot.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static io.github.oatelauser.springplus.boot.crypto.EncryptedPropertyEnvironmentPostProcessor.KEY_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedPropertyEnvironmentPostProcessorTest {

    private final EncryptedPropertyEnvironmentPostProcessor processor =
            new EncryptedPropertyEnvironmentPostProcessor();

    @BeforeEach
    void setUp() {
        System.clearProperty(KEY_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(KEY_PROPERTY);
    }

    @Test
    void decryptsEncryptedValuesToPlaintext() {
        String key = ConfigCipher.generateKey();
        StandardEnvironment environment = newEnvironment(
                "spring.datasource.password", ConfigCipher.encrypt(key, "Dev@123456"),
                "app.plain", "plain-value");
        System.setProperty(KEY_PROPERTY, key);

        processor.postProcessEnvironment(environment, null);

        assertEquals("Dev@123456", environment.getProperty("spring.datasource.password"));
        assertEquals("plain-value", environment.getProperty("app.plain"));
    }

    @Test
    void environmentWithoutEncryptedValueNeedsNoKey() {
        StandardEnvironment environment = newEnvironment(
                "spring.datasource.password", "",
                "app.plain", "plain-value");

        assertDoesNotThrow(() -> processor.postProcessEnvironment(environment, null));
        assertEquals("plain-value", environment.getProperty("app.plain"));
    }

    @Test
    void missingKeyWithEncryptedValueFailsFast() {
        StandardEnvironment environment = newEnvironment(
                "spring.datasource.password",
                ConfigCipher.encrypt(ConfigCipher.generateKey(), "Dev@123456"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> processor.postProcessEnvironment(environment, null));
        assertTrue(exception.getMessage().contains("APP_CONFIG_KEY"));
    }

    @Test
    void wrongKeyFailsFastWithPropertyName() {
        StandardEnvironment environment = newEnvironment(
                "spring.datasource.password",
                ConfigCipher.encrypt(ConfigCipher.generateKey(), "Dev@123456"));
        System.setProperty(KEY_PROPERTY, ConfigCipher.generateKey());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> processor.postProcessEnvironment(environment, null));
        assertTrue(exception.getMessage().contains("spring.datasource.password"));
    }

    @Test
    void decryptedValueIsVisibleToValueInjection() {
        String key = ConfigCipher.generateKey();
        StandardEnvironment environment = newEnvironment(
                "app.test.secret", ConfigCipher.encrypt(key, "s3cret"));
        System.setProperty(KEY_PROPERTY, key);
        processor.postProcessEnvironment(environment, null);

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.setEnvironment(environment);
            context.register(SecretHolder.class);
            context.refresh();
            assertEquals("s3cret", context.getBean(SecretHolder.class).secret);
        } finally {
            context.close();
        }
    }

    private StandardEnvironment newEnvironment(Object... pairs) {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> properties = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            properties.put((String) pairs[i], pairs[i + 1]);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    static class SecretHolder {

        @Value("${app.test.secret}")
        String secret;
    }
}
