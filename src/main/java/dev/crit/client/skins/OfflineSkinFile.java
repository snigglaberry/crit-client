package dev.crit.client.skins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** The versioned, per-instance and per-account file shared by Crit Launcher and Crit Client. */
final class OfflineSkinFile {
    private static final int VERSION = 1;
    private static final int MAX_PNG = 200_000, MAX_JSON = 280_000;
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final Selection DEFAULT = new Selection(null, false, "", null);

    /** A null PNG means an explicit reset, distinct from a missing selection. */
    record Selection(byte[] png, boolean slim, String clientKey, int[] pixels) {}

    private static Path path(Path dir, UUID owner) {
        return dir.resolve("offline-skins").resolve(owner + ".json");
    }

    /** Returns null only if the account's file is absent. */
    static Selection read(Path dir, UUID owner) throws IOException {
        byte[] data;
        try {
            data = readLimited(path(dir, owner), MAX_JSON);
        } catch (NoSuchFileException e) {
            return null;
        }
        try {
            JsonObject o = JSON.fromJson(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString(), JsonObject.class);
            if (o == null) throw new IOException("Missing offline skin file contents.");
            JsonElement version = o.get("version");
            if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                    || !Integer.toString(VERSION).equals(version.getAsString())) {
                throw new IOException("Unsupported offline skin file version.");
            }
            JsonElement png = o.get("png");
            if (png != null && png.isJsonNull()) return DEFAULT;
            String variant = text(o.get("variant"));
            if (!"classic".equals(variant) && !"slim".equals(variant)) throw new IOException("Invalid skin variant.");
            String encoded = text(png);
            if (encoded == null) throw new IOException("Missing skin PNG.");
            return prepare(Base64.getDecoder().decode(encoded), "slim".equals(variant), text(o.get("clientKey")));
        } catch (RuntimeException e) {
            throw new IOException("Invalid offline skin file.", e);
        }
    }

    static void write(Path dir, UUID owner, Selection selection) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("version", VERSION);
        if (selection.png() == null) {
            o.add("png", JsonNull.INSTANCE);
        } else {
            o.addProperty("png", Base64.getEncoder().encodeToString(selection.png()));
            o.addProperty("variant", selection.slim() ? "slim" : "classic");
            if (!selection.clientKey().isEmpty()) o.addProperty("clientKey", selection.clientKey());
        }
        Path target = path(dir, owner);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "offline-skin-", ".part");
        try {
            Files.writeString(temp, o.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static void reset(Path dir, UUID owner) throws IOException { write(dir, owner, DEFAULT); }

    /** The old files had no owner. Import them for at most one standalone offline account. */
    static Selection migrateLegacy(Path dir, UUID owner, String key) throws IOException {
        Path png = dir.resolve("offline-skin.png");
        if (!Files.isRegularFile(png)) return null;
        Path claim = path(dir, owner).resolveSibling("legacy-owner");
        if (Files.exists(claim) && !owner.toString().equals(new String(readLimited(claim, 64), StandardCharsets.UTF_8))) {
            return null;
        }
        Path meta = dir.resolve("offline-skin.json");
        boolean slim = false;
        if (Files.isRegularFile(meta)) {
            var o = JsonParser.parseString(new String(readLimited(meta, 4096), StandardCharsets.UTF_8)).getAsJsonObject();
            slim = o.has("slim") && o.get("slim").getAsBoolean();
        }
        Selection legacy = prepare(readLimited(png, MAX_PNG), slim, key);
        Files.createDirectories(claim.getParent());
        try {
            Files.writeString(claim, owner.toString(), StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            if (!owner.toString().equals(new String(readLimited(claim, 64), StandardCharsets.UTF_8))) return null;
        }
        write(dir, owner, legacy);
        return legacy;
    }

    static Selection prepare(byte[] raw, Boolean requestedSlim, String key) throws IOException {
        if (raw.length > MAX_PNG) throw new IOException("That skin PNG is too large.");
        BufferedImage img;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(raw))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidPng();
            var reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) throw invalidPng();
                reader.setInput(input, true, true);
                int height = reader.getHeight(0);
                if (reader.getWidth(0) != 64 || (height != 64 && height != 32)) throw invalidPng();
                img = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        boolean legacy = img.getHeight() == 32;
        boolean slim = !legacy && (requestedSlim != null ? requestedSlim : SkinFront.isSlim(img));
        String clientKey = key != null && key.length() <= 256 ? key : "";
        if (legacy) {
            img = toModern(img);
            var out = new ByteArrayOutputStream();
            if (!ImageIO.write(img, "png", out)) throw new IOException("Could not convert the legacy skin to PNG.");
            raw = out.toByteArray();
        }
        return new Selection(raw, slim, clientKey, img.getRGB(0, 0, 64, 64, null, 0, 64));
    }

    private static IOException invalidPng() { return new IOException("That file isn't a valid 64x64 / 64x32 PNG skin."); }

    private static byte[] readLimited(Path path, int max) throws IOException {
        try (var in = Files.newInputStream(path)) {
            byte[] raw = in.readNBytes(max + 1);
            if (raw.length > max) throw new IOException("Offline skin file is too large.");
            return raw;
        }
    }

    private static String text(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    /** 64x32 skins become 64x64 the same way the game does it: mirror the right limbs onto the left. */
    private static BufferedImage toModern(BufferedImage src) {
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) out.setRGB(x, y, src.getRGB(x, y));
        }

        // Old skins have no real transparency in the hat area: if it is fully opaque, it is just background.
        boolean seeThrough = false;
        for (int y = 0; y < 16 && !seeThrough; y++) {
            for (int x = 32; x < 64; x++) {
                if ((out.getRGB(x, y) >>> 24) < 128) { seeThrough = true; break; }
            }
        }
        if (!seeThrough) {
            for (int y = 0; y < 16; y++) for (int x = 32; x < 64; x++) out.setRGB(x, y, 0);
        }

        // Right leg -> left leg slot.
        mirror(out, 4, 16, 16, 32, 4, 4);
        mirror(out, 8, 16, 16, 32, 4, 4);
        mirror(out, 0, 20, 24, 32, 4, 12);
        mirror(out, 4, 20, 16, 32, 4, 12);
        mirror(out, 8, 20, 8, 32, 4, 12);
        mirror(out, 12, 20, 16, 32, 4, 12);
        // Right arm -> left arm slot.
        mirror(out, 44, 16, -8, 32, 4, 4);
        mirror(out, 48, 16, -8, 32, 4, 4);
        mirror(out, 40, 20, 0, 32, 4, 12);
        mirror(out, 44, 20, -8, 32, 4, 12);
        mirror(out, 48, 20, -16, 32, 4, 12);
        mirror(out, 52, 20, -8, 32, 4, 12);
        return out;
    }

    private static void mirror(BufferedImage img, int x, int y, int dx, int dy, int w, int h) {
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int tx = x + dx + (w - 1 - i), ty = y + dy + j;
                img.setRGB(tx, ty, img.getRGB(x + i, y + j));
            }
        }
    }

    private OfflineSkinFile() {}
}
