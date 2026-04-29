package dev.plug.disguise.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public final class DisguiseMessages {

    private static final TextColor PREFIX_COLOR = TextColor.color(0x55C7FF);
    private static final Component PREFIX = Component.text()
        .append(Component.text("[", NamedTextColor.DARK_GRAY))
        .append(Component.text("Disguise", PREFIX_COLOR))
        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
        .build();

    private DisguiseMessages() {
    }

    public static Component info(String message) {
        return prefixed(Component.text(message, NamedTextColor.GRAY));
    }

    public static Component success(String message) {
        return prefixed(Component.text(message, NamedTextColor.GREEN));
    }

    public static Component error(String message) {
        return prefixed(Component.text(message, NamedTextColor.RED));
    }

    public static Component chat(String displayName, String message) {
        return Component.text()
            .append(Component.text("<", NamedTextColor.DARK_GRAY))
            .append(Component.text(displayName, NamedTextColor.WHITE))
            .append(Component.text("> ", NamedTextColor.DARK_GRAY))
            .append(Component.text(message, NamedTextColor.WHITE))
            .build();
    }

    private static Component prefixed(Component message) {
        return PREFIX.append(message);
    }
}
