package dev.wardenpact;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PactConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ANY_SWORD;
    public static final ModConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue LIFETIME_SECONDS;
    public static final ModConfigSpec.IntValue DEFEND_RADIUS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ANY_SWORD = builder.comment("Allow any sword in minecraft:swords to summon, not just the Summoning Sword.")
                .define("anySwordSummons", true);
        COOLDOWN_SECONDS = builder.comment("Minimum seconds between new summons per player.")
                .defineInRange("cooldownSeconds", 20, 1, 3600);
        LIFETIME_SECONDS = builder.comment("Lifetime of a summoned warden in seconds. Attacking does not refresh this.")
                .defineInRange("lifetimeSeconds", 60, 5, 3600);
        DEFEND_RADIUS = builder.comment("Radius in blocks for automatically choosing nearby hostile mobs.")
                .defineInRange("defendRadius", 24, 4, 48);
        SPEC = builder.build();
    }

    private PactConfig() {}
}
