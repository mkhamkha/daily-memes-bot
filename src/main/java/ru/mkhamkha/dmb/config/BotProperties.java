package ru.mkhamkha.dmb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "bot-properties")
public class BotProperties {

    private String name;
    private String token;
}
