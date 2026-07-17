package bm.b0b0b0.SoulGolem.config.settings;

import java.util.ArrayList;
import java.util.List;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class GuiListSettings extends YamlSerializable {

    private static final SerializerConfig SERIALIZER_CONFIG = new SerializerConfig.Builder().build();

    public GuiListSettings() {
        super(SERIALIZER_CONFIG);
    }

    @Comment({
            @CommentValue("Inventory size must be multiple of 9 (9-54)")
    })
    public int size = 54;

    public String fillerMaterial = "GRAY_STAINED_GLASS_PANE";
    public String minerMaterial = "IRON_PICKAXE";
    public String farmerMaterial = "WHEAT";
    public String prevMaterial = "ARROW";
    public String nextMaterial = "ARROW";

    public int prevSlot = 45;
    public int nextSlot = 53;

    public List<Integer> golemSlots = defaultGolemSlots();
    public List<Integer> fillerSlots = defaultFillerSlots();

    private static List<Integer> defaultGolemSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    private static List<Integer> defaultFillerSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                slots.add(i);
            }
        }
        return slots;
    }
}
