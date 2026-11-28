package dev.witherpact;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=WitherPact.ID, name="Wither Pact", version=WitherPact.VERSION, acceptedMinecraftVersions="[1.8.9]")
public final class WitherPact {
    public static final String ID="witherpact", VERSION="1.0.0";
    @Mod.Instance(ID) public static WitherPact INSTANCE;
    @SidedProxy(clientSide="dev.witherpact.client.ClientProxy", serverSide="dev.witherpact.CommonProxy")
    public static CommonProxy proxy;
    public static final CreativeTabs TAB=new CreativeTabs(ID) {
        @Override public Item getTabIconItem() { return Items.nether_star; }
    };
    public static final PactEvents EVENTS=new PactEvents();
    public static int cooldown=20, lifetime=60, defendRadius=24;
    public static Item shard, reins, sword;
    public static Enchantment resonance, guard, mining;

    public static <T extends Item> T item(T item,String name) {
        item.setUnlocalizedName(ID+"."+name).setCreativeTab(TAB);
        GameRegistry.registerItem(item,name);
        return item;
    }

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        Configuration cfg=new Configuration(event.getSuggestedConfigurationFile());
        cfg.load();
        cooldown=cfg.getInt("cooldownSeconds","summons",20,1,3600,"Sword summon cooldown.");
        lifetime=cfg.getInt("lifetimeSeconds","summons",60,5,3600,"Friendly wither lifetime.");
        defendRadius=cfg.getInt("defendRadius","summons",24,4,48,"Hostile search radius.");
        int a=cfg.getInt("resonanceId","enchantments",120,64,255,"Must be free and unique.");
        int b=cfg.getInt("guardId","enchantments",121,64,255,"Must be free and unique.");
        int c=cfg.getInt("miningId","enchantments",122,64,255,"Must be free and unique.");
        if(a==b||b==c||a==c||Enchantment.getEnchantmentById(a)!=null
                ||Enchantment.getEnchantmentById(b)!=null||Enchantment.getEnchantmentById(c)!=null)
            throw new IllegalStateException("Wither Pact enchantment IDs conflict; change IDs in config/witherpact.cfg");
        cfg.save();
        shard=item(new Item(),"echo_shard");
        sword=item(new EchoGear.Sword(),"summoning_sword");
        reins=item(new EchoGear.Reins(),"wither_reins");
        EchoGear.register();
        resonance=new PactEnchantment(a,"contract_resonance",3,EnumEnchantmentType.WEAPON);
        guard=new PactEnchantment(b,"sculk_guard",4,EnumEnchantmentType.ARMOR);
        mining=new PactEnchantment(c,"echo_mining",3,EnumEnchantmentType.DIGGER);
        EntityRegistry.registerModEntity(PactWither.class,"pact_wither",1,this,96,2,true);
        EntityRegistry.registerModEntity(PactSkull.class,"pact_skull",2,this,64,2,true);
        PactNetwork.init();
        MinecraftForge.EVENT_BUS.register(EVENTS);
        FMLCommonHandler.instance().bus().register(EVENTS);
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        PactRecipes.register();
        proxy.init();
    }
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) { EVENTS.clear(); }

    public static boolean hostile(EntityPlayer owner,Entity entity) {
        if(!(entity instanceof EntityLivingBase)||!entity.isEntityAlive()||!(entity instanceof IMob)
                ||entity instanceof EntityPlayer||entity instanceof PactWither) return false;
        if(entity instanceof EntityTameable&&((EntityTameable)entity).isTamed()) return false;
        return !owner.isOnSameTeam((EntityLivingBase)entity);
    }
    public static int level(Enchantment enchantment,ItemStack stack) {
        return stack==null?0:net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(enchantment.effectId,stack);
    }
    public static boolean sword(ItemStack stack) { return stack!=null&&stack.getItem() instanceof net.minecraft.item.ItemSword; }
    public static void message(EntityPlayer player,String key,Object... args) {
        player.addChatMessage(new net.minecraft.util.ChatComponentTranslation(key,args));
    }

    private static final class PactEnchantment extends Enchantment {
        private final int max;
        PactEnchantment(int id,String name,int max,EnumEnchantmentType type) {
            super(id,new ResourceLocation(ID,name),3,type); this.max=max; setName(ID+"."+name);
        }
        @Override public int getMaxLevel() { return max; }
        @Override public int getMinEnchantability(int level) { return 10+(level-1)*10; }
        @Override public int getMaxEnchantability(int level) { return getMinEnchantability(level)+30; }
    }
}
