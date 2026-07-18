package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

public final class GolemDisplay {

    private static final double ORPHAN_SWEEP_RANGE = 48.0D;

    private GolemDisplay() {
    }

    public static String nameKey(ActiveGolem golem) {
        return switch (golem.data().type()) {
            case FARMER -> "golem-name-farmer";
            case DIGGER -> golem.data().isCrewHelper() ? "golem-name-digger" : "golem-name-digger-leader";
            default -> "golem-name-miner";
        };
    }

    public static String statusKey(ActiveGolem golem) {
        if (golem.data().paused()) {
            return "golem-status-paused";
        }
        if (golem.data().energy() <= 0 || golem.fetchingFeed()) {
            return "golem-status-hungry";
        }
        if (!golem.setupComplete() && bm.b0b0b0.SoulGolem.service.setup.GolemSetupWork.isSetupState(golem)) {
            return "golem-status-setup";
        }
        if (golem.data().type() == GolemType.DIGGER) {
            return switch (golem.diggerState()) {
                case DIGGING, MOVING_TO_DIG -> "golem-status-digging";
                case PLACING_STAIR -> "golem-status-placing-stair";
                case ESCAPING -> golem.crewReturning() ? "golem-status-crew-return" : "golem-status-escaping";
                case DONE -> "golem-status-dig-done";
                case MOVING_TO_CLEAR, CLEARING -> "golem-status-clearing";
                case MOVING_TO_FENCE_CLEAR, CLEARING_FENCE -> "golem-status-fence-clear";
                case MOVING_TO_FENCE, PLACING_FENCE -> "golem-status-fence";
                case MOVING_TO_GATE, PLACING_GATE -> "golem-status-gate";
                case MOVING_TO_CLOSE_GATE, CLOSING_GATE -> "golem-status-closing-gate";
                case MOVING_TO_SHELTER, BUILDING_SHELTER, SHELTERING -> "golem-status-shelter";
                case MOVING_TO_COMBAT, COMBATING -> "golem-status-combat";
                case MOVING_TO_TORCH, PLACING_TORCH -> "golem-status-torch";
                case PLACING_SEAT -> "golem-status-seat";
                case MOVING_TO_SEAT -> carryingStairs(golem) ? "golem-status-seat" : "golem-status-sitting";
                case SITTING -> "golem-status-sitting";
                case RESTING -> "golem-status-resting";
                case WAITING_CHEST -> "golem-status-waiting-chest";
                case MOVING_TO_CHEST -> {
                    if (golem.crewReturning()) {
                        yield "golem-status-crew-return";
                    }
                    if (golem.fetchingFeed()) {
                        yield "golem-status-hungry";
                    }
                    if (golem.fetchingTorch()) {
                        yield "golem-status-torch";
                    }
                    if (golem.fetchingFence() || golem.fetchingGate()) {
                        yield golem.fetchingGate() ? "golem-status-gate" : "golem-status-fence";
                    }
                    if (golem.fetchingSeat()) {
                        yield "golem-status-seat";
                    }
                    if (golem.fetchingWeapon()) {
                        yield "golem-status-combat";
                    }
                    yield "golem-status-carrying";
                }
                case MOVING_TO_SETUP_CLEAR, SETUP_CLEAR,
                     MOVING_TO_SETUP_BORDER, SETUP_BORDER,
                     MOVING_TO_SETUP_CHEST, SETUP_CHEST -> "golem-status-setup";
                default -> "golem-status-working";
            };
        }
        if (golem.data().type() == GolemType.FARMER) {
            return switch (golem.farmerState()) {
                case WAITING_SEEDS -> "golem-status-waiting-seeds";
                case PREPARE_FIELD, MOVING_TO_TILL, TILLING -> "golem-status-tilling";
                case MOVING_TO_PLANT, PLANTING -> "golem-status-planting";
                case WAIT_GROWTH, WANDERING -> "golem-status-waiting-crop";
                case PLACING_SEAT -> "golem-status-seat";
                case MOVING_TO_SEAT -> carryingStairs(golem) ? "golem-status-seat" : "golem-status-sitting-farmer";
                case SITTING -> "golem-status-sitting-farmer";
                case MOVING_TO_CLEAR, CLEARING -> "golem-status-clearing";
                case MOVING_TO_FENCE_CLEAR, CLEARING_FENCE -> "golem-status-fence-clear";
                case MOVING_TO_FENCE, PLACING_FENCE -> "golem-status-fence";
                case MOVING_TO_GATE, PLACING_GATE -> "golem-status-gate";
                case MOVING_TO_CLOSE_GATE, CLOSING_GATE -> "golem-status-closing-gate";
                case MOVING_TO_SHELTER, BUILDING_SHELTER, SHELTERING -> "golem-status-shelter";
                case MOVING_TO_COMBAT, COMBATING -> "golem-status-combat";
                case MOVING_TO_HARVEST, HARVESTING -> "golem-status-harvesting";
                case MOVING_TO_CHEST -> {
                    if (golem.fetchingFeed()) {
                        yield "golem-status-hungry";
                    }
                    if (golem.fetchingSeed()) {
                        yield "golem-status-planting";
                    }
                    if (golem.fetchingSeat()) {
                        yield "golem-status-seat";
                    }
                    if (golem.fetchingTorch()) {
                        yield "golem-status-torch";
                    }
                    if (golem.fetchingFence() || golem.fetchingGate()) {
                        yield golem.fetchingGate() ? "golem-status-gate" : "golem-status-fence";
                    }
                    if (golem.fetchingBoneMeal()) {
                        yield "golem-status-bonemeal";
                    }
                    if (golem.fetchingCraft()) {
                        yield "golem-status-place-craft";
                    }
                    if (golem.fetchingComposter() || golem.fetchingCompost()) {
                        yield "golem-status-compost";
                    }
                    if (golem.fetchingWeapon()) {
                        yield "golem-status-combat";
                    }
                    yield "golem-status-carrying";
                }
                case WAITING_CHEST -> "golem-status-waiting-chest";
                case MOVING_TO_CRAFT, CRAFTING -> "golem-status-crafting";
                case MOVING_TO_PLACE_CRAFT, PLACING_CRAFT -> "golem-status-place-craft";
                case MOVING_TO_TORCH, PLACING_TORCH -> "golem-status-torch";
                case MOVING_TO_BONEMEAL, APPLYING_BONEMEAL -> "golem-status-bonemeal";
                case MOVING_TO_PLACE_COMPOSTER, PLACING_COMPOSTER,
                     MOVING_TO_COMPOST, COMPOSTING,
                     MOVING_TO_COLLECT_COMPOST, COLLECTING_COMPOST -> "golem-status-compost";
                case RESTING -> "golem-status-waiting-crop";
                case MOVING_TO_SETUP_CLEAR, SETUP_CLEAR,
                     MOVING_TO_SETUP_BORDER, SETUP_BORDER,
                     MOVING_TO_SETUP_CHEST, SETUP_CHEST,
                     MOVING_TO_SETUP_CRAFT, SETUP_CRAFT -> "golem-status-setup";
            };
        }
        return switch (golem.state()) {
            case RESTING -> "golem-status-resting";
            case WAITING_CHEST -> {
                if (golem.pickaxeSwapBlocked()) {
                    yield "golem-status-waiting-chest-pickaxe";
                }
                yield "golem-status-waiting-chest";
            }
            case MINING, MOVING_TO_ORE -> "golem-status-mining";
            case MOVING_TO_CLEAR, CLEARING -> "golem-status-clearing";
            case MOVING_TO_FENCE_CLEAR, CLEARING_FENCE -> "golem-status-fence-clear";
            case MOVING_TO_FENCE, PLACING_FENCE -> "golem-status-fence";
            case MOVING_TO_GATE, PLACING_GATE -> "golem-status-gate";
            case MOVING_TO_CLOSE_GATE, CLOSING_GATE -> "golem-status-closing-gate";
            case MOVING_TO_SHELTER, BUILDING_SHELTER, SHELTERING -> "golem-status-shelter";
            case MOVING_TO_COMBAT, COMBATING -> "golem-status-combat";
            case MOVING_TO_TORCH, PLACING_TORCH -> "golem-status-torch";
            case PLACING_SEAT -> "golem-status-seat";
            case MOVING_TO_SEAT -> carryingStairs(golem) ? "golem-status-seat" : "golem-status-sitting";
            case SITTING -> "golem-status-sitting";
            case MOVING_TO_CHEST -> {
                if (golem.fetchingFeed()) {
                    yield "golem-status-hungry";
                }
                if (golem.fetchingTorch()) {
                    yield "golem-status-torch";
                }
                if (golem.fetchingFence() || golem.fetchingGate()) {
                    yield golem.fetchingGate() ? "golem-status-gate" : "golem-status-fence";
                }
                if (golem.fetchingSeat()) {
                    yield "golem-status-seat";
                }
                if (golem.fetchingPickaxe()) {
                    yield "golem-status-pickaxe";
                }
                if (golem.fetchingWeapon()) {
                    yield "golem-status-combat";
                }
                yield "golem-status-carrying";
            }
            case MOVING_TO_SETUP_CLEAR, SETUP_CLEAR,
                 MOVING_TO_SETUP_BORDER, SETUP_BORDER,
                 MOVING_TO_SETUP_CHEST, SETUP_CHEST -> "golem-status-setup";
            default -> "golem-status-working";
        };
    }

    private static boolean carryingStairs(ActiveGolem golem) {
        for (org.bukkit.inventory.ItemStack stack : golem.carried()) {
            if (stack != null && org.bukkit.Tag.STAIRS.isTagged(stack.getType())) {
                return true;
            }
        }
        return false;
    }

    public static void refresh(
            ActiveGolem golem,
            CopperGolem copper,
            MessageService messages,
            PluginKeys keys,
            GolemSettings.TextDisplays style
    ) {
        GolemSpawnService.applyDiggerLeaderGlow(copper, golem.data());
        if (style == null || !style.enabled) {
            return;
        }
        try {
            String key = statusKey(golem);
            String time = restTimer(golem);
            String energy = String.valueOf(golem.data().energy());
            String plateKey = nameKey(golem) + "|" + key + "|" + energy + (time.isEmpty() ? "" : "|" + time);
            TextDisplay display = ensureNameplate(copper, golem.data().id().toString(), keys, style);
            TextDisplayStyle.applyGolemNameplate(display, style, style.golemOffsetY);
            copper.setCustomNameVisible(false);
            copper.customName(null);
            if (!plateKey.equals(golem.lastStatusKey())) {
                golem.lastStatusKey(plateKey);
                if (time.isEmpty()) {
                    display.text(messages.golemNameplate(
                            nameKey(golem),
                            key,
                            MessageService.stub("energy", energy)
                    ));
                } else {
                    display.text(messages.golemNameplate(
                            nameKey(golem),
                            key,
                            MessageService.stub("energy", energy),
                            MessageService.stub("time", time)
                    ));
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static String restTimer(ActiveGolem golem) {
        if (golem.data().type() == GolemType.FARMER) {
            return "";
        }
        if (golem.data().type() == GolemType.DIGGER) {
            return switch (golem.diggerState()) {
                case RESTING, SITTING -> formatTicks(golem.restTicksLeft());
                case MOVING_TO_SEAT -> carryingStairs(golem) ? "" : formatTicks(golem.restTicksLeft());
                default -> "";
            };
        }
        return switch (golem.state()) {
            case RESTING, SITTING -> formatTicks(golem.restTicksLeft());
            case MOVING_TO_SEAT -> carryingStairs(golem) ? "" : formatTicks(golem.restTicksLeft());
            default -> "";
        };
    }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        long minutes = seconds / 60L;
        long rem = seconds % 60L;
        return minutes + ":" + (rem < 10L ? "0" : "") + rem;
    }

    public static void refreshForce(
            ActiveGolem golem,
            CopperGolem copper,
            MessageService messages,
            PluginKeys keys,
            GolemSettings.TextDisplays style
    ) {
        GolemSpawnService.applyDiggerLeaderGlow(copper, golem.data());
        if (style == null || !style.enabled) {
            return;
        }
        golem.lastStatusKey("");
        refresh(golem, copper, messages, keys, style);
    }

    public static void remove(CopperGolem copper, String golemId, PluginKeys keys) {
        if (copper != null && copper.isValid()) {
            for (Entity passenger : copper.getPassengers()) {
                if (passenger instanceof TextDisplay display && isGolemNameplate(display, golemId, keys)) {
                    passenger.remove();
                }
            }
            removeAllNear(copper.getWorld(), copper.getLocation(), ORPHAN_SWEEP_RANGE, golemId, keys);
            return;
        }
    }

    public static void removeAllNear(World world, Location center, double range, String golemId, PluginKeys keys) {
        if (world == null || center == null || golemId == null) {
            return;
        }
        for (Entity nearby : world.getNearbyEntities(center, range, range, range)) {
            if (!(nearby instanceof TextDisplay display)) {
                continue;
            }
            if (matchesGolemDisplay(display, golemId, keys)) {
                display.remove();
            }
        }
    }

    private static boolean matchesGolemDisplay(TextDisplay display, String golemId, PluginKeys keys) {
        if (isGolemNameplate(display, golemId, keys)) {
            return true;
        }
        String chest = display.getPersistentDataContainer().get(keys.chestGolemId(), PersistentDataType.STRING);
        if (golemId.equals(chest)) {
            return true;
        }
        String craft = display.getPersistentDataContainer().get(keys.craftGolemId(), PersistentDataType.STRING);
        if (golemId.equals(craft)) {
            return true;
        }
        String compost = display.getPersistentDataContainer().get(keys.compostGolemId(), PersistentDataType.STRING);
        return golemId.equals(compost);
    }

    private static boolean isGolemNameplate(TextDisplay display, String golemId, PluginKeys keys) {
        String plate = display.getPersistentDataContainer().get(keys.golemId(), PersistentDataType.STRING);
        return golemId.equals(plate);
    }

    private static TextDisplay ensureNameplate(
            CopperGolem copper,
            String golemId,
            PluginKeys keys,
            GolemSettings.TextDisplays style
    ) {
        TextDisplay kept = null;
        for (Entity passenger : copper.getPassengers()) {
            if (!(passenger instanceof TextDisplay display) || !display.isValid()) {
                continue;
            }
            if (!isGolemNameplate(display, golemId, keys)) {
                continue;
            }
            if (kept == null) {
                kept = display;
            } else {
                display.remove();
            }
        }

        World world = copper.getWorld();
        Location origin = copper.getLocation();
        for (Entity nearby : world.getNearbyEntities(origin, ORPHAN_SWEEP_RANGE, ORPHAN_SWEEP_RANGE, ORPHAN_SWEEP_RANGE)) {
            if (!(nearby instanceof TextDisplay display) || !display.isValid()) {
                continue;
            }
            if (!isGolemNameplate(display, golemId, keys)) {
                continue;
            }
            if (kept == null) {
                kept = display;
            } else if (!display.getUniqueId().equals(kept.getUniqueId())) {
                display.remove();
            }
        }

        if (kept != null && kept.isValid()) {
            mountNameplate(copper, kept);
            return kept;
        }

        float rideY = style.golemOffsetY;
        TextDisplay spawned = world.spawn(origin, TextDisplay.class, display -> {
            display.getPersistentDataContainer().set(keys.golemId(), PersistentDataType.STRING, golemId);
            display.setPersistent(false);
            TextDisplayStyle.applyGolemNameplate(display, style, rideY);
        });
        mountNameplate(copper, spawned);
        return spawned;
    }

    private static void mountNameplate(CopperGolem copper, TextDisplay display) {
        if (!display.isValid() || !copper.isValid()) {
            return;
        }
        display.setPersistent(false);
        display.setGravity(false);
        if (display.getVehicle() != null && display.getVehicle().getUniqueId().equals(copper.getUniqueId())) {
            return;
        }
        if (display.getVehicle() != null) {
            display.leaveVehicle();
        }
        copper.addPassenger(display);
    }
}
