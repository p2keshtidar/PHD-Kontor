package de.frauas.girokonto.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Alle eigenen Einstellungen aus application.yml (Präfix "app"). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Bank bank,
        long startBalanceCents,
        Login login,
        Transfer transfer,
        Card card,
        Subaccounts subaccounts,
        Admin admin,
        Simulation simulation,
        Cors cors) {

    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {}

    public record Bank(String blz, String name) {}

    public record Login(int maxFailedAttempts, long lockMinutes) {}

    public record Transfer(long dailyLimitCents, long maxAmountCents) {}

    public record Card(String encryptionKey, String bin, long defaultDailyLimitCents, long maxDailyLimitCents) {}

    public record Subaccounts(int maxPerUser) {}

    public record Admin(String email, String password) {}

    public record Simulation(String zone) {}

    public record Cors(List<String> allowedOriginPatterns) {}
}
