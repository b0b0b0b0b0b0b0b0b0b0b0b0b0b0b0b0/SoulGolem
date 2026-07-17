package bm.b0b0b0.SoulGolem.command;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.item.StatueItemFactory;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.service.GolemRegistry;
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
    private final GolemRegistry registry;
    private final OreTableService oreTable;
    private final SoulChestService chestService;

    public SoulGolemCommand(
            ConfigurationLoader configurationLoader,
            StatueItemFactory statueFactory,
            GolemRegistry registry,
            OreTableService oreTable,
            SoulChestService chestService
    ) {
        this.configurationLoader = configurationLoader;
        this.statueFactory = statueFactory;
        this.registry = registry;
        this.oreTable = oreTable;
        this.chestService = chestService;
    }

    private MessageService messages() {
        return this.configurationLoader.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages().send(sender, "command-usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "give" -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            default -> {
                messages().send(sender, "command-usage");
                yield true;
            }
        };
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        var permissions = this.configurationLoader.config().settings().permissions;
        if (!sender.hasPermission(permissions.give) && !sender.hasPermission(permissions.admin)) {
            messages().send(sender, "command-no-permission");
            return true;
        }

        Player target = null;
        int amount = 1;
        GolemType type = GolemType.MINER;

        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                messages().send(sender, "command-player-only");
                return true;
            }
            target = player;
        } else if (args.length == 2) {
            GolemType parsedType = tryParseType(args[1]);
            if (parsedType != null) {
                if (!(sender instanceof Player player)) {
                    messages().send(sender, "command-player-only");
                    return true;
                }
                target = player;
                type = parsedType;
            } else if (isInt(args[1])) {
                if (!(sender instanceof Player player)) {
                    messages().send(sender, "command-player-only");
                    return true;
                }
                target = player;
                amount = Math.max(1, Integer.parseInt(args[1]));
            } else {
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages().send(sender, "command-give-target-offline");
                    return true;
                }
            }
        } else if (args.length == 3) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages().send(sender, "command-give-target-offline");
                return true;
            }
            if (isInt(args[2])) {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } else {
                GolemType parsedType = tryParseType(args[2]);
                if (parsedType != null) {
                    type = parsedType;
                }
            }
        } else {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages().send(sender, "command-give-target-offline");
                return true;
            }
            if (isInt(args[2])) {
                amount = Math.max(1, Integer.parseInt(args[2]));
            }
            GolemType parsedType = tryParseType(args[3]);
            if (parsedType != null) {
                type = parsedType;
            }
        }

        ItemStack statue = this.statueFactory.create(amount, type);
        target.getInventory().addItem(statue);
        if (sender.equals(target)) {
            messages().send(sender, "command-give-self",
                    MessageService.stub("amount", String.valueOf(amount)),
                    MessageService.stub("type", type.name().toLowerCase(Locale.ROOT)));
        } else {
            messages().send(sender, "command-give-other",
                    MessageService.stub("amount", String.valueOf(amount)),
                    MessageService.stub("player", target.getName()),
                    MessageService.stub("type", type.name().toLowerCase(Locale.ROOT)));
        }
        return true;
    }

    private static GolemType tryParseType(String raw) {
        if (raw == null) {
            return null;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if ("MINER".equals(upper) || "FARMER".equals(upper)) {
            return GolemType.valueOf(upper);
        }
        return null;
    }

    private static boolean isInt(String raw) {
        try {
            Integer.parseInt(raw);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean handleReload(CommandSender sender) {
        var permissions = this.configurationLoader.config().settings().permissions;
        if (!sender.hasPermission(permissions.reload) && !sender.hasPermission(permissions.admin)) {
            messages().send(sender, "command-no-permission");
            return true;
        }
        this.configurationLoader.reload();
        this.oreTable.reload();
        this.chestService.updateStationNames(messages().raw("chest-name"), messages().raw("craft-table-name"));
        messages().send(sender, "command-reload-done");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "command-player-only");
            return true;
        }
        List<ActiveGolem> owned = new ArrayList<>();
        for (ActiveGolem golem : this.registry.all()) {
            if (player.getUniqueId().equals(golem.data().ownerUuid())) {
                owned.add(golem);
            }
        }
        if (owned.isEmpty()) {
            messages().send(player, "command-list-empty");
            return true;
        }
        messages().send(player, "command-list-header", MessageService.stub("count", String.valueOf(owned.size())));
        for (ActiveGolem golem : owned) {
            String status;
            if (golem.data().paused()) {
                status = "paused";
            } else if (golem.data().type() == GolemType.FARMER) {
                status = golem.farmerState().name().toLowerCase(Locale.ROOT);
            } else {
                status = golem.state().name().toLowerCase(Locale.ROOT);
            }
            messages().send(player, "command-list-entry",
                    MessageService.stub("type", golem.data().type().name()),
                    MessageService.stub("level", String.valueOf(golem.data().level())),
                    MessageService.stub("world", golem.data().worldName()),
                    MessageService.stub("x", String.valueOf((int) golem.data().x())),
                    MessageService.stub("y", String.valueOf((int) golem.data().y())),
                    MessageService.stub("z", String.valueOf((int) golem.data().z())),
                    MessageService.stub("status", status));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "reload", "list"), args[0]);
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            List<String> options = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
            options.add("miner");
            options.add("farmer");
            options.add("1");
            options.add("8");
            return filter(options, args[1]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return filter(List.of("1", "8", "16", "64", "miner", "farmer"), args[2]);
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0])) {
            return filter(List.of("miner", "farmer"), args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
