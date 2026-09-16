package io.github.oatelauser.springplus.calcite.memory.convert.impl;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverter;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import org.apache.calcite.sql.type.SqlTypeName;

/**
 * 时间类型转换器：将 java.time / java.util.Date / Calendar 统一转为 java.sql 类型，
 * 便于 Calcite 原生识别 DATE/TIME/TIMESTAMP。
 */
public class TemporalConverter implements TypeConverter {

    @Override
    public boolean supports(Class<?> javaType) {
        if (javaType == null) {
            return false;
        }
        return javaType == LocalDate.class
            || javaType == LocalTime.class
            || javaType == LocalDateTime.class
            || javaType == Instant.class
            || java.util.Date.class.isAssignableFrom(javaType)
            || javaType == Calendar.class;
    }

    @Override
    public SqlTypeName storageSqlType(Class<?> javaType) {
        if (javaType == LocalDate.class || javaType == java.sql.Date.class) {
            return SqlTypeName.DATE;
        }
        if (javaType == LocalTime.class || javaType == java.sql.Time.class) {
            return SqlTypeName.TIME;
        }
        return SqlTypeName.TIMESTAMP;
    }

    @Override
    public Object toStorage(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date d) {
            return d;
        }
        if (value instanceof java.sql.Time t) {
            return t;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts;
        }
        if (value instanceof LocalDate ld) {
            return Date.valueOf(ld);
        }
        if (value instanceof LocalTime lt) {
            return Time.valueOf(lt);
        }
        if (value instanceof LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        if (value instanceof Instant inst) {
            return Timestamp.from(inst);
        }
        if (value instanceof java.util.Date ud) {
            return new Timestamp(ud.getTime());
        }
        if (value instanceof Calendar cal) {
            return new Timestamp(cal.getTimeInMillis());
        }
        return value;
    }
}
