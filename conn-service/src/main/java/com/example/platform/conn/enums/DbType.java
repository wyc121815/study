package com.example.platform.conn.enums;

import java.util.Arrays;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;

/**
 * 支持管理的数据库类型。
 *
 * <p>新增一种数据库 = 加一个枚举项 + 在 pom 里加驱动依赖，不用改业务代码。</p>
 */
public enum DbType {

    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://%s:%d/%s", 3306,
            "useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=10000"),

    POSTGRESQL("PostgreSQL", "org.postgresql.Driver",
            "jdbc:postgresql://%s:%d/%s", 5432,
            "connectTimeout=5&socketTimeout=10");

    private final String label;
    private final String driverClass;
    private final String urlTemplate;
    private final int defaultPort;
    private final String timeoutParams;

    DbType(String label, String driverClass, String urlTemplate, int defaultPort, String timeoutParams) {
        this.label = label;
        this.driverClass = driverClass;
        this.urlTemplate = urlTemplate;
        this.defaultPort = defaultPort;
        this.timeoutParams = timeoutParams;
    }

    public String label() {
        return label;
    }

    public String driverClass() {
        return driverClass;
    }

    public int defaultPort() {
        return defaultPort;
    }

    public String timeoutParams() {
        return timeoutParams;
    }

    /** 拼出 JDBC URL；额外参数由用户在界面上填写。 */
    public String buildUrl(String host, int port, String databaseName, String extraParams) {
        String database = databaseName == null ? "" : databaseName.trim();
        StringBuilder url = new StringBuilder(String.format(urlTemplate, host, port, database));

        StringBuilder params = new StringBuilder(timeoutParams);
        if (extraParams != null && !extraParams.isBlank()) {
            String trimmed = extraParams.trim().replaceAll("^[?&]+", "");
            if (!trimmed.isEmpty()) {
                params.append('&').append(trimmed);
            }
        }
        url.append('?').append(params);
        return url.toString();
    }

    public static DbType of(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据库类型不能为空");
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "不支持的数据库类型: " + code));
    }
}
