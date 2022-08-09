package io.github.brawaru.aessentialsxafkdelay;

import java.util.Optional;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

public class Config {
  private static final int AFK_DELAY_DEFAULT = 10;

  private static final int TIMER_TICK_RATE_DEFAULT = 20;

  private final AEssentialsXAFKDelayPlugin plugin;

  private ConfigurationSection messages;

  private int afkDelay = AFK_DELAY_DEFAULT * 1000;

  private int timerTickRate = TIMER_TICK_RATE_DEFAULT;

  public Config(AEssentialsXAFKDelayPlugin plugin) {
    this.plugin = plugin;
  }

  public void reloadConfig(Configuration configuration) {
    messages = configuration.getConfigurationSection("messages");

    int $afkDelay = configuration.getInt("activation-delay", AFK_DELAY_DEFAULT);

    if ($afkDelay < 1) {
      plugin
          .getLogger()
          .warning(
              () ->
                  String.format(
                      "Activation delay is below acceptable range. It will be reset to default value (%s)",
                      AFK_DELAY_DEFAULT));
    } else {
      afkDelay = $afkDelay * 1000;
    }

    int $timerTickRate = configuration.getInt("timer-tick-rate", TIMER_TICK_RATE_DEFAULT);

    if ($timerTickRate < 1) {
      plugin
          .getLogger()
          .warning(
              () ->
                  String.format(
                      "Timer tick rate is below acceptable range. It will be reset to default value (%s)",
                      TIMER_TICK_RATE_DEFAULT));

      timerTickRate = TIMER_TICK_RATE_DEFAULT;
    } else if ($timerTickRate > (afkDelay * 20)) {
      plugin
          .getLogger()
          .warning(
              () ->
                  String.format(
                      "Timer tick rate exceeds activation delay. It will be reset to default value (%s)",
                      TIMER_TICK_RATE_DEFAULT));

      timerTickRate = TIMER_TICK_RATE_DEFAULT;
    } else {
      timerTickRate = $timerTickRate;
    }
  }

  /** @return AFK delay in milliseconds. */
  public int afkDelay() {
    return afkDelay;
  }

  /** @return AFK activation timer tick rate. */
  public int timerTickRate() {
    return timerTickRate;
  }

  public Optional<String> getMessage(@NotNull String localeCode, @NotNull String key) {
    return Optional.ofNullable(messages.getConfigurationSection(localeCode)).map(m -> m.getString(key));
  }
}
