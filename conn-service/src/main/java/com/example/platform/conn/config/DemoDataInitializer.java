package com.example.platform.conn.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.platform.common.core.util.AesCipher;
import com.example.platform.conn.entity.DbConnection;
import com.example.platform.conn.entity.MetricDefinition;
import com.example.platform.conn.enums.DbType;
import com.example.platform.conn.repository.DbConnectionRepository;
import com.example.platform.conn.repository.MetricDefinitionRepository;

/**
 * 首次启动时登记演示数据源与演示指标，让查询台和指标页一上手就有东西可看。
 *
 * <p>对应 {@code deploy/mysql/init/02-demo-data.sql} 里的 {@code platform_demo}
 * 库与只读账号。创建是幂等的：连接按名字判重，指标按名字判重，已存在就跳过，
 * 不会覆盖用户后续的修改。生产环境可以设 {@code APP_DEMO_ENABLED=false} 关掉。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    /** 演示指标：查询都落在 platform_demo.sales_daily 上。 */
    private record DemoMetric(String name, String description, String sql) {
    }

    private static final List<DemoMetric> DEMO_METRICS = List.of(
            new DemoMetric("每日销售额趋势",
                    "最近 30 天的支付金额与支付笔数，按日汇总",
                    """
                            SELECT stat_date, ROUND(SUM(amount), 2) AS amount, SUM(pay_cnt) AS pay_cnt
                            FROM sales_daily
                            WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
                            GROUP BY stat_date
                            ORDER BY stat_date
                            """),
            new DemoMetric("各渠道支付成功率",
                    "支付成功数 / 下单数，按渠道对比",
                    """
                            SELECT channel, SUM(order_cnt) AS order_cnt, SUM(pay_cnt) AS pay_cnt,
                                   ROUND(SUM(pay_cnt) / SUM(order_cnt) * 100, 2) AS pay_rate
                            FROM sales_daily
                            GROUP BY channel
                            ORDER BY pay_rate DESC
                            """),
            new DemoMetric("各地区销售分布",
                    "各大区的累计支付金额与支付笔数",
                    """
                            SELECT region, ROUND(SUM(amount), 2) AS amount, SUM(pay_cnt) AS pay_cnt
                            FROM sales_daily
                            GROUP BY region
                            ORDER BY amount DESC
                            """),
            new DemoMetric("渠道与地区销售额对比",
                    "渠道 × 大区的支付金额，用于分组对比",
                    """
                            SELECT channel, region, ROUND(SUM(amount), 2) AS amount
                            FROM sales_daily
                            GROUP BY channel, region
                            ORDER BY channel, region
                            """));

    @Bean
    public ApplicationRunner initDemoData(DbConnectionRepository connectionRepository,
                                          MetricDefinitionRepository metricRepository,
                                          AesCipher cipher,
                                          @Value("${app.demo.connection-name:演示-销售库}") String connectionName,
                                          @Value("${app.demo.host:127.0.0.1}") String host,
                                          @Value("${app.demo.port:3306}") int port,
                                          @Value("${app.demo.database:platform_demo}") String database,
                                          @Value("${app.demo.username:demo_reader}") String username,
                                          @Value("${app.demo.password:demo123}") String password) {
        return args -> {
            DbConnection datasource = connectionRepository.findByName(connectionName).orElse(null);
            if (datasource == null) {
                DbConnection entity = new DbConnection();
                entity.setName(connectionName);
                entity.setDbType(DbType.MYSQL.name());
                entity.setHost(host);
                entity.setPort(port);
                entity.setDatabaseName(database);
                entity.setUsername(username);
                entity.setPasswordCipher(cipher.encrypt(password));
                entity.setRemark("演示数据源，只读账号，随平台初始化创建");
                entity.setQueryEnabled(true);
                entity.setStatus(DbConnection.STATUS_UNKNOWN);
                datasource = connectionRepository.save(entity);
                log.warn("已创建演示数据源: {}（{}/{}，只读账号）", connectionName, database, username);
            }

            int created = 0;
            for (DemoMetric demo : DEMO_METRICS) {
                if (metricRepository.existsByName(demo.name())) {
                    continue;
                }
                MetricDefinition metric = new MetricDefinition();
                metric.setName(demo.name());
                metric.setDescription(demo.description());
                metric.setDatasourceId(datasource.getId());
                metric.setSqlText(demo.sql());
                metric.setStatus(MetricDefinition.STATUS_ENABLED);
                metricRepository.save(metric);
                created++;
            }
            if (created > 0) {
                log.info("已创建 {} 个演示指标（数据源: {}）", created, connectionName);
            }
        };
    }
}
