package dev.witherpact;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;

public final class EchoGear {
    private static final Item.ToolMaterial TOOL=EnumHelper.addToolMaterial("WITHER_ECHO",4,2031,9F,4F,15);
    private static final ItemArmor.ArmorMaterial ARMOR=EnumHelper.addArmorMaterial("WITHER_ECHO","witherpact:echo",37,new int[]{3,6,8,3},15);
    public static Item helmet,chest,legs,boots,pickaxe,axe,shovel,hoe;
    public static Block path;
    public static void register() {
        helmet=WitherPact.item(new Armor(0),"echo_helmet");
        chest=WitherPact.item(new Armor(1),"echo_chestplate");
        legs=WitherPact.item(new Armor(2),"echo_leggings");
        boots=WitherPact.item(new Armor(3),"echo_boots");
        pickaxe=WitherPact.item(new Pickaxe(),"echo_pickaxe");
        axe=WitherPact.item(new Axe(),"echo_axe");
        shovel=WitherPact.item(new Shovel(),"echo_shovel");
        hoe=WitherPact.item(new Hoe(),"echo_hoe");
        path=new PathBlock().setUnlocalizedName("witherpact.echo_path");
        GameRegistry.registerBlock(path,"echo_path");
    }
    static boolean repair(ItemStack stack) { return stack!=null&&(stack.getItem()==WitherPact.shard||stack.getItem()==Items.diamond); }
    @SuppressWarnings("unchecked") static void tooltip(Item item,List lines) {
        lines.add(net.minecraft.util.StatCollector.translateToLocal(item.getUnlocalizedName()+".ability"));
    }
    public static final class Sword extends ItemSword {
        public Sword() { super(TOOL); }
        @Override public boolean getIsRepairable(ItemStack a,ItemStack b) { return repair(b)||super.getIsRepairable(a,b); }
        @Override public void addInformation(ItemStack stack,EntityPlayer player,List lines,boolean advanced) { tooltip(this,lines); }
    }
    public static final class Armor extends ItemArmor {
        public Armor(int slot) { super(ARMOR,0,slot); }
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        @Override public net.minecraft.client.model.ModelBiped getArmorModel(net.minecraft.entity.EntityLivingBase entity,
                ItemStack stack,int slot,net.minecraft.client.model.ModelBiped original) {
            return dev.witherpact.client.PactArmorModel.forSlot(slot,original);
        }
        @Override public String getArmorTexture(ItemStack stack,Entity entity,int slot,String type) {
            return "witherpact:textures/models/armor/echo_layer_"+(armorType==2?"2":"1")+".png";
        }
        @Override public boolean getIsRepairable(ItemStack a,ItemStack b) { return repair(b)||super.getIsRepairable(a,b); }
        @Override public void addInformation(ItemStack stack,EntityPlayer player,List lines,boolean advanced) { tooltip(this,lines); }
    }
    public static final class Pickaxe extends ItemPickaxe {
        public Pickaxe() { super(TOOL); }
        @Override public boolean getIsRepairable(ItemStack a,ItemStack b) { return repair(b)||super.getIsRepairable(a,b); }
        @Override public void addInformation(ItemStack s,EntityPlayer p,List l,boolean a) { tooltip(this,l); }
    }
    public static final class Axe extends ItemAxe {
        public Axe() { super(TOOL); }
        @Override public boolean getIsRepairable(ItemStack a,ItemStack b) { return repair(b)||super.getIsRepairable(a,b); }
        @Override public void addInformation(ItemStack s,EntityPlayer p,List l,boolean a) { tooltip(this,l); }
    }
    public static final class Shovel extends ItemSpade {
        public Shovel() { super(TOOL); }
        @Override public boolean getIsRepairable(ItemStack a,ItemStack b) { return repair(b)||super.getIsRepairable(a,b); }
        @Override public void addInformation(ItemStack s,EntityPlayer p,List l,boolean a) { tooltip(this,l); }
    }
    public static final class Hoe extends ItemHoe {
        public Hoe() { super(TOOL); }
        @Override public boolean getIsRepairable(ItemStack a,ItemStack b) { return repair(b)||super.getIsRepairable(a,b); }
        @Override public void addInformation(ItemStack s,EntityPlayer p,List l,boolean a) { tooltip(this,l); }
    }
    public static final class Reins extends Item {
        public Reins() { setMaxStackSize(1); }
        @Override public ItemStack onItemRightClick(ItemStack stack,World world,EntityPlayer player) {
            if(!world.isRemote) WitherPact.EVENTS.reins((EntityPlayerMP)player);
            return stack;
        }
        @Override public void addInformation(ItemStack s,EntityPlayer p,List l,boolean a) { tooltip(this,l); }
    }
    public static final class PathBlock extends Block {
        public PathBlock() {
            super(Material.ground); setHardness(.65F); setStepSound(soundTypeGravel); setLightOpacity(255);
            setBlockBounds(0,0,0,1,.9375F,1); setHarvestLevel("shovel",0);
        }
        @Override public boolean isOpaqueCube() { return false; }
        @Override public boolean isFullCube() { return false; }
        @Override public Item getItemDropped(IBlockState state,Random random,int fortune) { return Item.getItemFromBlock(Blocks.dirt); }
        @Override public void onNeighborBlockChange(World world,BlockPos pos,IBlockState state,Block neighbor) {
            if(!world.isRemote&&world.getBlockState(pos.up()).getBlock().getMaterial().isSolid()) world.setBlockState(pos,Blocks.dirt.getDefaultState());
        }
    }

    public static boolean allowed(EntityPlayerMP player,BlockPos pos,ItemStack stack) {
        return !player.isSpectator()&&!player.theItemInWorldManager.getGameType().isAdventure()
                &&player.worldObj.isBlockLoaded(pos)&&player.worldObj.getWorldBorder().contains(pos)
                &&player.worldObj.isBlockModifiable(player,pos)&&player.canPlayerEdit(pos,EnumFacing.UP,stack);
    }
    static boolean ore(IBlockState state) {
        Block b=state.getBlock();
        if(b==Blocks.lit_redstone_ore) return true;
        Item item=Item.getItemFromBlock(b);
        if(item==null) return false;
        for(int id:OreDictionary.getOreIDs(new ItemStack(item,1,b.getMetaFromState(state))))
            if(OreDictionary.getOreName(id).startsWith("ore")) return true;
        return b==Blocks.coal_ore||b==Blocks.iron_ore||b==Blocks.gold_ore||b==Blocks.lapis_ore
                ||b==Blocks.diamond_ore||b==Blocks.redstone_ore||b==Blocks.emerald_ore||b==Blocks.quartz_ore;
    }
    static boolean same(IBlockState first,IBlockState next,boolean wood) {
        Block a=first.getBlock(),b=next.getBlock();
        if(a==Blocks.lit_redstone_ore) a=Blocks.redstone_ore;
        if(b==Blocks.lit_redstone_ore) b=Blocks.redstone_ore;
        if(a!=b) return false;
        if(wood&&(a==Blocks.log||a==Blocks.log2))
            return (a.getMetaFromState(first)&3)==(b.getMetaFromState(next)&3);
        return a.getMetaFromState(first)==b.getMetaFromState(next)||a==Blocks.redstone_ore;
    }
    public static int chain(EntityPlayerMP player,BlockPos origin,boolean wood) {
        World world=player.worldObj; ItemStack held=player.getHeldItem();
        if(held==null||!allowed(player,origin,held)) return 0;
        IBlockState first=world.getBlockState(origin);
        if(wood?!first.getBlock().isWood(world,origin):!ore(first)) return 0;
        ArrayDeque<BlockPos> queue=new ArrayDeque<BlockPos>(); Set<BlockPos> seen=new HashSet<BlockPos>();
        queue.add(origin); int count=0;
        while(!queue.isEmpty()&&count<16) {
            BlockPos pos=queue.remove();
            if(!seen.add(pos)||pos.distanceSq(origin)>64||!allowed(player,pos,held)
                    ||!same(first,world.getBlockState(pos),wood)) continue;
            if(!player.canHarvestBlock(world.getBlockState(pos).getBlock())) continue;
            List<BlockPos> neighbors=new ArrayList<BlockPos>();
            for(EnumFacing face:EnumFacing.values()) neighbors.add(pos.offset(face));
            if(!player.theItemInWorldManager.tryHarvestBlock(pos)) { if(count==0) break; else continue; }
            count++; queue.addAll(neighbors);
            if(player.getHeldItem()!=held||held.stackSize<=0) break;
        }
        return count;
    }
    private static boolean transform(EntityPlayerMP player,BlockPos pos,boolean hoe) {
        ItemStack held=player.getHeldItem(); World world=player.worldObj;
        if(held==null||!allowed(player,pos,held)||!world.isAirBlock(pos.up())) return false;
        IBlockState before=world.getBlockState(pos); Block block=before.getBlock();
        if(block!=Blocks.grass&&block!=Blocks.dirt) return false;
        if(MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,pos,before,player))) return false;
        if(hoe) return ((ItemHoe)EchoGear.hoe).onItemUse(held,player,world,pos,EnumFacing.UP,.5F,1,.5F);
        if(!world.setBlockState(pos,path.getDefaultState(),3)) return false;
        world.playSoundEffect(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,"step.gravel",.8F,.9F);
        if(!player.capabilities.isCreativeMode) held.damageItem(1,player);
        return true;
    }
    public static int area(EntityPlayerMP player,BlockPos origin,boolean hoe,boolean multiple) {
        ItemStack held=player.getHeldItem();
        if(!transform(player,origin,hoe)) return 0;
        int count=1;
        if(multiple) for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            if((x==0&&z==0)||player.getHeldItem()!=held||held.stackSize<=0) continue;
            if(transform(player,origin.add(x,0,z),hoe)) count++;
        }
        return count;
    }
}
