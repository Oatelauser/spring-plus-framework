package io.github.oatelauser.springplus.boot.crypto;

/**
 * 配置加解密命令行工具，本地生成密钥与密文后手动贴回配置文件。
 *
 * <pre>
 * 用法：
 *   genkey                        生成 256-bit Base64 密钥
 *   encrypt &lt;key&gt; &lt;明文&gt;          加密，输出 ENC(...) 密文
 *   encrypt &lt;明文&gt;                密钥取环境变量 APP_CONFIG_KEY
 *   decrypt &lt;key&gt; &lt;ENC(...)&gt;      解密（换密钥批量重加密时辅助）
 *   decrypt &lt;ENC(...)&gt;            密钥取环境变量 APP_CONFIG_KEY
 * </pre>
 */
public final class ConfigEncryptor {

    private static final String KEY_ENV_VARIABLE = "APP_CONFIG_KEY";

    private ConfigEncryptor() {
    }

    static void execute(String[] args) {
        if (args.length == 0) {
            throw usageError();
        }
        switch (args[0]) {
            case "genkey" -> {
                requireArgCount(args, 1);
                System.out.println(ConfigCipher.generateKey());
            }
            case "encrypt" -> System.out.println(encrypt(args));
            case "decrypt" -> System.out.println(decrypt(args));
            default -> throw usageError();
        }
    }

    private static String encrypt(String[] args) {
        if (args.length == 3) {
            return ConfigCipher.encrypt(args[1], args[2]);
        }
        if (args.length == 2) {
            return ConfigCipher.encrypt(requireEnvKey(), args[1]);
        }
        throw usageError();
    }

    private static String decrypt(String[] args) {
        if (args.length == 3) {
            return ConfigCipher.decrypt(args[1], args[2]);
        }
        if (args.length == 2) {
            return ConfigCipher.decrypt(requireEnvKey(), args[1]);
        }
        throw usageError();
    }

    private static String requireEnvKey() {
        String key = System.getenv(KEY_ENV_VARIABLE);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("环境变量 " + KEY_ENV_VARIABLE + " 未设置，"
                    + "请先设置密钥，或改用带密钥参数的形式");
        }
        return key.trim();
    }

    private static void requireArgCount(String[] args, int expected) {
        if (args.length != expected) {
            throw usageError();
        }
    }

    private static IllegalArgumentException usageError() {
        return new IllegalArgumentException("""
                用法：
                  genkey                       生成 256-bit Base64 密钥
                  encrypt <key> <明文>          加密，输出 ENC(...) 密文
                  encrypt <明文>                密钥取环境变量 APP_CONFIG_KEY
                  decrypt <key> <ENC(...)>      解密
                  decrypt <ENC(...)>            密钥取环境变量 APP_CONFIG_KEY
                """);
    }

    public static void main(String[] args) {
        try {
            execute(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

}
