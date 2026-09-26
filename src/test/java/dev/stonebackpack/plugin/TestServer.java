package dev.stonebackpack.plugin;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.inventory.ChestInventoryMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * MockBukkit's own Paper schedulers can't cancel tasks, which the plugin does
 * on reload and disable. These run every task on the simulated server thread,
 * driven by performTicks(), and support cancel().
 */
public class TestServer extends ServerMock {
    private final GlobalRegionScheduler globalScheduler = new GlobalRegionScheduler() {
        @Override
        public void execute(@NotNull Plugin plugin, @NotNull Runnable run) {
            schedule(plugin, task -> run.run(), 1, 0);
        }

        @Override
        public @NotNull ScheduledTask run(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task) {
            return schedule(plugin, task, 1, 0);
        }

        @Override
        public @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, long delayTicks) {
            return schedule(plugin, task, delayTicks, 0);
        }

        @Override
        public @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task,
                                                     long initialDelayTicks, long periodTicks) {
            return schedule(plugin, task, initialDelayTicks, periodTicks);
        }

        @Override
        public void cancelTasks(@NotNull Plugin plugin) {
            getScheduler().cancelTasks(plugin);
        }
    };

    private final AsyncScheduler asyncScheduler = new AsyncScheduler() {
        @Override
        public @NotNull ScheduledTask runNow(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task) {
            return schedule(plugin, task, 1, 0);
        }

        @Override
        public @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task,
                                                 long delay, @NotNull TimeUnit unit) {
            return schedule(plugin, task, toTicks(delay, unit), 0);
        }

        @Override
        public @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task,
                                                     long initialDelay, long period, @NotNull TimeUnit unit) {
            return schedule(plugin, task, toTicks(initialDelay, unit), toTicks(period, unit));
        }

        @Override
        public void cancelTasks(@NotNull Plugin plugin) {
            getScheduler().cancelTasks(plugin);
        }
    };

    // MockBukkit doesn't implement Paper's getHolder(boolean), which the
    // plugin uses to avoid block-state snapshots.
    @Override
    public @NotNull InventoryMock createInventory(InventoryHolder owner, int size, @NotNull Component title) {
        return new ChestInventoryMock(owner, size) {
            @Override
            public InventoryHolder getHolder(boolean useSnapshot) {
                return getHolder();
            }
        };
    }

    @Override
    public @NotNull GlobalRegionScheduler getGlobalRegionScheduler() {
        return globalScheduler;
    }

    @Override
    public @NotNull AsyncScheduler getAsyncScheduler() {
        return asyncScheduler;
    }

    private static long toTicks(long amount, TimeUnit unit) {
        return Math.max(1, unit.toMillis(amount) / 50);
    }

    private ScheduledTask schedule(Plugin plugin, Consumer<ScheduledTask> consumer, long delay, long period) {
        TestTask task = new TestTask(plugin, period > 0);
        Runnable body = () -> consumer.accept(task);
        long safeDelay = Math.max(1, delay);
        task.handle = period > 0
                ? getScheduler().runTaskTimer(plugin, body, safeDelay, period)
                : getScheduler().runTaskLater(plugin, body, safeDelay);
        return task;
    }

    private static final class TestTask implements ScheduledTask {
        private final Plugin plugin;
        private final boolean repeating;
        private BukkitTask handle;
        private boolean cancelled;

        private TestTask(Plugin plugin, boolean repeating) {
            this.plugin = plugin;
            this.repeating = repeating;
        }

        @Override
        public @NotNull Plugin getOwningPlugin() {
            return plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return repeating;
        }

        @Override
        public @NotNull CancelledState cancel() {
            if (cancelled) {
                return CancelledState.CANCELLED_ALREADY;
            }
            cancelled = true;
            handle.cancel();
            return CancelledState.CANCELLED_BY_CALLER;
        }

        @Override
        public @NotNull ExecutionState getExecutionState() {
            return cancelled ? ExecutionState.CANCELLED : ExecutionState.IDLE;
        }
    }
}
