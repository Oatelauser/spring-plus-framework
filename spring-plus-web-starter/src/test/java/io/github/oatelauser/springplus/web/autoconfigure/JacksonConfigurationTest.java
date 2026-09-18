package io.github.oatelauser.springplus.web.autoconfigure;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SpringPlusWebAutoConfiguration.JacksonConfiguration} 时间格式与 Long→String 开关的行为验证。
 * <p>
 * 直接驱动静态 customizer 方法构造独立 JsonMapper，不依赖 Spring 上下文。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
class JacksonConfigurationTest {

    private static JsonMapper mapper(String datetime, String date, String time, boolean longToString) {
        JsonMapper.Builder builder = JsonMapper.builder();
        SpringPlusWebAutoConfiguration.JacksonConfiguration.jacksonWebMapperBuilderCustomizer(
                "", datetime, date, time, longToString).customize(builder);
        return builder.build();
    }

    @Test
    void defaultPatternsApplyToJavaTime() {
        JsonMapper mapper = mapper(
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_DATETIME_PATTERN,
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_DATE_FORMAT,
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_TIME_FORMAT, false);

        assertEquals("\"2026-09-16 15:30:45\"",
                mapper.writeValueAsString(LocalDateTime.of(2026, 9, 16, 15, 30, 45)));
        assertEquals("\"2026-09-16\"", mapper.writeValueAsString(LocalDate.of(2026, 9, 16)));
        assertEquals("\"15:30:45\"", mapper.writeValueAsString(LocalTime.of(15, 30, 45)));
    }

    @Test
    void customPatternsOverrideDefaults() {
        JsonMapper mapper = mapper("yyyy/MM/dd HH:mm", "yyyy/MM/dd", "HH:mm", false);

        assertEquals("\"2026/09/16 15:30\"",
                mapper.writeValueAsString(LocalDateTime.of(2026, 9, 16, 15, 30, 45)));
        assertEquals("\"2026/09/16\"", mapper.writeValueAsString(LocalDate.of(2026, 9, 16)));
        assertEquals("\"15:30\"", mapper.writeValueAsString(LocalTime.of(15, 30, 45)));
    }

    @Test
    void utilDateSerializesAsTimestampByLegacyDesign() {
        // 旧项目显式 enable(WRITE_DATES_AS_TIMESTAMPS)：java.util.Date 输出 epoch millis 数字。
        // 这是被保留的原有功能行为（迁移只改架构不改行为）。
        JsonMapper mapper = mapper(
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_DATETIME_PATTERN,
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_DATE_FORMAT,
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_TIME_FORMAT, false);

        Date date = new GregorianCalendar(2026, 8, 16, 15, 30, 45).getTime();
        assertTrue(mapper.writeValueAsString(date).matches("^\\d+$"),
                "Date 应序列化为数字时间戳，实际: " + mapper.writeValueAsString(date));
    }

    @Test
    void longToStringSwitchControlsLongSerialization() {
        JsonMapper off = mapper(
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_DATETIME_PATTERN,
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_DATE_FORMAT,
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_TIME_FORMAT, false);
        assertEquals("123", off.writeValueAsString(123L));

        JsonMapper on = mapper(
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_DATETIME_PATTERN,
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_DATE_FORMAT,
                SpringPlusWebAutoConfiguration.JacksonConfiguration.DEFAULT_TIME_FORMAT, true);
        assertEquals("\"1937621958420111361\"", on.writeValueAsString(1937621958420111361L),
                "开启后 Long 输出字符串（防 JS 精度丢失）");
        assertEquals("\"42\"", on.writeValueAsString(42L));
    }

}
