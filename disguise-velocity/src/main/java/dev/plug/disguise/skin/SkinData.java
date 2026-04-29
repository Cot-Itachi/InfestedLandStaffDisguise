package dev.plug.disguise.skin;

import com.google.gson.JsonObject;

public record SkinData(String value, String signature) {

    public static SkinData fromJson(JsonObject json) {
        return new SkinData(
            json.get("value").getAsString(),
            json.get("signature").getAsString()
        );
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("value", value);
        obj.addProperty("signature", signature);
        return obj;
    }
}
