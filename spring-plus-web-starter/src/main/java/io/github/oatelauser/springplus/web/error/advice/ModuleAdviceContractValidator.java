package io.github.oatelauser.springplus.web.error.advice;

import io.github.oatelauser.springplus.boot.utils.ControllerAdviceScanUtils;
import io.github.oatelauser.springplus.boot.utils.ControllerAdviceScanUtils.AdviceBeanDescriptor;
import io.github.oatelauser.springplus.web.autoconfigure.GlobalExceptionAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 模块级 advice 契约的启动期校验器（全部告警，不拦截启动）。
 * <p>
 * 多 advice 共存机制依赖 Spring 原生语义：advice 按 order 排序后<b>逐个咨询、
 * 第一个能匹配的 advice 直接赢</b>——跨 advice 没有"最精确匹配"。据此立两条契约
 * （设计共识 Q8/Q12，校验逻辑复用 {@link ControllerAdviceScanUtils}）：
 *
 * <ul>
 *   <li><b>规则一</b>：非全局 advice 声明 {@code Exception.class}/{@code Throwable.class}
 *       级 {@code @ExceptionHandler} 会遮蔽全局兜底 {@link GlobalExceptionAdvice} 的全部
 *       具体 handler（validation / DAO / 请求解析等约 20 个），并吞掉 security 的
 *       denied 透传语义——仅当确有意整体接管（如 BFF 网关）才如此声明；</li>
 *   <li><b>规则二</b>：非全局 advice 未声明显式 {@code @Order} 时与全局兜底同为
 *       {@code LOWEST_PRECEDENCE}，先后由 bean 注册顺序决定（不确定性）——模块级
 *       advice 应显式声明，取值须小于 {@code Ordered.LOWEST_PRECEDENCE}（模块段位
 *       约定 0~900，spring-plus-security-starter = 100）。</li>
 * </ul>
 * <p>
 * {@code org.springframework.*} 的 Spring 自带 advice（如
 * {@code ProblemDetailsExceptionHandler}）不适用规则二：它们只声明窄类型、与全局
 * 兜底无重叠，无序并不产生语义问题，告警只会是噪音。
 * <p>
 * 双通道注册：本类保留 {@code @Component}（组件扫描可及的应用），starter 装配由
 * {@code ExceptionHandlingAutoConfiguration} 以 {@code @ConditionalOnMissingBean}
 * 兜底（对齐 error 包双通道惯例）。触发时机为实现 {@link SmartInitializingSingleton}：
 * 全部单例就绪后执行，早于应用就绪事件。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-18
 * @since 1.1
 */
@Component
public class ModuleAdviceContractValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ModuleAdviceContractValidator.class);

    /** Spring 自带 advice（如 ProblemDetailsExceptionHandler）不适用规则二：窄类型与全局兜底无重叠 */
    private static final String SPRING_FRAMEWORK_PACKAGE = "org.springframework.";

    private final ConfigurableListableBeanFactory beanFactory;

    public ModuleAdviceContractValidator(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate().forEach(log::warn);
    }

    /**
     * @return 全部契约告警消息（每 bean 每规则一条）；测试与自检复用
     */
    List<String> validate() {
        List<String> warnings = new ArrayList<>();
        for (AdviceBeanDescriptor advice : ControllerAdviceScanUtils.findAdviceBeans(beanFactory)) {
            Class<?> type = advice.beanType();
            // 全局兜底本体（含业务自定义子类）是契约的授权方，豁免
            if (GlobalExceptionAdvice.class.isAssignableFrom(type)) {
                continue;
            }
            Set<Class<? extends Throwable>> handlerTypes =
                    ControllerAdviceScanUtils.exceptionHandlerExceptionTypes(type);
            if (handlerTypes.contains(Exception.class) || handlerTypes.contains(Throwable.class)) {
                warnings.add("[advice-contract] bean '" + advice.beanName() + "' (" + type.getName()
                        + ") 声明了 Exception/Throwable 级 @ExceptionHandler——将遮蔽全局兜底 GlobalExceptionAdvice"
                        + " 的全部具体 handler（validation/DAO/请求解析等），并吞掉 security denied 透传语义；"
                        + "仅当确有意整体接管全局兜底时才这样声明，否则请改为窄异常类型");
            }
            if (!ControllerAdviceScanUtils.hasExplicitOrder(beanFactory, advice.beanName(), type)
                    && !type.getName().startsWith(SPRING_FRAMEWORK_PACKAGE)) {
                warnings.add("[advice-contract] bean '" + advice.beanName() + "' (" + type.getName()
                        + ") 未声明 @Order——与全局兜底同为 LOWEST_PRECEDENCE，先后由 bean 注册顺序决定；"
                        + "模块级 advice 应显式 @Order，取值小于 Ordered.LOWEST_PRECEDENCE（模块段位约定 0~900）");
            }
        }
        return warnings;
    }

}
