package dev.plug.disguise.rank;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public record RankData(String id, String prefix, String suffix, String colorHex) {

    public TextColor color() {
        if (colorHex == null || colorHex.isBlank()) return NamedTextColor.WHITE;
        try {
            return TextColor.fromHexString(colorHex);
        } catch (Exception e) {
            return NamedTextColor.WHITE;
        }
    }

    public static RankData fromJson(JsonObject json) {
        return new RankData(
            json.get("id").getAsString(),
            json.has("prefix")   ? json.get("prefix").getAsString()   : "",
            json.has("suffix")   ? json.get("suffix").getAsString()   : "",
            json.has("colorHex") ? json.get("colorHex").getAsString() : "#ffffff"
        );
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id",       id);
        obj.addProperty("prefix",   prefix);
        obj.addProperty("suffix",   suffix);
        obj.addProperty("colorHex", colorHex);
        return obj;
    }
}
