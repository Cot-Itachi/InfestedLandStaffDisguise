package dev.plug.disguise.common.message;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

public final class PluginMessageCodec {

    private PluginMessageCodec() {
    }

    public static byte[] encode(PluginMessage message) {
        JsonObject root = new JsonObject();
        root.addProperty("action", message.action().name());
        root.add("body", message.body().deepCopy());
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static PluginMessage decode(byte[] payload) {
        JsonObject root = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        MessageAction action = MessageAction.valueOf(root.get("action").getAsString());
        JsonObject body = root.has("body") && root.get("body").isJsonObject()
            ? root.getAsJsonObject("body")
            : new JsonObject();
        return new PluginMessage(action, body);
    }
}
