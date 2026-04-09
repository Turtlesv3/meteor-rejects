package anticope.rejects.modules;

import anticope.rejects.MeteorRejectsAddon;
import anticope.rejects.utils.PauseOnGUIUtils;
import anticope.rejects.utils.WorldUtils;
import meteordevelopment.meteorclient.events.entity.player.BreakBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class AutoFarm extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTill = settings.createGroup("Till");
    private final SettingGroup sgHarvest = settings.createGroup("Harvest");
    private final SettingGroup sgPlant = settings.createGroup("Plant");
    private final SettingGroup sgBonemeal = settings.createGroup("Bonemeal");
    private final SettingGroup sgAbility = settings.createGroup("Ability");

    private final Map<BlockPos, Item> replantMap = new HashMap<>();

    // General Settings
    private final Setting<Shape> shape = sgGeneral.add(new EnumSetting.Builder<Shape>()
            .name("shape")
            .description("The shape of the farm area.")
            .defaultValue(Shape.Sphere)
            .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
            .name("range")
            .description("Auto farm range (for sphere mode).")
            .defaultValue(4)
            .min(1)
            .visible(() -> shape.get() == Shape.Sphere)
            .build()
    );

    private final Setting<Integer> horizontalRange = sgGeneral.add(new IntSetting.Builder()
        .name("horizontal-range")
        .description("Horizontal distance to farm (left/right for cuboid).")
        .defaultValue(4)
        .min(0)
        .max(16)
        .visible(() -> shape.get() == Shape.Cuboid)
        .build()
    );

    private final Setting<Integer> verticalRange = sgGeneral.add(new IntSetting.Builder()
            .name("vertical-range")
            .description("Vertical distance to farm (up and down).")
            .defaultValue(2)
            .min(1)
            .max(8)
            .visible(() -> shape.get() == Shape.Cuboid)
            .build()
    );

    private final Setting<Integer> forwardRange = sgGeneral.add(new IntSetting.Builder()
            .name("forward-range")
            .description("Forward distance to farm (only for cuboid shape).")
            .defaultValue(6)
            .min(1)
            .max(16)
            .visible(() -> shape.get() == Shape.Cuboid)
            .build()
    );

    private final Setting<Integer> backwardRange = sgGeneral.add(new IntSetting.Builder()
            .name("backward-range")
            .description("Backward distance to farm (only for cuboid shape).")
            .defaultValue(2)
            .min(0)
            .max(16)
            .visible(() -> shape.get() == Shape.Cuboid)
            .build()
    );

    private final Setting<Integer> bpt = sgGeneral.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Amount of operations that can be applied in one tick.")
            .min(1)
            .defaultValue(1)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Whether or not to rotate towards block.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> pauseOnGui = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-on-gui")
            .description("Pause farming when any GUI screen is open.")
            .defaultValue(true)
            .build()
    );
    
    private final Setting<Boolean> debugMode = sgAbility.add(new BoolSetting.Builder()
            .name("debug-mode")
            .description("Log detailed information about ability activation.")
            .defaultValue(false)
            .build()
    );

    // Till Settings
    private final Setting<Boolean> till = sgTill.add(new BoolSetting.Builder()
            .name("till")
            .description("Turn nearby dirt into farmland.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> moist = sgTill.add(new BoolSetting.Builder()
            .name("moist")
            .description("Only till moist blocks.")
            .defaultValue(true)
            .build()
    );

    // Harvest Settings
    private final Setting<Boolean> harvest = sgHarvest.add(new BoolSetting.Builder()
            .name("harvest")
            .description("Harvest crops.")
            .defaultValue(true)
            .build()
    );

    private final Setting<List<Block>> harvestBlocks = sgHarvest.add(new BlockListSetting.Builder()
            .name("harvest-blocks")
            .description("Which crops to harvest.")
            .defaultValue()
            .filter(this::harvestFilter)
            .build()
    );

    // Plant Settings
    private final Setting<Boolean> plant = sgPlant.add(new BoolSetting.Builder()
            .name("plant")
            .description("Plant crops.")
            .defaultValue(true)
            .build()
    );

    private final Setting<List<Item>> plantItems = sgPlant.add(new ItemListSetting.Builder()
            .name("plant-items")
            .description("Which crops to plant.")
            .defaultValue()
            .filter(this::plantFilter)
            .build()
    );

    private final Setting<Boolean> onlyReplant = sgPlant.add(new BoolSetting.Builder()
            .name("only-replant")
            .description("Only replant planted crops.")
            .defaultValue(true)
            .onChanged(b -> replantMap.clear())
            .build()
    );

    // Bonemeal Settings
    private final Setting<Boolean> bonemeal = sgBonemeal.add(new BoolSetting.Builder()
            .name("bonemeal")
            .description("Bonemeal crops.")
            .defaultValue(true)
            .build()
    );

    private final Setting<List<Block>> bonemealBlocks = sgBonemeal.add(new BlockListSetting.Builder()
            .name("bonemeal-blocks")
            .description("Which crops to bonemeal.")
            .defaultValue()
            .filter(this::bonemealFilter)
            .build()
    );

    // Ability Settings
    private final Setting<Boolean> abilityEnabled = sgAbility.add(new BoolSetting.Builder()
            .name("ability-enabled")
            .description("Automatically activate power ability (Shift + Right Click).")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> abilityCooldown = sgAbility.add(new IntSetting.Builder()
            .name("ability-cooldown")
            .description("Cooldown in seconds between ability activations.")
            .defaultValue(240)
            .min(1)
            .max(3600)
            .visible(abilityEnabled::get)
            .build()
    );

    private final Setting<Integer> abilityDuration = sgAbility.add(new IntSetting.Builder()
            .name("ability-duration")
            .description("How long the ability lasts in seconds.")
            .defaultValue(21)
            .min(1)
            .max(300)
            .visible(abilityEnabled::get)
            .build()
    );

    private final Setting<Integer> abilityActivationDelay = sgAbility.add(new IntSetting.Builder()
            .name("activation-delay")
            .description("Seconds after cooldown ends to activate ability.")
            .defaultValue(2)
            .min(0)
            .max(60)
            .visible(abilityEnabled::get)
            .build()
    );

    private final Pool<BlockPos.MutableBlockPos> blockPosPool = new Pool<>(BlockPos.MutableBlockPos::new);
    private final List<BlockPos.MutableBlockPos> blocks = new ArrayList<>();

    // Ability state fields
    private long lastAbilityActivation = 0;
    private boolean abilityActive = false;
    private long abilityActivatedAt = 0;
    private boolean shiftPressed = false;
    private int shiftHoldTicks = 0;
    private int clickCount = 0;

    // Ability sequence states
    private enum AbilityState {
        IDLE,
        HOLDING_SHIFT,
        FIRST_CLICK,
        SECOND_CLICK,
        ACTIVE
    }
    private AbilityState abilityState = AbilityState.IDLE;

    int actions = 0;

    public AutoFarm() {
        super(MeteorRejectsAddon.CATEGORY, "auto-farm", "All-in-one farm utility.");
    }

    @Override
    public void onDeactivate() {
        replantMap.clear();
        
        // Clean up ability state
        if (shiftPressed) {
            mc.options.keyShift.setDown(false);
            shiftPressed = false;
        }
        abilityState = AbilityState.IDLE;
        abilityActive = false;
    }

    @EventHandler
    private void onBreakBlock(BreakBlockEvent event) {
        BlockState state = mc.level.getBlockState(event.blockPos);
        Block block = state.getBlock();
        if (onlyReplant.get()) {
            Item item = null;
            if (block == Blocks.WHEAT) item = Items.WHEAT_SEEDS;
            else if (block == Blocks.CARROTS) item = Items.CARROT;
            else if (block == Blocks.POTATOES) item = Items.POTATO;
            else if (block == Blocks.BEETROOTS) item = Items.BEETROOT_SEEDS;
            else if (block == Blocks.NETHER_WART) item = Items.NETHER_WART;
            else if (block == Blocks.PITCHER_CROP) item = Items.PITCHER_POD;
            else if (block == Blocks.TORCHFLOWER) item = Items.TORCHFLOWER_SEEDS;
            if (item != null) replantMap.put(event.blockPos, item);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Pause if a non-Meteor GUI is open and setting is enabled
        if (PauseOnGUIUtils.shouldPause(pauseOnGui.get())) {
            return;
        }

        // Handle ability activation
        if (abilityEnabled.get()) {
            handleAbility();
        }

        actions = 0;
        
        if (shape.get() == Shape.Cuboid) {
            registerCuboidBlocks();
        } else {
            registerSphereBlocks();
        }

        BlockIterator.after(() -> {
            blocks.sort(Comparator.comparingDouble(value -> mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(value))));

            for (BlockPos pos : blocks) {
                BlockState state = mc.level.getBlockState(pos);
                Block block = state.getBlock();
                if (till(pos, block) || harvest(pos, state, block) || plant(pos, block) || bonemeal(pos, state, block))
                    actions++;
                if (actions >= bpt.get()) break;
            }

            for (BlockPos.MutableBlockPos blockPos : blocks) blockPosPool.free(blockPos);
            blocks.clear();
        });
    }

    private boolean hasHarvestableCrops() {
        if (!harvest.get()) return false;
        if (mc.player == null || mc.level == null) return false;
        
        if (shape.get() == Shape.Cuboid) {
            Direction facing = mc.player.getDirection();
            int halfWidth = horizontalRange.get();
            int forward = forwardRange.get();
            int backward = backwardRange.get();
            int vertRange = verticalRange.get();
            
            BlockPos playerPos = mc.player.blockPosition();
            int minX, maxX, minZ, maxZ;
            
            switch (facing) {
                case NORTH -> {
                    minX = playerPos.getX() - halfWidth;
                    maxX = playerPos.getX() + halfWidth;
                    minZ = playerPos.getZ() - forward;
                    maxZ = playerPos.getZ() + backward;
                }
                case SOUTH -> {
                    minX = playerPos.getX() - halfWidth;
                    maxX = playerPos.getX() + halfWidth;
                    minZ = playerPos.getZ() - backward;
                    maxZ = playerPos.getZ() + forward;
                }
                case WEST -> {
                    minX = playerPos.getX() - forward;
                    maxX = playerPos.getX() + backward;
                    minZ = playerPos.getZ() - halfWidth;
                    maxZ = playerPos.getZ() + halfWidth;
                }
                case EAST -> {
                    minX = playerPos.getX() - backward;
                    maxX = playerPos.getX() + forward;
                    minZ = playerPos.getZ() - halfWidth;
                    maxZ = playerPos.getZ() + halfWidth;
                }
                default -> { return false; }
            }
            
            int minY = playerPos.getY() - vertRange;
            int maxY = playerPos.getY() + vertRange;
            
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = mc.level.getBlockState(pos);
                        if (state.isAir()) continue;
                        
                        Block block = state.getBlock();
                        if (harvestBlocks.get().contains(block) && isMature(state, block)) {
                            return true;
                        }
                    }
                }
            }
        } else {
            int r = range.get();
            BlockPos playerPos = mc.player.blockPosition();
            
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos pos = playerPos.offset(x, y, z);
                        double distance = Math.sqrt(x*x + y*y + z*z);
                        if (distance > r) continue;
                        
                        BlockState state = mc.level.getBlockState(pos);
                        if (state.isAir()) continue;
                        
                        Block block = state.getBlock();
                        if (harvestBlocks.get().contains(block) && isMature(state, block)) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }

    private void handleAbility() {
        long currentTime = System.currentTimeMillis();
        long cooldownMs = abilityCooldown.get() * 1000L;
        long durationMs = abilityDuration.get() * 1000L;
        long activationDelayMs = abilityActivationDelay.get() * 1000L;

        // Check if ability is currently active
        if (abilityActive) {
            if (currentTime - abilityActivatedAt >= durationMs) {
                abilityActive = false;
                abilityState = AbilityState.IDLE;
                lastAbilityActivation = currentTime;
                info("Ability duration ended. Next activation in " + abilityCooldown.get() + " seconds.");
            }
            return;
        }
        
        // Handle the ability sequence state machine
        switch (abilityState) {
            case IDLE -> {
                boolean shouldActivate = false;
                
                if (lastAbilityActivation == 0) {
                    if (currentTime >= activationDelayMs) shouldActivate = true;
                } else {
                    long timeSinceLastActivation = currentTime - lastAbilityActivation;
                    long timeToNextActivation = cooldownMs + activationDelayMs;
                    if (timeSinceLastActivation >= timeToNextActivation) shouldActivate = true;
                }
                
                // ONLY activate if there are crops to harvest!
                if (shouldActivate && hasHarvestableCrops()) {
                    abilityState = AbilityState.HOLDING_SHIFT;
                    shiftPressed = true;
                    shiftHoldTicks = 0;
                    mc.options.keyShift.setDown(true);
                    info("Crops detected! Holding shift for 1 second...");
                } else if (shouldActivate) {
                    lastAbilityActivation = currentTime;
                    if (debugMode.get()) {
                        info("No harvestable crops nearby - skipping ability activation");
                    }
                }
            }
            
            case HOLDING_SHIFT -> {
    shiftHoldTicks++;
    // Hold shift for 10 ticks (500ms)
    if (shiftHoldTicks >= 10) {
        // Double-check crops are still there before clicking
        if (hasHarvestableCrops()) {
            abilityState = AbilityState.FIRST_CLICK;
            clickCount = 0;
        } else {
            mc.options.keyShift.setDown(false);
            shiftPressed = false;
            abilityState = AbilityState.IDLE;
            lastAbilityActivation = currentTime;
            info("Crops no longer present - ability cancelled");
        }
    }
}

case FIRST_CLICK -> {
    Utils.rightClick();
    info("First right-click");
    clickCount = 2; // Wait 2 ticks (100ms) before second click
    abilityState = AbilityState.SECOND_CLICK;
}

case SECOND_CLICK -> {
    clickCount--;
    if (clickCount > 0) {
        // Still waiting for delay
        return;
    }
    Utils.rightClick();
    info("Second right-click");
    
    mc.options.keyShift.setDown(false);
    shiftPressed = false;
    
    abilityState = AbilityState.ACTIVE;
    abilityActive = true;
    abilityActivatedAt = currentTime;
    
    info("Ability activated! Duration: " + abilityDuration.get() + " seconds.");
}
            
            case ACTIVE -> {
                // Already handled by abilityActive check above
            }
        }
    }

    private void registerSphereBlocks() {
        int r = range.get();
        BlockIterator.register(r, r, (pos, state) -> {
            if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= r)
                blocks.add(blockPosPool.get().set(pos));
        });
    }

    private void registerCuboidBlocks() {
        Direction facing = mc.player.getDirection();
        
        int halfWidth = horizontalRange.get();
        int forward = forwardRange.get();
        int backward = backwardRange.get();
        int vertRange = verticalRange.get();
        
        BlockPos playerPos = mc.player.blockPosition();
        
        int minX, maxX, minZ, maxZ;
        
        switch (facing) {
            case NORTH:
                minX = playerPos.getX() - halfWidth;
                maxX = playerPos.getX() + halfWidth;
                minZ = playerPos.getZ() - forward;
                maxZ = playerPos.getZ() + backward;
                break;
            case SOUTH:
                minX = playerPos.getX() - halfWidth;
                maxX = playerPos.getX() + halfWidth;
                minZ = playerPos.getZ() - backward;
                maxZ = playerPos.getZ() + forward;
                break;
            case WEST:
                minX = playerPos.getX() - forward;
                maxX = playerPos.getX() + backward;
                minZ = playerPos.getZ() - halfWidth;
                maxZ = playerPos.getZ() + halfWidth;
                break;
            case EAST:
                minX = playerPos.getX() - backward;
                maxX = playerPos.getX() + forward;
                minZ = playerPos.getZ() - halfWidth;
                maxZ = playerPos.getZ() + halfWidth;
                break;
            default:
                return;
        }
        
        int minY = playerPos.getY() - vertRange;
        int maxY = playerPos.getY() + vertRange;
        
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()) continue;
                    
                    blocks.add(blockPosPool.get().set(pos));
                }
            }
        }
    }

    private boolean till(BlockPos pos, Block block) {
        if (!till.get()) return false;
        boolean moist = !this.moist.get() || isWaterNearby(mc.level, pos);
        boolean tillable = block == Blocks.GRASS_BLOCK ||
                block == Blocks.DIRT_PATH ||
                block == Blocks.DIRT ||
                block == Blocks.COARSE_DIRT ||
                block == Blocks.ROOTED_DIRT;
        if (moist && tillable && mc.level.getBlockState(pos.above()).isAir()) {
            FindItemResult hoe = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof HoeItem);
            return WorldUtils.interact(pos, hoe, rotate.get());
        }
        return false;
    }

    private boolean harvest(BlockPos pos, BlockState state, Block block) {
        if (!harvest.get()) return false;
        if (!harvestBlocks.get().contains(block)) return false;
        
        if (block == Blocks.SUGAR_CANE || block == Blocks.CACTUS) {
            if (mc.level.getBlockState(pos.below()).getBlock() == block) {
                BlockUtils.breakBlock(pos, true);
                return true;
            }
            return false;
        }
        
        if (!isMature(state, block)) return false;
        if (block instanceof SweetBerryBushBlock)
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Utils.vec3d(pos), Direction.UP, pos, false));
        else {
            BlockUtils.breakBlock(pos, true);
        }
        return true;
    }

    private boolean plant(BlockPos pos, Block block) {
        if (!plant.get()) return false;
        if (!mc.level.isEmptyBlock(pos.above())) return false;
        FindItemResult findItemResult = null;
        if (onlyReplant.get()) {
            for (BlockPos replantPos : replantMap.keySet()) {
                if (replantPos.equals(pos.above())) {
                    findItemResult = InvUtils.find(replantMap.get(replantPos));
                    replantMap.remove(replantPos);
                    break;
                }
            }
        } else if (block instanceof FarmBlock) {
            findItemResult = InvUtils.find(itemStack -> {
                Item item = itemStack.getItem();
                return item != Items.NETHER_WART && plantItems.get().contains(item);
            });
        } else if (block instanceof SoulSandBlock) {
            findItemResult = InvUtils.find(itemStack -> {
                Item item = itemStack.getItem();
                return item == Items.NETHER_WART && plantItems.get().contains(Items.NETHER_WART);
            });
        }
        if (findItemResult != null && findItemResult.found()) {
            BlockUtils.place(pos.above(), findItemResult, rotate.get(), -100, false);
            return true;
        }
        return false;
    }

    private boolean bonemeal(BlockPos pos, BlockState state, Block block) {
        if (!bonemeal.get()) return false;
        if (!bonemealBlocks.get().contains(block)) return false;
        if (isMature(state, block)) return false;

        FindItemResult bonemeal = InvUtils.findInHotbar(Items.BONE_MEAL);
        return WorldUtils.interact(pos, bonemeal, rotate.get());
    }

    private boolean isWaterNearby(LevelReader world, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (world.getFluidState(blockPos).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private boolean isMature(BlockState state, Block block) {
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        } else if (block instanceof CocoaBlock cocoaBlock) {
            return state.getValue(cocoaBlock.AGE) >= 2;
        } else if (block instanceof StemBlock) {
            return state.getValue(StemBlock.AGE) == StemBlock.MAX_AGE;
        } else if (block instanceof SweetBerryBushBlock sweetBerryBushBlock) {
            return state.getValue(sweetBerryBushBlock.AGE) >= 2;
        } else if (block instanceof NetherWartBlock netherWartBlock) {
            return state.getValue(netherWartBlock.AGE) >= 3;
        } else if (block instanceof PitcherCropBlock pitcherCropBlock) {
            return state.getValue(pitcherCropBlock.AGE) >= 4;
        } else if (block instanceof SaplingBlock) {
            return false;
        }
        return true;
    }

    private boolean bonemealFilter(Block block) {
        return block instanceof CropBlock ||
                block instanceof StemBlock ||
                block instanceof MushroomBlock ||
                block instanceof AzaleaBlock ||
                block instanceof SaplingBlock ||
                block == Blocks.COCOA ||
                block == Blocks.SWEET_BERRY_BUSH ||
                block == Blocks.PITCHER_CROP ||
                block == Blocks.TORCHFLOWER;
    }

    private boolean harvestFilter(Block block) {
        return block instanceof CropBlock ||
                block == Blocks.PUMPKIN ||
                block == Blocks.MELON ||
                block == Blocks.NETHER_WART ||
                block == Blocks.SWEET_BERRY_BUSH ||
                block == Blocks.COCOA ||
                block == Blocks.PITCHER_CROP ||
                block == Blocks.TORCHFLOWER ||
                block == Blocks.SUGAR_CANE ||
                block == Blocks.CACTUS;
    }

    private boolean plantFilter(Item item) {
        return item == Items.WHEAT_SEEDS ||
                item == Items.CARROT ||
                item == Items.POTATO ||
                item == Items.BEETROOT_SEEDS ||
                item == Items.PUMPKIN_SEEDS ||
                item == Items.MELON_SEEDS ||
                item == Items.NETHER_WART ||
                item == Items.PITCHER_POD ||
                item == Items.TORCHFLOWER_SEEDS;
    }

    public enum Shape {
        Sphere,
        Cuboid
    }
}