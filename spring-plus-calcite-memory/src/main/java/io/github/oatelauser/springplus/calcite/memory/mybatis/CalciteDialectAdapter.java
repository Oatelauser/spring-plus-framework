package io.github.oatelauser.springplus.calcite.memory.mybatis;

/**
 * Calcite 方言适配：为消费方提供程序化分页（{@code LIMIT}/{@code OFFSET}）与标识符引用能力。
 *
 * <p>Calcite 支持标准 {@code LIMIT}/{@code OFFSET} 分页与双引号标识符引用。本类封装为无状态工具，
 * 用于不便写死分页 SQL 的动态拼装场景（如外部入参分页）。MyBatis Mapper 内可直接书写 {@code LIMIT/OFFSET}，
 * 无需经过本类。</p>
 *
 * <p>线程安全：无状态，可被多线程并发使用。</p>
 */
public final class CalciteDialectAdapter {

    /** Calcite 标识符引用字符：双引号。 */
    public static final char IDENTIFIER_QUOTE = '"';

    /**
     * 追加分页：{@code LIMIT limit}，{@code offset > 0} 时附加 {@code OFFSET offset}。
     *
     * @param sql    原始 SQL（不含分页子句）
     * @param offset 偏移行数，0 表示从头
     * @param limit  最大返回行数，必须为正
     * @return 追加分页子句后的 SQL
     */
    public String paginate(String sql, long offset, long limit) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须为正: " + limit);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset 不能为负: " + offset);
        }
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        StringBuilder sb = new StringBuilder(trimmed.length() + 24).append(trimmed);
        sb.append(" LIMIT ").append(limit);
        if (offset > 0) {
            sb.append(" OFFSET ").append(offset);
        }
        return sb.toString();
    }

    /**
     * 双引号引用标识符，内部双引号转义为两个双引号。
     */
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("标识符不能为空");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
