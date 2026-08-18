package com.leaseguard.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Supplies the {@link Clock} the whole application uses for "today". The demo profile fixes
 * this to a configurable date so risk scores and boundary tests stay deterministic regardless
 * of when the application is actually run; a production profile would instead bind
 * {@code leaseguard.demo.as-of-date} to the real system date.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock demoClock(DemoProperties demoProperties) {
        return Clock.fixed(demoProperties.asOfDate().atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    @Validated
    @ConfigurationProperties(prefix = "leaseguard.demo")
    public record DemoProperties(@NotNull LocalDate asOfDate, String bundledCsvPath) {
    }
}
