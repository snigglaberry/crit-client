package dev.crit.client;

import java.nio.file.Path;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** The system's own "open file" window (File Explorer on Windows). It blocks until the player picks or cancels. */
public final class FilePicker {
    /**
     * Opens the picker for an image. Call this from a worker thread so the game keeps drawing meanwhile.
     *
     * @return the chosen file, or null if the player cancelled
     */
    public static Path pickImage(String title) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).put(stack.UTF8("*.bmp"));
            filters.flip();
            String picked = TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, "Images (png, jpg, bmp)", false);
            if (picked == null || picked.isBlank()) return null;
            return Path.of(picked);
        } catch (Throwable t) {
            CritClient.LOG.warn("Could not open the file picker", t);
            return null;
        }
    }

    private FilePicker() {}
}
