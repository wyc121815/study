package com.example.platform.conn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 守住一个踩过的坑：mysql 客户端的连接字符集默认是 latin1，而官方镜像执行
 * {@code docker-entrypoint-initdb.d} 里的脚本、以及我们用 {@code docker exec}
 * 跑迁移时都不会带 {@code --default-character-set}。脚本里只要出现中文（哪怕只是
 * 表注释）又没声明字符集，存进去就是双重编码的乱码。
 *
 * <p>所以规则很简单：带非 ASCII 字符的 SQL 文件必须显式 {@code SET NAMES utf8mb4}。</p>
 */
class SqlScriptCharsetTest {

    @Test
    void sqlScriptsWithNonAsciiMustDeclareCharset() throws IOException {
        Path deployDir = Path.of("..", "deploy", "mysql");
        assumeTrue(Files.isDirectory(deployDir), "找不到 deploy/mysql，跳过");

        List<Path> scripts;
        try (Stream<Path> paths = Files.walk(deployDir)) {
            scripts = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .toList();
        }
        assertThat(scripts).as("deploy/mysql 下应该至少有一个 SQL 脚本").isNotEmpty();

        for (Path script : scripts) {
            String content = Files.readString(script, StandardCharsets.UTF_8);
            boolean hasNonAscii = content.chars().anyMatch(c -> c > 127);
            if (!hasNonAscii) {
                continue;
            }
            assertThat(content)
                    .as("%s 含中文但没有声明 SET NAMES utf8mb4，灌库会变成乱码", script)
                    .containsPattern("(?i)SET\\s+NAMES\\s+utf8mb4");
        }
    }
}
