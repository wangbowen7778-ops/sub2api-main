package com.sub2api.module.common.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * PostgreSQL TIMESTAMPTZ 类型处理器
 * 处理 PostgreSQL 的 TIMESTAMPTZ（带时区的时间戳）与 Java OffsetDateTime 的转换
 */
public class OffsetDateTimeTypeHandler extends BaseTypeHandler<OffsetDateTime> {

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OffsetDateTime parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            ps.setNull(i, Types.OTHER);
        } else {
            ps.setString(i, parameter.toString());
        }
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return parseOffsetDateTime(value);
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return parseOffsetDateTime(value);
    }

    @Override
    public OffsetDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return parseOffsetDateTime(value);
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return OffsetDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            String withoutZone = value.substring(0, Math.max(value.lastIndexOf('+'), value.lastIndexOf('-')));
            if (withoutZone.length() >= 19) {
                String dateTimePart = withoutZone.substring(0, 19);
                return java.time.LocalDateTime.parse(dateTimePart).atOffset(ZoneOffset.UTC);
            }
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("Cannot parse OffsetDateTime from: " + value);
    }
}
