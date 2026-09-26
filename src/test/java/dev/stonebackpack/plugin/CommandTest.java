package dev.stonebackpack.plugin;

import dev.stonebackpack.plugin.model.BackpackHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTest extends PluginTestBase {

    private static boolean showsBackpack(PlayerMock player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder(false) instanceof BackpackHolder;
    }

    @Test
    void backpackWithoutUsePermissionIsDenied() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "stonebackpack.use", false);

        player.performCommand("backpack");
        settle();

        assertFalse(showsBackpack(player));
    }

    @Test
    void openingAnotherBackpackRequiresPermission() {
        server.addPlayer("Victim");
        PlayerMock player = server.addPlayer("Curious");

        player.performCommand("backpack Victim");
        settle();

        assertFalse(showsBackpack(player));
        assertTrue(drainMessages(player).stream().anyMatch(message -> message.contains("permission")));
    }

    @Test
    void ownNameDoesNotNeedTheOthersPermission() {
        PlayerMock player = server.addPlayer("Self");

        player.performCommand("backpack self");
        settle();

        assertTrue(showsBackpack(player));
        BackpackHolder holder = (BackpackHolder) player.getOpenInventory().getTopInventory().getHolder(false);
        assertEquals(player.getUniqueId(), holder.getOwnerId());
    }

    @Test
    void adminSubcommandsRequireAdminPermission() {
        server.addPlayer("Target");
        PlayerMock player = server.addPlayer("Regular");

        for (String command : List.of("reload", "give Target", "inspect Target", "backups", "checkupdate")) {
            player.performCommand("stonebackpack " + command);
            settle();
            List<String> messages = drainMessages(player);
            assertTrue(messages.stream().anyMatch(message -> message.contains("permission")), command + " -> " + messages);
            assertFalse(showsBackpack(player), command);
        }
        assertTrue(player.getInventory().isEmpty());
    }

    @Test
    void giveWithoutOrWithUnknownPlayerIsHandled() {
        PlayerMock admin = server.addPlayer();
        admin.setOp(true);

        admin.performCommand("stonebackpack give");
        admin.performCommand("stonebackpack give NobodyWithThisName");

        List<String> messages = drainMessages(admin);
        assertTrue(messages.stream().anyMatch(message -> message.contains("Usage")), messages.toString());
        assertTrue(messages.stream().anyMatch(message -> message.contains("not found")), messages.toString());
        assertTrue(admin.getInventory().isEmpty());
    }

    @Test
    void cooldownBlocksRapidReuse() {
        setConfig("command.cooldown-seconds", 60);
        PlayerMock player = server.addPlayer();

        openOwnBackpack(player);
        player.closeInventory();
        player.performCommand("backpack");
        settle();

        assertFalse(showsBackpack(player));
        assertTrue(drainMessages(player).stream().anyMatch(message -> message.contains("wait")));
    }

    @Test
    void typedNamesCannotInjectFormattingOrClickEvents() {
        PlayerMock admin = server.addPlayer();
        admin.setOp(true);

        admin.performCommand("backpack <click:run_command:/stop>x");
        settle();

        Component message = admin.nextComponentMessage();
        assertNotNull(message);
        assertFalse(hasClickEvent(message));
        assertTrue(drainPlain(message).contains("<click:run_command:/stop>x"));
    }

    @Test
    void worldRestrictionBlocksBackpackInListedWorld() {
        PlayerMock player = server.addPlayer();
        editConfig(yaml -> {
            yaml.set("worlds.mode", "BLACKLIST");
            yaml.set("worlds.list", List.of(player.getWorld().getName()));
        });

        player.performCommand("backpack");
        settle();

        assertFalse(showsBackpack(player));
    }

    @Test
    void invalidConfigValuesFallBackSafely() {
        editConfig(yaml -> {
            yaml.set("backpack.default-rows", 99);
            yaml.set("worlds.mode", "NOT_A_MODE");
            yaml.set("backpack.blocked-items", List.of("NOT_A_MATERIAL", "SHULKER_BOX"));
        });
        PlayerMock player = server.addPlayer();

        Inventory backpack = openOwnBackpack(player);

        assertInstanceOf(BackpackHolder.class, backpack.getHolder(false));
        assertEquals(54, backpack.getSize());
    }

    private static String drainPlain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static boolean hasClickEvent(Component component) {
        if (component.clickEvent() != null) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasClickEvent(child)) {
                return true;
            }
        }
        return false;
    }
}
