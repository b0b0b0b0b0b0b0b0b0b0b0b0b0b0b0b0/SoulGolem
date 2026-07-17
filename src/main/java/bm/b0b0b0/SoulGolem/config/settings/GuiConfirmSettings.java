package bm.b0b0b0.SoulGolem.config.settings;

import java.util.ArrayList;
import java.util.List;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class GuiConfirmSettings extends YamlSerializable {

    private static final SerializerConfig SERIALIZER_CONFIG = new SerializerConfig.Builder().build();

    public GuiConfirmSettings() {
        super(SERIALIZER_CONFIG);
    }

    @Comment({
            @CommentValue("Inventory size must be multiple of 9 (9-54)")
    })
    public int size = 27;

    public String fillerMaterial = "GRAY_STAINED_GLASS_PANE";
    public String warningMaterial = "BARRIER";
    public String yesMaterial = "LIME_WOOL";
    public String noMaterial = "RED_WOOL";

    public int warningSlot = 13;
    public int yesSlot = 11;
    public int noSlot = 15;

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
