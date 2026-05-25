package dev.plug.disguise;

import com.google.gson.JsonObject;
import dev.plug.disguise.rank.RankData;
import dev.plug.disguise.skin.SkinData;

import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class DisguiseData {

    private final UUID playerUuid;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private String  nick;
    private SkinData skin;
    private RankData rank;
    private boolean  active;

    public DisguiseData(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() { return playerUuid; }

    public String getNick() {
        lock.readLock().lock();
        try { return nick; }
        finally { lock.readLock().unlock(); }
    }

    public SkinData getSkin() {
        lock.readLock().lock();
        try { return skin; }
        finally { lock.readLock().unlock(); }
    }

    public RankData getRank() {
        lock.readLock().lock();
        try { return rank; }
        finally { lock.readLock().unlock(); }
    }

    public boolean isActive() {
        lock.readLock().lock();
        try { return active; }
        finally { lock.readLock().unlock(); }
    }

    public boolean hasAnyDisguise() {
        lock.readLock().lock();
        try { return nick != null || skin != null || rank != null; }
        finally { lock.readLock().unlock(); }
    }

    public void setNick(String nick) {
        lock.writeLock().lock();
        try { this.nick = nick; }
        finally { lock.writeLock().unlock(); }
    }

    public void setSkin(SkinData skin) {
        lock.writeLock().lock();
        try { this.skin = skin; }
        finally { lock.writeLock().unlock(); }
    }

    public void setRank(RankData rank) {
        lock.writeLock().lock();
        try { this.rank = rank; }
        finally { lock.writeLock().unlock(); }
    }

    public void setActive(boolean active) {
        lock.writeLock().lock();
        try { this.active = active; }
        finally { lock.writeLock().unlock(); }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            nick   = null;
            skin   = null;
            rank   = null;
            active = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public JsonObject serialize() {
        lock.readLock().lock();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("uuid",   playerUuid.toString());
            root.addProperty("active", active);
            if (nick != null) root.addProperty("nick", nick);
            if (skin != null) root.add("skin", skin.toJson());
            if (rank != null) root.add("rank", rank.toJson());
            return root;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static DisguiseData deserialize(JsonObject json) {
        DisguiseData data = new DisguiseData(UUID.fromString(json.get("uuid").getAsString()));
        if (json.has("active")) {
            data.setActive(json.get("active").getAsBoolean());
        }
        if (json.has("nick")) {
            data.setNick(json.get("nick").getAsString());
        }
        if (json.has("skin")) {
            data.setSkin(SkinData.fromJson(json.getAsJsonObject("skin")));
        }
        if (json.has("rank")) {
            data.setRank(RankData.fromJson(json.getAsJsonObject("rank")));
        }
        return data;
    }
}
