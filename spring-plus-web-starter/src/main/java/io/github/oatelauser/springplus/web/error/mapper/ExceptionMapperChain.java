package io.github.oatelauser.springplus.web.error.mapper;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Mapper 责任链：按 {@link ExceptionMapper#order()} 排序后遍历，沿异常因果链（cause chain）
 * 找到第一个返回非 null 的翻译结果（设计文档 6.3）。
 * <p>
 * 本类是 {@code ExceptionOutputFactory} 在 P2 阶段（方法/类级注解、handler defaultDescriptor 都未命中）
 * 调用的核心组件。它把「多个 Mapper」和「异常因果链」两个维度织成一个双层循环：
 * <pre>
 *   for 每个异常节点（沿 cause 链向上，限深 10）:
 *       for 每个已排序 Mapper（order 升序）:
 *           命中即返回
 * </pre>
 *
 * <h3>排序规则</h3>
 * <p>
 * 启动期构造时按 {@link ExceptionMapper#order()} 升序排好，固化进 {@link #sortedMappers}，运行期不再排序。
 * 内置 Mapper 用负数（-1000 / -900 / {@code MAX_VALUE}），业务 Mapper 用正数，自然形成
 * 「异常类注解 → 业务自定义 → ServerStatus → 兜底」的顺序。
 *
 * <h3>因果链限深（MAX_DEPTH = 10）</h3>
 * <p>
 * 沿 {@code getCause()} 往上爬，但最多 10 层——防御业务代码构造超长或自引用异常链导致死循环。
 * 配合集合（用「对象身份」去重），即便出现 {@code a.getCause()==a} 的环也能安全终止。
 *
 * <h3>理论上的非空保证</h3>
 * <p>
 * {@code DefaultExceptionMapper}（order = {@code MAX_VALUE}）始终返回非 null，所以正常装配下
 * 本方法一定会在遍历到根异常时返回结果。返回 null 仅在「Mapper 列表为空」的极端配置下出现，
 * 引擎层会再做一次硬兜底（设计 5.2 P3）。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class ExceptionMapperChain {

    /**
     * 因果链最大遍历深度，防御超长 / 环状异常链。
     */
    private static final int MAX_DEPTH = 10;

    /**
     * 启动期按 order 升序排好的 Mapper 列表（不可变快照）。
     */
    private final List<ExceptionMapper> sortedMappers;

    /**
     * @param mappers 容器中所有 {@link ExceptionMapper} Bean（含内置 + 业务自定义）
     */
    public ExceptionMapperChain(List<ExceptionMapper> mappers) {
        // 拷贝一份再排序，避免改动入参；按 order 升序（小者优先）。
        List<ExceptionMapper> sorted = new ArrayList<>(mappers);
        sorted.sort(Comparator.comparingInt(ExceptionMapper::order));
        this.sortedMappers = List.copyOf(sorted);
    }

    /**
     * 沿异常因果链 + Mapper 链双层遍历，返回第一个非 null 翻译结果。
     * <p>
     * 优先级（v2.0 新增第一档「异常自带 mapper」）：对每个异常节点，先看它<b>自身</b>是否实现了
     * {@link ExceptionMapper}（{@code instanceof} 判定），是则直接调 {@code ex.map(ex, ctx)}——
     * 这是 Concern 1 的能力，让异常类能携带自己的结构化映射逻辑（如读自己的字段构造 details），
     * 优先级<b>高于</b>所有全局 Bean Mapper。然后再遍历全局排序好的 Mapper 链。
     *
     * @param rootEx 根异常
     * @param ctx    Mapper 上下文（request / handlerMethod / protocol）
     * @return 翻译出的错误描述；理论上不会为 null（兜底 Mapper 兜底）
     */
    @Nullable
    public ErrorDescriptor map(Throwable rootEx, ExceptionMapperContext ctx) {
        Set<Throwable> visited = new HashSet<>();
        Throwable current = rootEx;
        int depth = 0;
        // 三个终止条件：爬到 null、超过限深、遇到已访问过的异常（环检测）。
        while (current != null && depth++ < MAX_DEPTH && visited.add(current)) {
            // 第一档：异常自带 mapper（Concern 1，优先级最高）。
            // 异常类直接 implements ExceptionMapper，自身就是翻译器——天然能访问自己的字段，
            // 比 Bean Mapper 写强转 + 反射更直观、更类型安全。
            if (current instanceof ExceptionMapper selfMapper) {
                ErrorDescriptor descriptor = selfMapper.map(current, ctx);
                if (descriptor != null) {
                    return descriptor;
                }
            }
            // 第二档：全局已排序的 Mapper 链（按 order 升序遍历）。
            for (ExceptionMapper mapper : sortedMappers) {
                ErrorDescriptor descriptor = mapper.map(current, ctx);
                if (descriptor != null) {
                    return descriptor;
                }
            }
            current = current.getCause();
        }
        return null;
    }
}
