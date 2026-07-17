package bm.b0b0b0.SoulGolem.message;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MessageService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> messages;
    private final String prefixRaw;

    private MessageService(Map<String, String> messages) {
        this.messages = messages;
        this.prefixRaw = messages.getOrDefault("prefix", "");
    }

    public static MessageService load(Plugin plugin, String language) {
        Path langDir = plugin.getDataFolder().toPath().resolve("lang");
        try {
            Files.createDirectories(langDir);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create lang folder", exception);
        }

        copyDefault(plugin, langDir, "en");
        copyDefault(plugin, langDir, "ru");

        Path file = langDir.resolve(language + ".yml");
        if (!Files.exists(file)) {
            file = langDir.resolve("en.yml");
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        Map<String, String> map = new HashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key)) {
                continue;
            }
            Object value = yaml.get(key);
            if (value instanceof List<?> list) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        builder.append('\n');
                    }
                    builder.append(String.valueOf(list.get(i)));
                }
                map.put(key, builder.toString());
            } else if (value != null) {
                map.put(key, String.valueOf(value));
            }
        }
        mergeMissingFromJar(plugin, language, map);
        return new MessageService(map);
    }

    private static void mergeMissingFromJar(Plugin plugin, String language, Map<String, String> map) {
        String resource = "lang/" + language + ".yml";
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key) || map.containsKey(key)) {
                    continue;
                }
                Object value = defaults.get(key);
                if (value instanceof List<?> list) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) {
                            builder.append('\n');
                        }
                        builder.append(String.valueOf(list.get(i)));
                    }
                    map.put(key, builder.toString());
                } else if (value != null) {
                    map.put(key, String.valueOf(value));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void copyDefault(Plugin plugin, Path langDir, String name) {
        Path target = langDir.resolve(name + ".yml");
        if (Files.exists(target)) {
            return;
        }
        String resource = "lang/" + name + ".yml";
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) {
                return;
            }
            Files.copy(stream, target);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot copy lang/" + name + ".yml", exception);
        }
    }

    public Component component(String key, TagResolver... resolvers) {
        String raw = this.messages.getOrDefault(key, "<red>Missing message: " + key);
        raw = raw.replace("<prefix>", this.prefixRaw);
        return MINI.deserialize(raw, resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(component(key, resolvers));
    }

    public void send(Player player, String key, TagResolver... resolvers) {
        player.sendMessage(component(key, resolvers));
    }

    public static TagResolver stub(String name, String value) {
        return Placeholder.unparsed(name, value);
    }

    public String raw(String key) {
        return this.messages.getOrDefault(key, key);
    }

    public Component golemNameplate(String statusKey) {
        return golemNameplate("golem-name-miner", statusKey);
    }

    public Component golemNameplate(String nameKey, String statusKey) {
        return MINI.deserialize(raw(nameKey))
                .appendNewline()
                .append(MINI.deserialize(raw(statusKey)));
    }

    public List<String> rawListLines(String key) {
        String value = this.messages.get(key);
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return List.of(value.split("\n", -1));
    }
}
