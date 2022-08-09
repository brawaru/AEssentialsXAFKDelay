package io.github.brawaru.aessentialsxafkdelay;

import com.earth2me.essentials.User;
import com.earth2me.essentials.utils.DateUtil;
import net.ess3.api.IEssentials;
import net.ess3.api.events.AfkStatusChangeEvent;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings({"deprecation", "RedundantSuppression"})
public final class AEssentialsXAFKDelayPlugin extends JavaPlugin {
  // TODO: move translation code to its own class.
  private static final String DEFAULT_LOCALE = "en_us";

  /**
   * Essentials plugin.
   */
  @Nullable private IEssentials essentials;

  /**
   * Original {@code afk} command executor.
   */
  @Nullable private CommandExecutor essentialsAfkCommandExecutor;

  private Config ownConfig;

  public Config getOwnConfig() {
    return ownConfig;
  }

  private final Map<UUID, DelayedAFKTimer> timerRegistrations = new HashMap<>();

  @Nullable private BukkitAudiences audiences = null;

  @NotNull BukkitAudiences adventure() {
    if (audiences == null) {
      throw new IllegalStateException("Attempt to access Adventure while the plugin is disabled.");
    }

    return audiences;
  }

  @Override
  public void onEnable() {
    essentials = (IEssentials) Bukkit.getPluginManager().getPlugin("Essentials");

    if (essentials == null) {
      // This will never actually happen, but handling just in case.
      getLogger().severe("This plugin won't work since Essentials not installed");

      return;
    }

    if (audiences == null) {
      audiences = BukkitAudiences.create(this);
    }

    PluginCommand essentialsAfkCommand = essentials.getPluginCommand("afk");

    if (essentialsAfkCommand == null) {
      getLogger().severe("Cannot find AFK command of Essentials");

      return;
    }

    saveDefaultConfig();

    ownConfig = new Config(this);

    reloadConfig();

    essentialsAfkCommandExecutor = essentialsAfkCommand.getExecutor();

    // Hijacking Essentials AFK command.
    essentialsAfkCommand.setExecutor(this::onAfkCommand);

    // Following Essentials behaviour, reload our config when Essentials config is reloaded.
    essentials.addReloadListener(this::reloadConfig);
  }

  private @NotNull Component missingTranslation(@NotNull String key) {
    return Component.text("MissingTranslation[key=" + key + "]").color(NamedTextColor.RED);
  }

  public @NotNull Component translate(@NotNull String localeCode, @NotNull String key, @Nullable
      TagResolver tagResolver) {
    if (ownConfig == null) {
      return missingTranslation(key);
    }

    var message = ownConfig.getMessage(localeCode, key).or(() -> ownConfig.getMessage(DEFAULT_LOCALE, key));

    if (message.isEmpty()) {
      return missingTranslation(key);
    }

    if (tagResolver == null) {
      return MiniMessage.miniMessage().deserialize(message.get());
    } else {
      return MiniMessage.miniMessage().deserialize(message.get(), tagResolver);
    }
  }

  public @NotNull Component translate(@NotNull String localeCode, @NotNull String key) {
    return translate(localeCode, key, null);
  }

  public @NotNull Component translate(@NotNull CommandSender sender, @NotNull String key, @Nullable TagResolver tagResolver) {
    if (sender instanceof Player player) {
      return translate(player.getLocale(), key, tagResolver);
    } else {
      return translate(DEFAULT_LOCALE, key, tagResolver);
    }
  }

  public @NotNull Component translate(@NotNull CommandSender sender, @NotNull String key) {
    return translate(sender, key, null);
  }

  @Override
  public void reloadConfig() {
    super.reloadConfig();

    ownConfig.reloadConfig(getConfig());
  }

  /**
   * Combines all arguments starting at {@code start} until the end.
   * @param args Arguments which tails need to be extracted.
   * @param start Where tail starts.
   * @return Arguments starting at {@code start} combined into a string joined by a space character.
   */
  private static String getTail(String[] args, int start) {
    StringBuilder result = new StringBuilder();

    for (int i = start, l = args.length; i < l; i++) {
      if (i != start) {
        result.append(" ");
      }

      result.append(args[i]);
    }

    return result.toString();
  }

  private boolean onAfkCommand(CommandSender sender, Command command, String label, String[] args) {
    if (essentials == null) {
      adventure().sender(sender).sendMessage(translate(sender,"essentials-not-loaded"));

      return true;
    }

    if (!(sender instanceof Player)) {
      adventure().sender(sender).sendMessage(translate(sender, "only-players"));

      return true;
    }

    if (!sender.hasPermission("essentials.afk")) {
      adventure().sender(sender).sendMessage(translate(sender, "no-permission"));

      return true;
    }

    var senderUser = essentials.getUser((Player) sender);

    if (args.length > 0 && sender.hasPermission("essentials.afk.others")) {
      var user = essentials.getUser(args[0]);

      var message = args.length > 1 ? getTail(args, 1) : null;

      if (user == null) {
        adventure().sender(sender).sendMessage(translate(sender, "user-not-found"));
      } else {
        toggleAfk(senderUser, user, message, AfkStatusChangeEvent.Cause.COMMAND);
      }
    } else {
      var message = args.length > 0 ? getTail(args, 0) : null;

      toggleAfk(senderUser, senderUser, message, AfkStatusChangeEvent.Cause.COMMAND);
    }

    return true;
  }

  /**
   * Toggles user's AFK status on or off.
   * <p>
   * This method partly re-implements Essentials code since it is not exposed as API.
   * @param sender User that requested AFK status change.
   * @param user User which AFK status needs to be changed.
   * @param message User's custom message for going AFK.
   * @param cause What caused user AFK status change.
   */
  private void toggleAfk(User sender, User user, String message, AfkStatusChangeEvent.Cause cause) {
    assert essentials != null;

    var i18n = essentials.getI18n();

    if (message != null && sender != null) {
      if (essentials != null && user.isMuted()) {
        final String dateDiff =
            sender.getMuteTimeout() > 0 ? DateUtil.formatDateDiff(sender.getMuteTimeout()) : null;

        String errorMessage;

        if (dateDiff == null) {
          errorMessage =
              sender.hasMuteReason()
                  ? i18n.format("voiceSilencedReason", sender.getMuteReason())
                  : i18n.format("voiceSilenced");
        } else {
          errorMessage =
              sender.hasMuteReason()
                  ? i18n.format("voiceSilencedReasonTime", dateDiff, sender.getMuteReason())
                  : i18n.format("voiceSilencedTime", dateDiff);
        }

        user.sendMessage(i18n.format("errorWithMessage", errorMessage));

        return;
      }

      if (!sender.isAuthorized("essentials.afk.message")) {
        user.sendMessage(i18n.format("errorWithMessage", i18n.format("noPermToAFKMessage")));

        return;
      }
    }

    if (user.isAfk()) {
      user.updateActivity(true, cause);
    } else {
      if (user.equals(sender)) {
        if (user.isAuthorized("essentials.afk.immediate")) {
          toggleUserAfkOn(user, message, cause);
        } else {
          adventure().sender(user.getBase()).sendMessage(translate(user.getBase(), "afk-requested"));

          new DelayedAFKTimer(user, message, cause, this).start();
        }
      } else {
        toggleUserAfkOn(user, message, cause);
      }
    }
  }

  /**
   * Toggles user AFK status on and broadcasts that (if enabled).
   * <p>
   * This method re-implements going AFK from the Essentials plugin.
   * @param user User which AFK status needs to be turned on.
   * @param message User's own message for going AFK.
   * @param cause What caused user to go AFK.
   */
  public void toggleUserAfkOn(User user, String message, AfkStatusChangeEvent.Cause cause) {
    user.setAfk(true, cause);

    user.setDisplayNick();

    String broadcastMessage = "";
    String selfMessage;

    if (!user.isHidden()) {
      if (essentials != null) { //
        var i18n = essentials.getI18n();

        if (essentials.getSettings().broadcastAfkMessage()) {
          if (message == null) {
            broadcastMessage = i18n.format("userIsAway", user.getDisplayName());
          } else {
            broadcastMessage = i18n.format("userIsAwayWithMessage", user.getDisplayName(), message);
          }
        }

        if (!broadcastMessage.isEmpty()) {
          essentials.broadcastMessage(user, broadcastMessage, u -> u == user);
        }

        if (message == null) {
          selfMessage = i18n.format("userIsAwaySelf", user.getDisplayName());
        } else {
          selfMessage = i18n.format("userIsAwaySelfWithMessage", user.getDisplayName(), message);
        }

        if (!selfMessage.isEmpty()) {
          user.sendMessage(selfMessage);
        }
      }
    }

    user.setAfkMessage(message);
  }

  public void registerTimer(User user, DelayedAFKTimer timer) {
    var playerUniqueId = user.getBase().getUniqueId();

    var existingTimer = timerRegistrations.get(playerUniqueId);

    if (existingTimer != null) {
      existingTimer.cancel();
    }

    timerRegistrations.put(playerUniqueId, timer);
  }

  public void unregisterTimer(User user, DelayedAFKTimer timer) {
    var playerUniqueId = user.getBase().getUniqueId();

    timerRegistrations.remove(playerUniqueId, timer);
  }

  @Override
  public void onDisable() {
    if (audiences != null) {
      audiences.close();
      audiences = null;
    }

    if (essentials != null) {
      var essentialsAfkCommand = essentials.getPluginCommand("afk");

      if (essentialsAfkCommand != null) {
        essentialsAfkCommand.setExecutor(essentialsAfkCommandExecutor);
      }
    }
  }
}
