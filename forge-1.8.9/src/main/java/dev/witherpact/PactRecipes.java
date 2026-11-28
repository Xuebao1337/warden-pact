package dev.witherpact;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

public final class PactRecipes {
    public static void register() {
        GameRegistry.addRecipe(new ItemStack(WitherPact.shard,8),"BOB","OSO","BOB",
                'B',Items.bone,'O',Blocks.obsidian,'S',Items.nether_star);
        GameRegistry.addShapelessRecipe(new ItemStack(WitherPact.reins),WitherPact.shard,Items.saddle,Blocks.soul_sand);
        Item[] base={Items.diamond_sword,Items.diamond_helmet,Items.diamond_chestplate,Items.diamond_leggings,Items.diamond_boots,
                     Items.diamond_pickaxe,Items.diamond_axe,Items.diamond_shovel,Items.diamond_hoe};
        Item[] result={WitherPact.sword,EchoGear.helmet,EchoGear.chest,EchoGear.legs,EchoGear.boots,
                       EchoGear.pickaxe,EchoGear.axe,EchoGear.shovel,EchoGear.hoe};
        for(int i=0;i<base.length;i++) GameRegistry.addRecipe(new Upgrade(base[i],result[i]));
    }
    public static final class Upgrade implements IRecipe {
        private final Item base,result;
        public Upgrade(Item base,Item result) { this.base=base; this.result=result; }
        private ItemStack source(InventoryCrafting inventory) {
            ItemStack original=null; boolean shard=false;
            for(int i=0;i<inventory.getSizeInventory();i++) {
                ItemStack stack=inventory.getStackInSlot(i);
                if(stack==null) continue;
                if(stack.getItem()==base&&original==null) original=stack;
                else if(stack.getItem()==WitherPact.shard&&!shard) shard=true;
                else return null;
            }
            return original!=null&&shard?original:null;
        }
        @Override public boolean matches(InventoryCrafting inventory,World world) { return source(inventory)!=null; }
        @Override public ItemStack getCraftingResult(InventoryCrafting inventory) {
            ItemStack base=source(inventory); if(base==null) return null;
            ItemStack upgraded=new ItemStack(result);
            upgraded.setItemDamage(Math.min(base.getItemDamage(),upgraded.getMaxDamage()-1));
            if(base.hasTagCompound()) upgraded.setTagCompound((net.minecraft.nbt.NBTTagCompound)base.getTagCompound().copy());
            return upgraded;
        }
        @Override public int getRecipeSize() { return 2; }
        @Override public ItemStack getRecipeOutput() { return new ItemStack(result); }
        @Override public ItemStack[] getRemainingItems(InventoryCrafting inventory) {
            ItemStack[] result=new ItemStack[inventory.getSizeInventory()];
            for(int i=0;i<result.length;i++) {
                ItemStack stack=inventory.getStackInSlot(i);
                if(stack!=null&&stack.getItem().hasContainerItem(stack)) result[i]=stack.getItem().getContainerItem(stack);
            }
            return result;
        }
    }
}
