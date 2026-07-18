package bm.b0b0b0.SoulGolem.item;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.GolemType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

public final class StatueRecipeService {

    private final Plugin plugin;
    private final StatueItemFactory statueFactory;
    private final Supplier<GolemSettings> golems;
    private final List<NamespacedKey> registered = new ArrayList<>(2);

    public StatueRecipeService(
            Plugin plugin,
            StatueItemFactory statueFactory,
            Supplier<GolemSettings> golems
    ) {
        this.plugin = plugin;
        this.statueFactory = statueFactory;
        this.golems = golems;
    }

    public void register() {
        unregister();
        GolemSettings settings = this.golems.get();
        registerOne("farmer_statue", GolemType.FARMER, settings.farmer.craft);
        registerOne("miner_statue", GolemType.MINER, settings.miner.craft);
        registerOne("digger_statue", GolemType.DIGGER, settings.digger.craft);
    }

    public void unregister() {
        for (NamespacedKey key : this.registered) {
            Bukkit.removeRecipe(key);
        }
        this.registered.clear();
    }

    private void registerOne(String id, GolemType type, GolemSettings.StatueCraft craft) {
        if (craft == null || !craft.enabled) {
            return;
        }
        String[] shape = normalizeShape(craft.shape);
        if (shape == null) {
            this.plugin.getLogger().warning("Invalid statue recipe shape for " + type.name());
            return;
        }
        Map<Character, Material> ingredients = parseIngredients(craft.ingredients);
        if (ingredients.isEmpty()) {
            this.plugin.getLogger().warning("Empty statue recipe ingredients for " + type.name());
            return;
        }
        for (String row : shape) {
            for (int i = 0; i < row.length(); i++) {
                char key = row.charAt(i);
                if (key == ' ') {
                    continue;
                }
                if (!ingredients.containsKey(key)) {
                    this.plugin.getLogger().warning(
                            "Statue recipe " + type.name() + " missing ingredient for key '" + key + "'"
                    );
                    return;
                }
            }
        }

        NamespacedKey key = new NamespacedKey(this.plugin, id);
        ItemStack result = this.statueFactory.create(1, type);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);
        for (Map.Entry<Character, Material> entry : ingredients.entrySet()) {
            recipe.setIngredient(entry.getKey(), new RecipeChoice.MaterialChoice(entry.getValue()));
        }
        try {
            Bukkit.addRecipe(recipe);
            this.registered.add(key);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to register statue recipe " + id, exception);
        }
    }

    private static String[] normalizeShape(List<String> raw) {
        if (raw == null || raw.isEmpty() || raw.size() > 3) {
            return null;
        }
        List<String> rows = new ArrayList<>(raw.size());
        int width = -1;
        for (String line : raw) {
            if (line == null) {
                return null;
            }
            String row = line.toUpperCase(Locale.ROOT);
            if (row.isEmpty() || row.length() > 3) {
                return null;
            }
            if (width < 0) {
                width = row.length();
            } else if (row.length() != width) {
                return null;
            }
            rows.add(row);
        }
        return rows.toArray(String[]::new);
    }

    private static Map<Character, Material> parseIngredients(List<String> raw) {
        Map<Character, Material> map = new HashMap<>(4);
        if (raw == null) {
            return map;
        }
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            int sep = trimmed.indexOf('=');
            if (sep != 1) {
                continue;
            }
            char key = Character.toUpperCase(trimmed.charAt(0));
            Material material = Material.matchMaterial(trimmed.substring(2).trim());
            if (material == null || material.isAir()) {
                continue;
            }
            map.put(key, material);
        }
        return map;
    }
}
