package dev.crit.client.accounts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/** Saved accounts in config/chezaccounts.json. Tokens are stored in plain text. */
public final class Store {
    public static final class Account {
        public String id = UUID.randomUUID().toString();
        public String name = "";
        public String uuid = "";
        public String type = "OFFLINE"; // OFFLINE or MICROSOFT
        public String token = "";
    }

    public List<Account> accounts = new ArrayList<>();
    public String activeAccount;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Store inst;

    public static synchronized Store get() {
        if (inst == null) inst = load();
        return inst;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chezaccounts.json");
    }

    private static Store load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Store s = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Store.class);
                if (s != null) {
                    if (s.accounts == null) s.accounts = new ArrayList<>();
                    return s;
                }
            }
        } catch (Exception e) {
            Sessions.LOG.warn("Couldn't read chezaccounts.json, starting fresh", e);
        }
        return new Store();
    }

    public synchronized void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Sessions.LOG.warn("Couldn't save chezaccounts.json", e);
        }
    }

    public synchronized Account activeAccount() {
        if (activeAccount == null) return null;
        for (Account a : accounts) if (a.id.equals(activeAccount)) return a;
        return null;
    }
}
