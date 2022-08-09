package io.github.brawaru.aessentialsxafkdelay;

import com.earth2me.essentials.User;
import net.ess3.api.events.AfkStatusChangeEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DelayedAFKTimer extends BukkitRunnable {

  /**
   * The distance that player can move before their request gets rejected.
   */
  private static final double MOVE_THRESHOLD = 1;

  private final Config config;
  private final User user;
  private final AEssentialsXAFKDelayPlugin plugin;
  private final Location initialLocation;

  @Nullable private final String message;

  private final AfkStatusChangeEvent.Cause cause;
  boolean decisionMade = false;
  private long timeRequested;
  private double lastHealth;

  public DelayedAFKTimer(
      @NotNull User user,
      @Nullable String message,
      @NotNull AfkStatusChangeEvent.Cause cause,
      @NotNull AEssentialsXAFKDelayPlugin plugin) {
    this.user = user;
    this.message = message;
    this.config = plugin.getOwnConfig();
    this.cause = cause;

    var player = user.getBase();
    this.initialLocation = player.getLocation();
    this.lastHealth = player.getHealth();
    this.plugin = plugin;
  }

  /**
   * Checks if player has surpassed the threshold distance for request to be considered failed.
   * @param player Current players instance from which location is obtained.
   * @return Whether the player has surpassed the threshold or not.
   */
  private boolean hasMoved(@NotNull Player player) {
    Location currentLocation = player.getLocation();

    return Math.abs(initialLocation.getX() - currentLocation.getX()) >= MOVE_THRESHOLD
        || Math.abs(initialLocation.getY() - currentLocation.getY()) >= MOVE_THRESHOLD
        || Math.abs(initialLocation.getZ() - currentLocation.getZ()) >= MOVE_THRESHOLD;
  }

  @Override
  public void run() {
    // Not sure whether this can change or not, so not storing the player and instead getting base every time.
    var player = user.getBase();

    if (!player.isOnline()) {
      rejectAfk(false);

      return;
    }

    if (hasMoved(player)) {
      rejectAfk(true);

      return;
    }

    var playerHealth = player.getHealth();

    if (playerHealth < lastHealth) {
      rejectAfk(true);

      return;
    }

    // Allow healing while waiting for the status to apply
    if (playerHealth > lastHealth) {
      lastHealth = playerHealth;
    }

    final var difference = System.currentTimeMillis() - timeRequested;

    if (difference >= config.afkDelay()) {
      allowAfk();
    }
  }

  private void rejectAfk(boolean withMessage) {
    decisionMade = true;

    if (withMessage) {
      var player = user.getBase();
      plugin.adventure().player(player).sendMessage(plugin.translate(player, "afk-canceled"));
    }

    cancel();
  }

  private void allowAfk() {
    decisionMade = true;

    plugin.toggleUserAfkOn(user, message, cause);

    cancel();
  }

  @Override
  public synchronized void cancel() throws IllegalStateException {
    try {
      super.cancel();
    } finally {
      plugin.unregisterTimer(user, this);

      if (!decisionMade) {
        var player = user.getBase();
        plugin.adventure().player(player).sendMessage(plugin.translate(player, "afk-canceled"));
      }
    }
  }

  public void start() {
    timeRequested = System.currentTimeMillis();

    plugin.registerTimer(user, this);

    runTaskTimer(plugin, 0, config.timerTickRate());
  }
}
