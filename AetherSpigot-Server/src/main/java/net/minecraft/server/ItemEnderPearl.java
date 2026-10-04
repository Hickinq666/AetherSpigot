package net.minecraft.server;

public class ItemEnderPearl extends Item {

    public ItemEnderPearl() {
        this.maxStackSize = 16;
        this.a(CreativeModeTab.f);
    }

    public ItemStack a(ItemStack itemstack, World world, EntityHuman entityhuman) {
        if (entityhuman.abilities.canInstantlyBuild) {
            return itemstack;
        } else {
            // AetherSpigot start - cooldown et spawn vérifiés avant que la perle existe
            if (!world.isClientSide && !org.aetherspigot.AetherHooks.pearlMayThrow(entityhuman)) {
                return itemstack;
            }
            // AetherSpigot end
            --itemstack.count;
            world.makeSound(entityhuman, "random.bow", 0.5F, 0.4F / (ItemEnderPearl.g.nextFloat() * 0.4F + 0.8F));
            if (!world.isClientSide) {
                // AetherSpigot start
                if (world.addEntity(new EntityEnderPearl(world, entityhuman))) {
                    org.aetherspigot.AetherHooks.pearlThrown(entityhuman);
                }
                // AetherSpigot end
            }

            entityhuman.b(StatisticList.USE_ITEM_COUNT[Item.getId(this)]);
            return itemstack;
        }
    }
}
