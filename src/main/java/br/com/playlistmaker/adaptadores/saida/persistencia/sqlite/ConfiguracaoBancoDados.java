package br.com.playlistmaker.adaptadores.saida.persistencia.sqlite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

@Configuration
public class ConfiguracaoBancoDados {

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(databaseUrl);
        return dataSource;
    }

    @Bean
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }
}
