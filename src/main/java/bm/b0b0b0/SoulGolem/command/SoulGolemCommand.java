package bm.b0b0b0.SoulGolem.command;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.gui.GolemGuiService;
import bm.b0b0b0.SoulGolem.item.StatueItemFactory;
import bm.b0b0b0.SoulGolem.item.StatueRecipeService;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.service.OreTableService;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class SoulGolemCommand implements CommandExecutor, TabCompleter {

    private final ConfigurationLoader configurationLoader;
    private final StatueItemFactory statueFactory;
    private final StatueRecipeService statueRecipes;
    private final OreTableService oreTable;
    private final SoulChestService chestService;
    private final GolemGuiService guiService;

    public SoulGolemCommand(
            ConfigurationLoader configurationLoader,
            StatueItemFactory statueFactory,
            StatueRecipeService statueRecipes,
            OreTableService oreTable,
            SoulChestService chestService,
            GolemGuiService guiService
    ) {
        this.configurationLoader = configurationLoader;
        this.statueFactory = statueFactory;
        this.statueRecipes = statueRecipes;
        this.oreTable = oreTable;
        this.chestService = chestService;
        this.guiService = guiService;
    }

    private MessageService messages() {
        return this.configurationLoader.messages();
    }

    private Settings.Permissions permissions() {
        return this.configurationLoader.config().settings().permissions;
    }

    private boolean has(CommandSender sender, String permission) {
        Settings.Permissions perms = permissions();
        return sender.hasPermission(perms.admin) || sender.hasPermission(permission);
    }

    private boolean denyUnless(CommandSender sender, String permission) {
        if (has(sender, permission)) {
            return false;
        }
        messages().send(sender, "command-no-permission");
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages().send(sender, "command-usage");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            case "list", "gui" -> handleGui(sender);
            default -> {
                messages().send(sender, "command-usage");
                yield true;
            }
        };
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (denyUnless(sender, permissions().give)) {
            return true;
        }
        if (args.length < 3) {
            messages().send(sender, "command-give-usage");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages().send(sender, "command-give-target-offline");
            return true;
        }

        GolemType type = parseGiveType(args[2]);
        if (type == null) {
            messages().send(sender, "command-give-usage");
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                messages().send(sender, "command-give-usage");
                return true;
            }
            if (amount < 1) {
                messages().send(sender, "command-give-usage");
                return true;
            }
        }

        ItemStack statue = this.statueFactory.create(amount, type);
        target.getInventory().addItem(statue);
        String typeName = type.name().toLowerCase(Locale.ROOT);
        if (sender.equals(target)) {
            messages().send(sender, "command-give-self",
                    MessageService.stub("amount", String.valueOf(amount)),
                    MessageService.stub("type", typeName));
        } else {
            messages().send(sender, "command-give-other",
                    MessageService.stub("amount", String.valueOf(amount)),
                    MessageService.stub("player", target.getName()),
                    MessageService.stub("type", typeName));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (denyUnless(sender, permissions().reload)) {
            return true;
        }
        this.configurationLoader.reload();
        this.oreTable.reload();
        this.chestService.updateStationNames(messages().raw("chest-name"), messages().raw("craft-table-name"));
        if (this.statueRecipes != null) {
            this.statueRecipes.register();
        }
        messages().send(sender, "command-reload-done");
        return true;
    }

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "command-player-only");
            return true;
        }
        if (denyUnless(player, permissions().use)) {
            return true;
        }
        this.guiService.openList(player, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        Settings.Permissions perms = permissions();
        return switch (args.length) {
            case 1 -> filter(allowedSubcommands(sender, perms), args[0]);
            case 2 -> {
                if (!"give".equalsIgnoreCase(args[0]) || !has(sender, perms.give)) {
                    yield List.of();
                }
                yield filter(onlinePlayerNames(), args[1]);
            }
            case 3 -> {
                if (!"give".equalsIgnoreCase(args[0]) || !has(sender, perms.give)) {
                    yield List.of();
                }
                yield filter(List.of("miner", "farmer", "digger"), args[2]);
            }
            default -> List.of();
        };
    }

    private List<String> allowedSubcommands(CommandSender sender, Settings.Permissions perms) {
        List<String> options = new ArrayList<>(4);
        if (has(sender, perms.give)) {
            options.add("give");
        }
        if (has(sender, perms.reload)) {
            options.add("reload");
        }
        if (has(sender, perms.use)) {
            options.add("list");
            options.add("gui");
        }
        return options;
    }

    private static GolemType parseGiveType(String raw) {
        if ("miner".equalsIgnoreCase(raw)) {
            return GolemType.MINER;
        }
        if ("farmer".equalsIgnoreCase(raw)) {
            return GolemType.FARMER;
        }
        if ("digger".equalsIgnoreCase(raw)) {
            return GolemType.DIGGER;
        }
        return null;
    }

    private static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>(options.size());
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
