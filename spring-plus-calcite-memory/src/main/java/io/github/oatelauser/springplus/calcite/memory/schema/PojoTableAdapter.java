package io.github.oatelauser.springplus.calcite.memory.schema;

import io.github.oatelauser.springplus.calcite.memory.annotation.MemoryColumn;
import io.github.oatelauser.springplus.calcite.memory.annotation.MemoryId;
import io.github.oatelauser.springplus.calcite.memory.annotation.MemoryNested;
import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverter;
import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.exception.NestedMappingException;
import io.github.oatelauser.springplus.calcite.memory.exception.SchemaException;
import io.github.oatelauser.springplus.calcite.memory.exception.TypeException;
import io.github.oatelauser.springplus.calcite.memory.model.ColumnSpec;
import io.github.oatelauser.springplus.calcite.memory.model.NestedMode;
import io.github.oatelauser.springplus.calcite.memory.model.SqlType;
import io.github.oatelauser.springplus.calcite.memory.model.TableSchema;
import org.apache.calcite.sql.type.SqlTypeName;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * POJO 元数据适配器：反射扫描字段，结合 {@link MemoryColumn}/{@link MemoryId}/{@link MemoryNested}
 * 注解与 {@link TypeConverterRegistry} 推断 Schema 并提取行。
 *
 * <p>嵌套语义：
 * <ul>
 *   <li>单值嵌套对象默认 {@link NestedMode#FLATTEN}，递归扁平化为前缀列
 *       （buyer.address.city -&gt; buyer_address_city），支持深层路径，深度上限 {@value #MAX_FLATTEN_DEPTH}。</li>
 *   <li>集合字段必须显式 {@link MemoryNested}({@link NestedMode#CHILD_TABLE})，否则注册失败；
 *       声明后收集为 {@link ChildFieldBinding}，由会话层注册为独立子表（P2.3）。</li>
 *   <li>循环引用、列名冲突均抛 {@link NestedMappingException}，绝不自动加后缀消歧。</li>
 * </ul>
 */
public class PojoTableAdapter<T> implements MetadataAdapter<T> {

    /**
     * 扁平化最大深度，防止过深或无界嵌套导致列爆炸。
     */
    public static final int MAX_FLATTEN_DEPTH = 5;

    private final Class<T> elementType;
    private final TableSchema schema;
    private final List<Binding> bindings;
    private final List<ChildFieldBinding> childFields;

    public PojoTableAdapter(String tableName, Class<T> elementType, TypeConverterRegistry registry) {
        this.elementType = elementType;
        this.bindings = new ArrayList<>();
        this.childFields = new ArrayList<>();
        BuildContext ctx = new BuildContext(tableName, registry);
        Set<Class<?>> visited = new HashSet<>();
        visited.add(elementType);
        collectColumns(elementType, new ArrayList<>(), "", 0, visited, ctx);
        if (ctx.columns.isEmpty()) {
            throw new SchemaException("类型 " + elementType.getName() + " 无可注册字段");
        }
        this.schema = new TableSchema(tableName, ctx.columns);
    }

    @Override
    public TableSchema inferSchema() {
        return schema;
    }

    @Override
    public Object[] toRow(T item) {
        if (item == null) {
            throw new TypeException("POJO 行数据不能为 null（表 " + schema.getTableName() + "）");
        }
        Object[] row = new Object[bindings.size()];
        for (Binding b : bindings) {
            Object cur = item;
            for (Field f : b.path()) {
                try {
                    cur = f.get(cur);
                } catch (IllegalAccessException e) {
                    throw new TypeException("字段取值失败: " + f.getName(), e);
                }
                if (cur == null) {
                    break;
                }
            }
            row[b.index()] = (cur == null || b.converter() == null)
                    ? cur
                    : b.converter().toStorage(cur);
        }
        return row;
    }

    /**
     * @return CHILD_TABLE 声明的集合字段绑定，供会话层注册子表（P2.3）。
     */
    public List<ChildFieldBinding> childFields() {
        return childFields;
    }

    private void collectColumns(Class<?> type, List<Field> path, String prefix, int depth,
            Set<Class<?>> visited, BuildContext ctx) {
        if (depth > MAX_FLATTEN_DEPTH) {
            throw new NestedMappingException("扁平化深度超过上限 " + MAX_FLATTEN_DEPTH
                    + ": " + type.getName());
        }
        for (Field f : collectFields(type)) {
            f.setAccessible(true);
            MemoryColumn colAnno = f.getAnnotation(MemoryColumn.class);
            if (colAnno != null && colAnno.ignore()) {
                continue;
            }
            Class<?> ft = f.getType();
            String segName = toSnake(f.getName());
            if (isCollectionLike(ft)) {
                handleCollection(f, ft, path, ctx, segName);
                continue;
            }
            if (Map.class.isAssignableFrom(ft)) {
                throw new NestedMappingException("字段 " + f.getName()
                        + " 为 Map 类型，POJO 内扁平化暂不支持（请用 @MemoryColumn(ignore=true) 跳过）");
            }
            if (isNestedObject(ft, ctx.registry)) {
                handleNested(f, ft, path, prefix, depth, visited, ctx, colAnno, segName);
                continue;
            }
            addScalar(f, ft, path, prefix, colAnno, ctx, segName);
        }
    }

    private void handleNested(Field f, Class<?> ft, List<Field> path, String prefix, int depth,
            Set<Class<?>> visited, BuildContext ctx, MemoryColumn colAnno, String segName) {
        if (colAnno != null && !colAnno.value().isEmpty()) {
            throw new NestedMappingException("字段 " + f.getName()
                    + " 同时标注列名与 @MemoryNested，语义冲突");
        }
        MemoryNested nestedAnno = f.getAnnotation(MemoryNested.class);
        NestedMode mode = nestedAnno != null ? nestedAnno.mode() : NestedMode.FLATTEN;
        if (mode != NestedMode.FLATTEN) {
            throw new NestedMappingException("字段 " + f.getName()
                    + " 嵌套模式 " + mode + " 暂不支持（当前仅支持 FLATTEN）");
        }
        if (!visited.add(ft)) {
            throw new NestedMappingException("循环引用检测: 嵌套链路出现 " + ft.getName()
                    + "（字段 " + f.getName() + "）");
        }
        String nestedPrefix = (nestedAnno != null && !nestedAnno.prefix().isEmpty())
                ? nestedAnno.prefix()
                : segName;
        String childPrefix = prefix + nestedPrefix + "_";
        List<Field> childPath = new ArrayList<>(path);
        childPath.add(f);
        collectColumns(ft, childPath, childPrefix, depth + 1, visited, ctx);
        visited.remove(ft);
    }

    private void handleCollection(Field f, Class<?> ft, List<Field> path,
            BuildContext ctx, String segName) {
        MemoryNested nestedAnno = f.getAnnotation(MemoryNested.class);
        if (nestedAnno == null) {
            throw new NestedMappingException("集合字段 " + f.getName()
                    + " 必须显式 @MemoryNested(mode=CHILD_TABLE) 声明，或用 @MemoryColumn(ignore=true) 跳过");
        }
        NestedMode mode = nestedAnno.mode();
        if (mode != NestedMode.CHILD_TABLE) {
            throw new NestedMappingException("集合字段 " + f.getName()
                    + " 嵌套模式 " + mode + " 暂不支持（当前仅支持 CHILD_TABLE）");
        }
        Class<?> childType = resolveCollectionElementType(f, ft);
        if (childType == null) {
            throw new NestedMappingException("集合字段 " + f.getName()
                    + " 无法解析元素类型，请使用参数化集合");
        }
        String childTable = (nestedAnno.table() != null && !nestedAnno.table().isEmpty())
                ? nestedAnno.table()
                : ctx.tableName + "$" + segName;
        List<Field> parentPath = new ArrayList<>(path);
        parentPath.add(f);
        childFields.add(new ChildFieldBinding(parentPath, childType, childTable,
                nestedAnno.parentKey(), nestedAnno.foreignKey()));
    }

    private void addScalar(Field f, Class<?> ft, List<Field> path, String prefix,
            MemoryColumn colAnno, BuildContext ctx, String segName) {
        String name = (colAnno != null && !colAnno.value().isEmpty())
                ? colAnno.value()
                : prefix + segName;
        if (!ctx.seen.add(name)) {
            throw new NestedMappingException("列名冲突: " + name + "（字段 " + f.getName() + "）");
        }
        boolean pk = f.isAnnotationPresent(MemoryId.class);
        boolean nullable = !ft.isPrimitive();
        TypeConverter conv = ctx.registry.resolve(ft);
        SqlTypeName sqlName = conv != null
                ? conv.storageSqlType(ft)
                : ScalarTypeMapping.mapJavaToSql(ft, f.getName());
        SqlType sqlType = SqlType.of(sqlName, nullable);
        ctx.columns.add(ColumnSpec.builder()
                .name(name)
                .sqlType(sqlType)
                .sourcePath(buildSourcePath(path, f))
                .primaryKey(pk)
                .build());
        List<Field> bindingPath = new ArrayList<>(path);
        bindingPath.add(f);
        bindings.add(new Binding(bindingPath, conv, ctx.columns.size() - 1));
    }

    private static List<Field> collectFields(Class<?> type) {
        List<Field> all = new ArrayList<>();
        Class<?> cur = type;
        while (cur != null && cur != Object.class) {
            for (Field f : cur.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
                    continue;
                }
                all.add(f);
            }
            cur = cur.getSuperclass();
        }
        return all;
    }

    private static Class<?> resolveCollectionElementType(Field f, Class<?> ft) {
        if (ft.isArray()) {
            Class<?> comp = ft.getComponentType();
            return (comp != null && !comp.isPrimitive()) ? comp : null;
        }
        Type generic = f.getGenericType();
        if (generic instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class<?> c) {
                return c;
            }
        }
        return null;
    }

    private static boolean isCollectionLike(Class<?> t) {
        return t.isArray()
                || (Iterable.class.isAssignableFrom(t) && !Map.class.isAssignableFrom(t));
    }

    private static boolean isNestedObject(Class<?> t, TypeConverterRegistry registry) {
        if (t.isPrimitive() || t == String.class || t == char.class || t == Character.class) {
            return false;
        }
        if (Number.class.isAssignableFrom(t)) {
            return false;
        }
        if (t == Boolean.class || t == byte[].class || t == java.util.UUID.class) {
            return false;
        }
        if (t.isEnum() || Enum.class.isAssignableFrom(t)) {
            return false;
        }
        if (java.time.temporal.TemporalAccessor.class.isAssignableFrom(t)) {
            return false;
        }
        if (java.util.Date.class.isAssignableFrom(t) || java.util.Calendar.class.isAssignableFrom(t)) {
            return false;
        }
        return registry.resolve(t) == null;
    }

    private static String buildSourcePath(List<Field> path, Field leaf) {
        if (path.isEmpty()) {
            return leaf.getName();
        }
        StringBuilder sb = new StringBuilder();
        for (Field f : path) {
            sb.append(f.getName()).append('.');
        }
        sb.append(leaf.getName());
        return sb.toString();
    }

    private static String toSnake(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 取值绑定：从根对象沿字段路径取值，转换后写入指定列下标。
     */
    private record Binding(List<Field> path, TypeConverter converter, int index) {
    }

    /**
     * CHILD_TABLE 集合字段的待建子表描述。
     */
    public record ChildFieldBinding(List<Field> parentPath, Class<?> childElementType,
        String childTableName, String parentKeyHint, String foreignKeyHint) {
    }

    private static final class BuildContext {
        final String tableName;
        final TypeConverterRegistry registry;
        final List<ColumnSpec> columns = new ArrayList<>();
        final Set<String> seen = new HashSet<>();

        BuildContext(String tableName, TypeConverterRegistry registry) {
            this.tableName = tableName;
            this.registry = registry;
        }
    }
}
