package dev.wardenpact;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** Server-side configuration, reloaded when the integrated or dedicated server starts. */
public final class PactConfig {
    public static final Setting<Boolean> ANY_SWORD = new Setting<>(true);
    public static final Setting<Integer> COOLDOWN_SECONDS = new Setting<>(20);
    public static final Setting<Integer> LIFETIME_SECONDS = new Setting<>(60);
    public static final Setting<Integer> DEFEND_RADIUS = new Setting<>(24);

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("wardenpact-fabric.properties");
        Properties properties = new Properties();
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
            ANY_SWORD.set(Boolean.parseBoolean(properties.getProperty("anySwordSummons", "true")));
            COOLDOWN_SECONDS.set(integer(properties, "cooldownSeconds", 20, 1, 3600));
            LIFETIME_SECONDS.set(integer(properties, "lifetimeSeconds", 60, 5, 3600));
            DEFEND_RADIUS.set(integer(properties, "defendRadius", 24, 4, 48));
            if (!Files.exists(path)) {
                properties.setProperty("anySwordSummons", "true");
                properties.setProperty("cooldownSeconds", "20");
                properties.setProperty("lifetimeSeconds", "60");
                properties.setProperty("defendRadius", "24");
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    properties.store(writer, "Warden Pact Fabric - server settings; restart the world after editing");
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            LoggerFactory.getLogger(WardenPact.MOD_ID).warn("Cannot read Warden Pact configuration: {}", path, e);
        }
    }

    private static int integer(Properties properties, String name, int fallback, int min, int max) {
        try {
            return Math.clamp(Integer.parseInt(properties.getProperty(name, String.valueOf(fallback))), min, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static final class Setting<T> {
        private T value;
        private Setting(T value) { this.value = value; }
        public T get() { return value; }
        public void set(T value) { this.value = value; }
    }

    private PactConfig() {}
}
