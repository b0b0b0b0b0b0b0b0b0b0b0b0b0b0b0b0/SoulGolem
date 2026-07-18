package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import java.util.Locale;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class TextDisplayStyle {

    private TextDisplayStyle() {
    }

    public static void applyCommon(TextDisplay display, GolemSettings.TextDisplays settings) {
        display.setBillboard(parseBillboard(settings.billboard));
        display.setAlignment(parseAlignment(settings.alignment));
        display.setSeeThrough(settings.seeThrough);
        display.setShadowed(settings.shadowed);
        display.setDefaultBackground(settings.defaultBackground);
        display.setViewRange(Math.max(0.01F, settings.viewRange));
        display.setLineWidth(Math.max(1, settings.lineWidth));
        display.setGravity(false);
        display.setPersistent(true);
        display.setTeleportDuration(0);
        if (settings.textOpacity < 0) {
            display.setTextOpacity((byte) -1);
        } else {
            display.setTextOpacity((byte) Math.min(255, settings.textOpacity));
        }
        if (settings.fullBright) {
            display.setBrightness(new Display.Brightness(15, 15));
        }
    }

    public static void applyGolemNameplate(TextDisplay display, GolemSettings.TextDisplays settings) {
        applyGolemNameplate(display, settings, Math.max(0.0F, settings.golemOffsetY));
    }

    public static void applyGolemNameplate(TextDisplay display, GolemSettings.TextDisplays settings, float rideOffsetY) {
        applyCommon(display, settings);
        float scale = Math.max(0.05F, settings.golemScale);
        display.setTransformation(new Transformation(
                new Vector3f(0F, rideOffsetY, 0F),
                new AxisAngle4f(0F, 0F, 0F, 1F),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0F, 0F, 0F, 1F)
        ));
    }

    public static void applyChestHologram(TextDisplay display, GolemSettings.TextDisplays settings) {
        applyCommon(display, settings);
        float scale = Math.max(0.05F, settings.chestScale);
        display.setTransformation(new Transformation(
                new Vector3f(0F, 0F, 0F),
                new AxisAngle4f(0F, 0F, 0F, 1F),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0F, 0F, 0F, 1F)
        ));
    }

    private static Display.Billboard parseBillboard(String raw) {
        if (raw == null || raw.isBlank()) {
            return Display.Billboard.CENTER;
        }
        try {
            return Display.Billboard.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Display.Billboard.CENTER;
        }
    }

    private static TextDisplay.TextAlignment parseAlignment(String raw) {
        if (raw == null || raw.isBlank()) {
            return TextDisplay.TextAlignment.CENTER;
        }
        try {
            return TextDisplay.TextAlignment.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TextDisplay.TextAlignment.CENTER;
        }
    }
}
