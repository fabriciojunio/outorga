package br.com.outorga;

import br.com.outorga.infrastructure.config.ConfiguracaoDaOutorga;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(ConfiguracaoDaOutorga.class)
@EnableScheduling
public class OutorgaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutorgaApplication.class, args);
    }
}
