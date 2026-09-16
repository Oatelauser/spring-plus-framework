package io.github.oatelauser.springplus.calcite.memory.model;

/**
 * 嵌套字段的处理模式。
 * <ul>
 *   <li>{@link #FLATTEN} - 单值嵌套对象默认行为：递归扁平化为前缀列（buyer.address.city -> buyer_address_city）</li>
 *   <li>{@link #CHILD_TABLE} - 集合字段：拆分为独立子表，通过外键关联父表</li>
 *   <li>{@link #ROW} / {@link #ARRAY} / {@link #MULTISET} - 结构化保留（实验性，当前版本不支持）</li>
 * </ul>
 */
public enum NestedMode {

    FLATTEN,
    CHILD_TABLE,
    ROW,
    ARRAY,
    MULTISET

}
