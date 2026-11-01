package org.novato.hardcore_grace_period.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.novato.hardcore_grace_period.AltarManager;

import java.util.Map;

/** Player-facing {@code /altar}: shows what the resurrection altar currently wants. */
public class AltarCommand implements CommandExecutor {
    private final AltarManager altar;

    public AltarCommand(AltarManager altar) {
        this.altar = altar;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Component.text("✦ Altar of Rebirth ✦").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));

        if (!altar.hasAltar()) {
            sender.sendMessage(Component.text("No altar has been raised yet. Ask an admin to set one.").color(NamedTextColor.GRAY));
            return true;
        }

        int dead = altar.deadCount();
        sender.sendMessage(Component.text("Souls revived so far: ").color(NamedTextColor.GRAY)
            .append(Component.text(altar.getRevivalsDone(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Fallen awaiting rebirth: ").color(NamedTextColor.GRAY)
            .append(Component.text(dead, dead > 0 ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY)));

        Location loc = altar.getAltarLocation();
        if (loc != null) {
            sender.sendMessage(Component.text("The altar stands at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + " in " + loc.getWorld().getName()).color(NamedTextColor.DARK_GRAY));
        }

        if (dead == 0) {
            sender.sendMessage(Component.text("The altar is dormant — no one has fallen to bring back.").color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
            return true;
        }

        sender.sendMessage(Component.text("Offer these by right-clicking the altar with them in hand:").color(NamedTextColor.YELLOW));
        for (Map.Entry<Material, Integer> entry : altar.getOffering().entrySet()) {
            int have = altar.getDonated(entry.getKey());
            int need = entry.getValue();
            boolean done = have >= need;
            sender.sendMessage(Component.text("  • ").color(NamedTextColor.DARK_GRAY)
                .append(Component.text(AltarManager.pretty(entry.getKey()) + ": ", NamedTextColor.WHITE))
                .append(Component.text(have + "/" + need, done ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(done ? Component.text("  ✔", NamedTextColor.GREEN) : Component.empty()));
        }
        return true;
    }
}
