package anticope.rejects.modules;

import anticope.rejects.MeteorRejectsAddon;
import anticope.rejects.utils.PauseOnGUIUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class AutoSeller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpawners = settings.createGroup("Spawners");
    
    // General Settings
    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
            .name("range")
            .description("Maximum distance to reach spawners.")
            .defaultValue(10)
            .min(1)
            .max(20)
            .build()
    );
    
    private final Setting<Integer> openDelay = sgGeneral.add(new IntSetting.Builder()
            .name("open-delay")
            .description("Ticks to wait for GUI to open.")
            .defaultValue(8)
            .min(0)
            .max(40)
            .build()
    );
    
    private final Setting<Integer> clickDelay = sgGeneral.add(new IntSetting.Builder()
            .name("click-delay")
            .description("Ticks to wait after clicking before closing.")
            .defaultValue(5)
            .min(0)
            .max(40)
            .build()
    );
    
    private final Setting<Integer> betweenDelay = sgGeneral.add(new IntSetting.Builder()
            .name("between-delay")
            .description("Ticks to wait between spawners.")
            .defaultValue(10)
            .min(0)
            .max(40)
            .build()
    );
    
    private final Setting<Integer> slotId = sgGeneral.add(new IntSetting.Builder()
            .name("sell-slot")
            .description("The slot ID to click for selling.")
            .defaultValue(22)
            .min(0)
            .max(53)
            .build()
    );
    
    private final Setting<TimeUnit> timeUnit = sgGeneral.add(new EnumSetting.Builder<TimeUnit>()
            .name("time-unit")
            .description("Unit to use for interval settings.")
            .defaultValue(TimeUnit.Seconds)
            .build()
    );
    
    private final Setting<Boolean> pauseOnGui = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-on-gui")
            .description("Pause when a GUI is already open.")
            .defaultValue(true)
            .build()
    );
	
	private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("Swing hand when interacting with spawners.")
        .defaultValue(true)
        .build()
);

private final Setting<Boolean> lookAtSpawner = sgGeneral.add(new BoolSetting.Builder()
        .name("look-at-spawner")
        .description("Look at the spawner before interacting.")
        .defaultValue(false)
        .build()
);

private final Setting<Integer> lookSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("look-speed")
        .description("Speed to rotate towards spawner (degrees per tick).")
        .defaultValue(180)
        .min(1)
        .max(360)
        .visible(lookAtSpawner::get)
        .build()
);
    
    // Spawner 1
    private final Setting<Boolean> s1Enabled = sgSpawners.add(new BoolSetting.Builder()
            .name("spawner-1-enabled")
            .description("Enable spawner 1.")
            .defaultValue(false)
            .build()
    );
    
    private final Setting<Integer> s1X = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-1-x")
            .defaultValue(0)
            .visible(s1Enabled::get)
            .build()
    );
    
    private final Setting<Integer> s1Y = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-1-y")
            .defaultValue(0)
            .visible(s1Enabled::get)
            .build()
    );
    
    private final Setting<Integer> s1Z = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-1-z")
            .defaultValue(0)
            .visible(s1Enabled::get)
            .build()
    );
    
    private final Setting<Double> s1Interval = sgSpawners.add(new DoubleSetting.Builder()
            .name("spawner-1-interval")
            .defaultValue(30)
            .min(0.05)
            .max(3600)
            .visible(s1Enabled::get)
            .build()
    );
    
    // Spawner 2
    private final Setting<Boolean> s2Enabled = sgSpawners.add(new BoolSetting.Builder()
            .name("spawner-2-enabled")
            .defaultValue(false)
            .build()
    );
    
    private final Setting<Integer> s2X = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-2-x")
            .defaultValue(0)
            .visible(s2Enabled::get)
            .build()
    );
    
    private final Setting<Integer> s2Y = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-2-y")
            .defaultValue(0)
            .visible(s2Enabled::get)
            .build()
    );
    
    private final Setting<Integer> s2Z = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-2-z")
            .defaultValue(0)
            .visible(s2Enabled::get)
            .build()
    );
    
    private final Setting<Double> s2Interval = sgSpawners.add(new DoubleSetting.Builder()
            .name("spawner-2-interval")
            .defaultValue(30)
            .min(0.05)
            .max(3600)
            .visible(s2Enabled::get)
            .build()
    );
    
    // Spawner 3
    private final Setting<Boolean> s3Enabled = sgSpawners.add(new BoolSetting.Builder()
            .name("spawner-3-enabled")
            .defaultValue(false)
            .build()
    );
    
    private final Setting<Integer> s3X = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-3-x")
            .defaultValue(0)
            .visible(s3Enabled::get)
            .build()
    );
    
    private final Setting<Integer> s3Y = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-3-y")
            .defaultValue(0)
            .visible(s3Enabled::get)
            .build()
    );
    
    private final Setting<Integer> s3Z = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-3-z")
            .defaultValue(0)
            .visible(s3Enabled::get)
            .build()
    );
    
    private final Setting<Double> s3Interval = sgSpawners.add(new DoubleSetting.Builder()
            .name("spawner-3-interval")
            .defaultValue(30)
            .min(0.05)
            .max(3600)
            .visible(s3Enabled::get)
            .build()
    );
    
    // Spawner 4
    private final Setting<Boolean> s4Enabled = sgSpawners.add(new BoolSetting.Builder()
            .name("spawner-4-enabled")
            .defaultValue(false)
            .build()
    );
    
    private final Setting<Integer> s4X = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-4-x")
            .defaultValue(0)
            .visible(s4Enabled::get)
            .build()
    );
    
    private final Setting<Integer> s4Y = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-4-y")
            .defaultValue(0)
            .visible(s4Enabled::get)
            .build()
    );
    
    private final Setting<Integer> s4Z = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-4-z")
            .defaultValue(0)
            .visible(s4Enabled::get)
            .build()
    );
    
    private final Setting<Double> s4Interval = sgSpawners.add(new DoubleSetting.Builder()
            .name("spawner-4-interval")
            .defaultValue(30)
            .min(0.05)
            .max(3600)
            .visible(s4Enabled::get)
            .build()
    );
    
    // Spawner 5
    private final Setting<Boolean> s5Enabled = sgSpawners.add(new BoolSetting.Builder()
            .name("spawner-5-enabled")
            .defaultValue(false)
            .build()
    );
    
    private final Setting<Integer> s5X = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-5-x")
            .defaultValue(0)
            .visible(s5Enabled::get)
            .build()
    );
    
    private final Setting<Integer> s5Y = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-5-y")
            .defaultValue(0)
            .visible(s5Enabled::get)
            .build()
    );
    
    private final Setting<Integer> s5Z = sgSpawners.add(new IntSetting.Builder()
            .name("spawner-5-z")
            .defaultValue(0)
            .visible(s5Enabled::get)
            .build()
    );
    
    private final Setting<Double> s5Interval = sgSpawners.add(new DoubleSetting.Builder()
            .name("spawner-5-interval")
            .defaultValue(30)
            .min(0.05)
            .max(3600)
            .visible(s5Enabled::get)
            .build()
    );
    
    // State
    private final Map<Integer, Long> lastProcessed = new HashMap<>();
    private final Queue<SpawnerTask> queue = new LinkedList<>();
    private SpawnerTask current = null;
    private State state = State.IDLE;
    private int timer = 0;
    
    public enum TimeUnit {
        Ticks("Ticks", 1),
        Seconds("Seconds", 20),
        Minutes("Minutes", 1200);
        
        private final String name;
        private final int multiplier;
        
        TimeUnit(String name, int multiplier) {
            this.name = name;
            this.multiplier = multiplier;
        }
        
        public int toTicks(double value) {
            return (int) Math.round(value * multiplier);
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    private enum State {
        IDLE, OPENING, WAIT_OPEN, CLICKING, WAIT_CLICK, CLOSING, COOLDOWN
    }
    
    private record SpawnerTask(int id, BlockPos pos) {}
    
    public AutoSeller() {
        super(MeteorRejectsAddon.CATEGORY, "auto-seller", "Automatically sells items from spawner blocks.");
    }
    
    @Override
    public void onActivate() {
        queue.clear();
        lastProcessed.clear();
        current = null;
        state = State.IDLE;
        timer = 0;
    }
    
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (PauseOnGUIUtils.shouldPause(pauseOnGui.get()) && state == State.IDLE) {
    return;
}
        
        addReadyToQueue();
        
        if (timer > 0) {
            timer--;
            return;
        }
        
        switch (state) {
            case IDLE -> {
                if (!queue.isEmpty()) {
                    current = queue.poll();
                    state = State.OPENING;
                    info("Opening spawner " + current.id());
                }
            }
            
            case OPENING -> {
                openContainer(current.pos());
                timer = openDelay.get();
                state = State.WAIT_OPEN;
            }
            
            case WAIT_OPEN -> {
                if (mc.screen != null) {
                    state = State.CLICKING;
                } else {
                    warning("Failed to open spawner " + current.id() + " - retrying");
                    state = State.OPENING;
                }
            }
            
            case CLICKING -> {
                if (mc.player.containerMenu != null) {
                    InvUtils.click().slotId(slotId.get());
                    timer = clickDelay.get();
                    state = State.WAIT_CLICK;
                } else {
                    state = State.OPENING;
                }
            }
            
            case WAIT_CLICK -> {
                state = State.CLOSING;
            }
            
            case CLOSING -> {
                mc.player.closeContainer();
                lastProcessed.put(current.id(), System.currentTimeMillis());
                info("Sold from spawner " + current.id());
                current = null;
                timer = betweenDelay.get();
                state = State.COOLDOWN;
            }
            
            case COOLDOWN -> {
                state = State.IDLE;
            }
        }
    }
    
    private void openContainer(BlockPos pos) {
    if (!isInRange(pos)) {
        warning("Spawner out of range");
        return;
    }
    
    // Optionally look at the spawner
    if (lookAtSpawner.get()) {
        lookAtBlock(pos);
    }
    
    // Right-click the block
    mc.gameMode.useItemOn(
        mc.player,
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
    );
    
    // Optionally swing hand
    if (swingHand.get()) {
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
}

private void lookAtBlock(BlockPos pos) {
    Vec3 center = Vec3.atCenterOf(pos);
    double dx = center.x - mc.player.getX();
    double dy = center.y - (mc.player.getY() + mc.player.getEyeHeight());
    double dz = center.z - mc.player.getZ();
    double distance = Math.sqrt(dx * dx + dz * dz);
    
    float targetYaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
    float targetPitch = (float) (-Math.atan2(dy, distance) * 180 / Math.PI);
    
    // Smooth rotation based on speed setting
    float speed = lookSpeed.get();
    float currentYaw = mc.player.getYRot();
    float currentPitch = mc.player.getXRot();
    
    // Calculate yaw difference (handle wrap-around)
    float yawDiff = targetYaw - currentYaw;
    while (yawDiff > 180) yawDiff -= 360;
    while (yawDiff < -180) yawDiff += 360;
    
    // Apply rotation with speed limit
    if (Math.abs(yawDiff) <= speed) {
        mc.player.setYRot(targetYaw);
    } else {
        mc.player.setYRot(currentYaw + Math.signum(yawDiff) * speed);
    }
    
    if (Math.abs(targetPitch - currentPitch) <= speed) {
        mc.player.setXRot(targetPitch);
    } else {
        mc.player.setXRot(currentPitch + Math.signum(targetPitch - currentPitch) * speed);
    }
}
    
    private void addReadyToQueue() {
        long now = System.currentTimeMillis();
        
        for (int i = 1; i <= 5; i++) {
            final int id = i;
            
            if (!isEnabled(id)) continue;
            
            BlockPos pos = getPos(id);
            if (pos == null) continue;
            if (!isInRange(pos)) continue;
            
            Long last = lastProcessed.get(id);
            if (last != null) {
                long elapsed = now - last;
                long intervalMs = getIntervalTicks(id) * 50L;
                if (elapsed < intervalMs) continue;
            }
            
            boolean inQueue = queue.stream().anyMatch(t -> t.id() == id);
            boolean isCurrent = (current != null && current.id() == id);
            if (inQueue || isCurrent) continue;
            
            queue.add(new SpawnerTask(id, pos));
        }
    }
    
    private BlockPos getPos(int id) {
        return switch (id) {
            case 1 -> new BlockPos(s1X.get(), s1Y.get(), s1Z.get());
            case 2 -> new BlockPos(s2X.get(), s2Y.get(), s2Z.get());
            case 3 -> new BlockPos(s3X.get(), s3Y.get(), s3Z.get());
            case 4 -> new BlockPos(s4X.get(), s4Y.get(), s4Z.get());
            case 5 -> new BlockPos(s5X.get(), s5Y.get(), s5Z.get());
            default -> null;
        };
    }
    
    private boolean isEnabled(int id) {
        return switch (id) {
            case 1 -> s1Enabled.get();
            case 2 -> s2Enabled.get();
            case 3 -> s3Enabled.get();
            case 4 -> s4Enabled.get();
            case 5 -> s5Enabled.get();
            default -> false;
        };
    }
    
    private int getIntervalTicks(int id) {
        double value = switch (id) {
            case 1 -> s1Interval.get();
            case 2 -> s2Interval.get();
            case 3 -> s3Interval.get();
            case 4 -> s4Interval.get();
            case 5 -> s5Interval.get();
            default -> 30;
        };
        return timeUnit.get().toTicks(value);
    }
    
    private boolean isInRange(BlockPos pos) {
        return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= range.get();
    }
}