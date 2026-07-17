package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.CropType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.ItemStack;

public final class FarmerCarried {

    private final FarmerContext ctx;

    public FarmerCarried(FarmerContext ctx) {
        this.ctx = ctx;
    }

    public boolean hasCarriedSeed(ActiveGolem golem) {
        return carriedCropType(golem) != null;
    }

    public CropType carriedCropType(ActiveGolem golem) {
        for (ItemStack stack : golem.carried()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CropType type = CropType.bySeed(stack.getType());
            if (type != null && this.ctx.enabledCrops().contains(type)) {
                return type;
            }
        }
        return null;
    }

    public void consumeCarriedSeed(ActiveGolem golem) {
        List<ItemStack> leftover = new ArrayList<>();
        boolean removed = false;
        for (ItemStack stack : golem.carried()) {
            CropType type = stack == null ? null : CropType.bySeed(stack.getType());
            if (!removed && type != null && this.ctx.enabledCrops().contains(type) && !stack.isEmpty()) {
                int amount = stack.getAmount() - 1;
                if (amount > 0) {
                    ItemStack copy = stack.clone();
                    copy.setAmount(amount);
                    leftover.add(copy);
                }
                removed = true;
                continue;
            }
            if (stack != null) {
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    public void returnSeedToChest(ActiveGolem golem) {
        if (!hasCarriedSeed(golem)) {
            return;
        }
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            CropType type = stack == null ? null : CropType.bySeed(stack.getType());
            if (type != null && this.ctx.enabledCrops().contains(type)) {
                this.ctx.chestService().deposit(golem.data(), stack.clone());
            } else if (stack != null) {
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    public static int countCarried(ActiveGolem golem, Material material) {
        int total = 0;
        for (ItemStack stack : golem.carried()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public static void consumeCarried(ActiveGolem golem, Material material, int amount) {
        int left = amount;
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (stack == null) {
                continue;
            }
            if (left > 0 && stack.getType() == material) {
                int take = Math.min(left, stack.getAmount());
                left -= take;
                int remain = stack.getAmount() - take;
                if (remain > 0) {
                    ItemStack copy = stack.clone();
                    copy.setAmount(remain);
                    leftover.add(copy);
                }
                continue;
            }
            leftover.add(stack.clone());
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    public void returnCarriedToChest(ActiveGolem golem, Material material) {
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (stack != null && stack.getType() == material) {
                this.ctx.chestService().deposit(golem.data(), stack.clone());
            } else if (stack != null) {
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    public void returnAllCarriedToChest(ActiveGolem golem) {
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!this.ctx.chestService().deposit(golem.data(), stack.clone())) {
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    public static Material carriedStairs(ActiveGolem golem) {
        for (ItemStack stack : golem.carried()) {
            if (stack != null && org.bukkit.Tag.STAIRS.isTagged(stack.getType())) {
                return stack.getType();
            }
        }
        return null;
    }

    public static Collection<ItemStack> cropHarvestDrops(Block crop, CropType cropType) {
        List<ItemStack> drops = new ArrayList<>(2);
        Material type = crop.getType();
        if (cropType.isStemCrop() && cropType.isFruit(type)) {
            if (cropType == CropType.MELON) {
                drops.add(cropType.harvestProduct(3 + ThreadLocalRandom.current().nextInt(5)));
            } else {
                drops.add(cropType.harvestProduct(1));
            }
            return drops;
        }
        if (!(crop.getBlockData() instanceof Ageable ageable)
                || ageable.getAge() < ageable.getMaximumAge()) {
            drops.add(new ItemStack(cropType.seed(), 1));
            return drops;
        }
        if (cropType == CropType.WHEAT) {
            drops.add(new ItemStack(Material.WHEAT, 1));
            int seeds = 1;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                if (random.nextDouble() < 0.5714286D) {
                    seeds++;
                }
            }
            drops.add(new ItemStack(Material.WHEAT_SEEDS, seeds));
            return drops;
        }
        if (cropType == CropType.BEETROOT) {
            drops.add(new ItemStack(Material.BEETROOT, 1));
            int seeds = 1;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                if (random.nextDouble() < 0.5714286D) {
                    seeds++;
                }
            }
            drops.add(new ItemStack(Material.BEETROOT_SEEDS, seeds));
            return drops;
        }
        int amount = 1 + ThreadLocalRandom.current().nextInt(3);
        drops.add(cropType.harvestProduct(amount));
        return drops;
    }
}
