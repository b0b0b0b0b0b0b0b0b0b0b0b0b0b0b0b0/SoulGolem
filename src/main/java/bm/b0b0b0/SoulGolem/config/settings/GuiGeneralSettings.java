package bm.b0b0b0.SoulGolem.config.settings;

import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;
import java.util.ArrayList;
import java.util.List;

public final class GuiGeneralSettings extends YamlSerializable {

    private static final SerializerConfig SERIALIZER_CONFIG = new SerializerConfig.Builder().build();

    public GuiGeneralSettings() {
        super(SERIALIZER_CONFIG);
    }

    @Comment({
            @CommentValue("Inventory size must be multiple of 9 (9-54)")
    })
    public int size = 27;

    public String fillerMaterial = "GRAY_STAINED_GLASS_PANE";

    public int infoSlot = 11;
    public String infoMaterial = "COPPER_INGOT";

    public int pauseSlot = 13;
    public String pauseMaterial = "REDSTONE";
    public String resumeMaterial = "LIME_DYE";

    public int upgradesSlot = 15;
    public String upgradesMaterial = "ANVIL";

    public List<Integer> fillerSlots = defaultFillerSlots();

    private static List<Integer> defaultFillerSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            if (i != 11 && i != 13 && i != 15) {
                slots.add(i);
            }
        }
        return slots;
    }
}
