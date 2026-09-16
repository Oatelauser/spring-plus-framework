package io.github.oatelauser.springplus.calcite.memory.benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH 入口：执行 {@link MemoryQueryBenchmark} 全部基准。
 *
 * <p>用法（编译后）：</p>
 * <pre>
 * mvn -f apartment-framework/spring-calcite-memory-framework/pom.xml test-compile
 * mvn -f apartment-framework/spring-calcite-memory-framework/pom.xml \
 *     exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.oatelauser.springplus.calcite.memory.benchmark.BenchmarkRunner
 * </pre>
 *
 * <p>参数透传：命令行参数会追加到 JMH Options（如 {@code -p scale=10000 -f 2}）。</p>
 */
public final class BenchmarkRunner {

    private BenchmarkRunner() {
    }

    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        // 命令行参数经 CommandLineOptions 解析；默认包含本类全部基准，CLI 额外 include 追加
        CommandLineOptions cli = new CommandLineOptions(args);
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include(MemoryQueryBenchmark.class.getSimpleName());
        for (String include : cli.getIncludes()) {
            builder = builder.include(include);
        }
        new Runner(builder.build()).run();
    }
}
