package org.novato.hardcore_grace_period;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.novato.hardcore_grace_period.config.ModConfig;

import java.time.Duration;

/**
 * Shows everyone a title (and plays a calm sound) when a player dies for good.
 * Only call this for permanent deaths, i.e. when the player has no grace period left.
 * Text, timing and sound come from the death* settings in config.json.
 */
public class DeathAnnouncer {
    private final ModConfig config;

    public DeathAnnouncer(ModConfig config) {
        this.config = config;
    }

    public void announce(Player dead) {
        if (!config.deathTitleEnabled) return;

        Title title = Title.title(
            legacy(config.deathTitle, dead.getName()),
            legacy(config.deathSubtitle, dead.getName()),
            Title.Times.times(ticks(config.deathTitleFadeIn), ticks(config.deathTitleStay), ticks(config.deathTitleFadeOut))
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            // Played at each listener's own position so everyone hears it at the same volume
            if (config.deathSound != null && !config.deathSound.isEmpty()) {
                player.playSound(player.getLocation(), config.deathSound, SoundCategory.MASTER,
                    config.deathSoundVolume, config.deathSoundPitch);
            }
        }
    }

    private static Component legacy(String text, String playerName) {
        return LegacyComponentSerializer.legacySection().deserialize(text == null ? "" : text.replace("%player%", playerName));
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(ticks * 50L);
    }
}
