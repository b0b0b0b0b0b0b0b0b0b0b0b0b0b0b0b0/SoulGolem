package bm.b0b0b0.SoulGolem.gui;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.GuiConfirmSettings;
import bm.b0b0b0.SoulGolem.config.settings.GuiGeneralSettings;
import bm.b0b0b0.SoulGolem.config.settings.GuiListSettings;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.service.GolemControlService;
import bm.b0b0b0.SoulGolem.service.GolemDisplay;
import bm.b0b0b0.SoulGolem.service.GolemRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

public final class GolemGuiService {

    private final ConfigurationLoader configurationLoader;
    private final GolemRegistry registry;
    private final GolemControlService controlService;

    public GolemGuiService(
            ConfigurationLoader configurationLoader,
            GolemRegistry registry,
            GolemControlService controlService
    ) {
        this.configurationLoader = configurationLoader;
        this.registry = registry;
        this.controlService = controlService;
    }

    public ConfigurationLoader configurationLoader() {
        return this.configurationLoader;
    }

    public GolemRegistry registry() {
        return this.registry;
    }

    public GolemControlService controlService() {
        return this.controlService;
    }

    public GuiListSettings listSettings() {
        return this.configurationLoader.config().guiList();
    }

    public GuiGeneralSettings manageSettings() {
        return this.configurationLoader.config().guiGeneral();
    }

    public GuiConfirmSettings confirmSettings() {
        return this.configurationLoader.config().guiConfirm();
    }

    public MessageService messages() {
        return this.configurationLoader.messages();
    }

    public boolean canAccess(Player player, ActiveGolem golem) {
        if (player.isOp() || player.hasPermission(permissions().admin)) {
            return true;
        }
        return player.getUniqueId().equals(golem.data().ownerUuid());
    }

    public List<ActiveGolem> ownedGolems(Player player) {
        boolean admin = player.isOp() || player.hasPermission(permissions().admin);
        List<ActiveGolem> owned = new ArrayList<>();
        for (ActiveGolem golem : this.registry.all()) {
            if (admin || player.getUniqueId().equals(golem.data().ownerUuid())) {
                owned.add(golem);
            }
        }
        owned.sort(Comparator
                .comparing((ActiveGolem golem) -> golem.data().type().name())
                .thenComparing(golem -> golem.data().worldName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(golem -> (int) golem.data().x())
                .thenComparingInt(golem -> (int) golem.data().z()));
        return owned;
    }

    public Optional<ActiveGolem> findGolem(UUID golemId) {
        return this.registry.byId(golemId);
    }

    public void openList(Player player, int page) {
        List<ActiveGolem> golems = ownedGolems(player);
        if (golems.isEmpty()) {
            messages().send(player, "command-list-empty");
            return;
        }
        GolemListMenu menu = new GolemListMenu(this, player, page, golems);
        player.openInventory(menu.getInventory());
    }

    public void openManage(Player player, UUID golemId, int listPage) {
        Optional<ActiveGolem> optional = findGolem(golemId);
        if (optional.isEmpty()) {
            openList(player, listPage);
            return;
        }
        ActiveGolem golem = optional.get();
        if (!canAccess(player, golem)) {
            messages().send(player, "gui-not-owner");
            return;
        }
        GolemManageMenu menu = new GolemManageMenu(this, player, golem, listPage);
        player.openInventory(menu.getInventory());
    }

    public void openDeleteConfirm(Player player, UUID golemId, int listPage) {
        Optional<ActiveGolem> optional = findGolem(golemId);
        if (optional.isEmpty()) {
            openList(player, listPage);
            return;
        }
        ActiveGolem golem = optional.get();
        if (!canAccess(player, golem)) {
            messages().send(player, "gui-not-owner");
            return;
        }
        GolemDeleteConfirmMenu menu = new GolemDeleteConfirmMenu(this, player, golem, listPage);
        player.openInventory(menu.getInventory());
    }

    public TagResolver[] golemResolvers(ActiveGolem golem) {
        String typeLabel = golem.data().type() == GolemType.FARMER ? "farmer" : "miner";
        return new TagResolver[] {
                MessageService.stub("type", golem.data().type().name()),
                MessageService.stub("type_label", typeLabel),
                MessageService.stub("level", String.valueOf(golem.data().level())),
                MessageService.stub("world", golem.data().worldName()),
                MessageService.stub("x", String.valueOf((int) golem.data().x())),
                MessageService.stub("y", String.valueOf((int) golem.data().y())),
                MessageService.stub("z", String.valueOf((int) golem.data().z())),
                MessageService.stub("energy", String.valueOf(golem.data().energy())),
                MessageService.stub("time", "")
        };
    }

    public net.kyori.adventure.text.Component statusComponent(ActiveGolem golem) {
        return messages().component(GolemDisplay.statusKey(golem), MessageService.stub("time", ""));
    }

    public String golemNameKey(ActiveGolem golem) {
        return golem.data().type() == GolemType.FARMER ? "golem-name-farmer" : "golem-name-miner";
    }

    public String golemIconMaterial(ActiveGolem golem, GuiListSettings settings) {
        return golem.data().type() == GolemType.FARMER ? settings.farmerMaterial : settings.minerMaterial;
    }

    private bm.b0b0b0.SoulGolem.config.settings.Settings.Permissions permissions() {
        return this.configurationLoader.config().settings().permissions;
    }
}
