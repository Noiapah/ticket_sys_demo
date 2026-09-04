package no.telefonhjelp.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.format.DateTimeFormatter;

@Configuration
public class DatabaseConfig {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    DataSource dataSource(StoragePaths paths) {
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + paths.database().toAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setConnectionInitSql("PRAGMA foreign_keys=ON");
        return new HikariDataSource(config);
    }

    @Bean
    Flyway flyway(DataSource dataSource, StoragePaths paths) throws Exception {
        var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        if (Files.exists(paths.database()) && Files.size(paths.database()) > 0 && flyway.info().pending().length > 0) {
            var name = "pre-migration-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".db";
            Files.copy(paths.database(), paths.backups().resolve(name), StandardCopyOption.COPY_ATTRIBUTES);
        }
        flyway.migrate();
        return flyway;
    }
}
