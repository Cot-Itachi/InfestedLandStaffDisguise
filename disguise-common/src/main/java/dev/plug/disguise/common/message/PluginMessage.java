package dev.plug.disguise.common.message;

import com.google.gson.JsonObject;

public record PluginMessage(MessageAction action, JsonObject body) {
}
