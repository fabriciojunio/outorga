package br.com.mirante;

import br.com.mirante.infrastructure.config.ConfiguracaoDoMirante;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(ConfiguracaoDoMirante.class)
@EnableScheduling
public class MiranteApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiranteApplication.class, args);
    }
}
