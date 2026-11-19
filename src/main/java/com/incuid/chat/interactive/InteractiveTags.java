package com.incuid.chat.interactive;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.Locale;
import java.util.regex.Pattern;

/** Snapshot Bukkit data and item hover event on the primary thread; render without Bukkit access. */
public final class InteractiveTags {
    private InteractiveTags() { }
    private static final Pattern TOKEN = Pattern.compile("\\[(item|coords)]",Pattern.CASE_INSENSITIVE);
    public record Snapshot(Component item, Component coordinates, boolean itemAllowed, boolean coordsAllowed) { }
    public static Snapshot capture(Player player, boolean itemEnabled, boolean coordsEnabled, int maxBytes) {
        boolean itemAllowed = itemEnabled && player.hasPermission("incuidchat.items");
        boolean coordsAllowed = coordsEnabled && player.hasPermission("incuidchat.coordinates");
        Component item = Component.text("[empty hand]");
        if (itemAllowed) {
            ItemStack held = player.getInventory().getItemInMainHand().clone();
            if (held.getType() != Material.AIR && held.getAmount() > 0) {
                // Avoid large hover payloads and custom item metadata. No inventory state retained.
                try {
                    if (held.serializeAsBytes().length <= maxBytes) {
                        item = Component.text("["+held.getType().name().toLowerCase(Locale.ROOT).replace('_',' ')+"]",
                                        TextColor.color(0x55FFFF)).hoverEvent(held.asHoverEvent());
                    } else item = Component.text("[item too complex]");
                } catch (RuntimeException ex) { item = Component.text("[item unavailable]"); }
            }
        }
        Component coords = Component.empty();
        if (coordsAllowed) {
            Location l=player.getLocation();
            String simple=l.getBlockX()+" "+l.getBlockY()+" "+l.getBlockZ();
            coords=Component.text("[X: "+l.getBlockX()+", Y: "+l.getBlockY()+", Z: "+l.getBlockZ()+"]",
                    TextColor.color(0x55FFFF))
                    .hoverEvent(HoverEvent.showText(Component.text("World: "+l.getWorld().getName()+"\n"
                            + String.format(Locale.ROOT,"Exact: %.2f, %.2f, %.2f",l.getX(),l.getY(),l.getZ()))))
                    .clickEvent(ClickEvent.suggestCommand(simple));
        }
        return new Snapshot(item,coords,itemAllowed,coordsAllowed);
    }
    public static Component replace(Component original, Snapshot snapshot, int maxItems, int maxCoords) {
        String text=PlainTextComponentSerializer.plainText().serialize(original);
        var m=TOKEN.matcher(text);
        Component output=Component.empty(); int end=0, items=0, coords=0;
        while (m.find()) {
            output=output.append(Component.text(text.substring(end,m.start())));
            if (m.group(1).equalsIgnoreCase("item") && snapshot.itemAllowed() && items++ < maxItems)
                output=output.append(snapshot.item());
            else if (m.group(1).equalsIgnoreCase("coords") && snapshot.coordsAllowed() && coords++ < maxCoords)
                output=output.append(snapshot.coordinates());
            else output=output.append(Component.text(m.group()));
            end=m.end();
        }
        return end==0 ? original : output.append(Component.text(text.substring(end)));
    }
}
