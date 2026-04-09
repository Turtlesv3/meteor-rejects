package anticope.rejects.modules;

import anticope.rejects.MeteorRejectsAddon;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CaptchaSolver extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTest = settings.createGroup("Test Mode");
    private final SettingGroup sgFile = settings.createGroup("File Settings");
    
    // ===== TEST MODE SETTINGS =====
    private final Setting<Boolean> testMode = sgTest.add(new BoolSetting.Builder()
            .name("test-mode")
            .description("Enable test mode to manually test slot clicking.")
            .defaultValue(false)
            .build()
    );
    
    private final Setting<Integer> testSlot = sgTest.add(new IntSetting.Builder()
            .name("test-slot")
            .description("Slot ID to click when test mode is enabled.")
            .defaultValue(22)
            .min(0)
            .max(80)
            .visible(testMode::get)
            .build()
    );
    
    private final Setting<ClickType> testClickType = sgTest.add(new EnumSetting.Builder<ClickType>()
            .name("test-click-type")
            .description("Type of click to perform.")
            .defaultValue(ClickType.LEFT_CLICK)
            .visible(testMode::get)
            .build()
    );
    
    private final Setting<Integer> testDelay = sgTest.add(new IntSetting.Builder()
            .name("test-delay")
            .description("Ticks to wait before clicking after screen opens.")
            .defaultValue(10)
            .min(1)
            .max(2000)
            .visible(testMode::get)
            .build()
    );
    
    private final Setting<Boolean> testScanInventory = sgTest.add(new BoolSetting.Builder()
            .name("test-scan-inventory")
            .description("Log all items in container slots when screen opens.")
            .defaultValue(true)
            .visible(testMode::get)
            .build()
    );
    
    public enum ClickType {
        LEFT_CLICK,
        RIGHT_CLICK,
        SHIFT_CLICK,
        PICKUP,
        QUICK_MOVE
    }
    
    // ===== GENERAL SETTINGS =====
    private final Setting<Integer> scanDelay = sgGeneral.add(new IntSetting.Builder()
            .name("scan-delay")
            .description("Ticks to wait before scanning after screen opens.")
            .defaultValue(10)
            .min(1)
            .max(2000)
            .visible(() -> !testMode.get())
            .build()
    );
    
    private final Setting<Integer> solveCooldown = sgGeneral.add(new IntSetting.Builder()
            .name("solve-cooldown")
            .description("Seconds to wait before scanning again after a solve.")
            .defaultValue(5)
            .min(1)
            .max(60)
            .visible(() -> !testMode.get())
            .build()
    );
    
    private final Setting<Boolean> autoClose = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-close")
            .description("Automatically close container after solving.")
            .defaultValue(false)
            .visible(() -> !testMode.get())
            .build()
    );
    
    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
            .name("debug-mode")
            .description("Log detailed information for debugging.")
            .defaultValue(true)
            .build()
    );
    
    private final Setting<Integer> minFilledSlots = sgGeneral.add(new IntSetting.Builder()
            .name("min-filled-slots")
            .description("Minimum filled container slots to trigger CAPTCHA detection.")
            .defaultValue(5)
            .min(1)
            .max(45)
            .visible(() -> !testMode.get())
            .build()
    );
    
    // ===== FILE SETTINGS =====
    private final Setting<String> triggerFile = sgFile.add(new StringSetting.Builder()
            .name("trigger-file")
            .description("File to create when CAPTCHA is detected.")
            .defaultValue("C:\\Users\\HP\\Desktop\\minecraftcaptcha\\trigger.txt")
            .visible(() -> !testMode.get())
            .build()
    );
    
    private final Setting<String> responseFile = sgFile.add(new StringSetting.Builder()
            .name("response-file")
            .description("File where AI writes the detected item.")
            .defaultValue("C:\\Users\\HP\\Desktop\\minecraftcaptcha\\response.txt")
            .visible(() -> !testMode.get())
            .build()
    );
    
    private final Setting<Integer> aiTimeout = sgGeneral.add(new IntSetting.Builder()
            .name("ai-timeout")
            .description("Seconds to wait for AI response.")
            .defaultValue(15)
            .min(1)
            .max(30)
            .visible(() -> !testMode.get())
            .build()
    );
    
    // State
    private State state = State.IDLE;
    private int timer = 0;
    private long lastSolveTime = 0;
    private boolean hasScanned = false;
    private String detectedItem = null;
    private int foundSlot = -1;
    private boolean testClicked = false;
    
    private enum State {
        IDLE,
        TRIGGERING,
        WAITING_AI,
        FINDING_SLOT,
        CLICKING,
        WAITING_CLOSE,
        COOLDOWN,
        TEST_WAITING,
        TEST_CLICKING
    }
    
    public CaptchaSolver() {
        super(MeteorRejectsAddon.CATEGORY, "captcha-solver", "Automatically solves CAPTCHAs using external OCR AI.");
    }
    
    @Override
    public void onActivate() {
        state = State.IDLE;
        timer = 0;
        hasScanned = false;
        lastSolveTime = 0;
        detectedItem = null;
        foundSlot = -1;
        testClicked = false;
        
        if (!testMode.get()) {
            try {
                Files.deleteIfExists(Paths.get(triggerFile.get()));
                Files.deleteIfExists(Paths.get(responseFile.get()));
            } catch (IOException e) {}
        }
    }
    
    @Override
    public void onDeactivate() {
        state = State.IDLE;
        hasScanned = false;
    }
    
    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        hasScanned = false;
        detectedItem = null;
        foundSlot = -1;
        testClicked = false;
        
        if (event.screen instanceof ContainerScreen) {
            if (debugMode.get()) info("📱 Container screen opened.");
            
            if (testMode.get()) {
                if (testScanInventory.get()) {
                    scanAndLogInventory();
                }
                timer = testDelay.get();
                state = State.TEST_WAITING;
                info("🧪 Test Mode: Will click slot " + testSlot.get() + " in " + testDelay.get() + " ticks");
            } else {
                if (debugMode.get()) info("Checking for CAPTCHA...");
                timer = scanDelay.get();
                state = State.TRIGGERING;
            }
        }
    }
    
    private int getContainerSize() {
        if (mc.player == null || mc.player.containerMenu == null) return 0;
        
        // Total slots minus player inventory (36 slots = 27 main + 9 hotbar)
        int totalSlots = mc.player.containerMenu.slots.size();
        return totalSlots - 36;
    }
    
    private void scanAndLogInventory() {
        if (mc.player == null || mc.player.containerMenu == null) return;
        
        int containerSize = getContainerSize();
        
        info("=== CONTAINER SCAN (Slots 0-" + (containerSize - 1) + ") ===");
        int filledCount = 0;
        
        for (int i = 0; i < containerSize; i++) {
            if (i < mc.player.containerMenu.slots.size()) {
                var slot = mc.player.containerMenu.getSlot(i);
                if (slot.hasItem()) {
                    filledCount++;
                    ItemStack stack = slot.getItem();
                    String itemName = stack.getDisplayName().getString();
                    int count = stack.getCount();
                    info("  Slot " + i + ": " + count + "x " + itemName);
                }
            }
        }
        
        info("=== Total filled container slots: " + filledCount + " ===");
    }
    
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (timer > 0) {
            timer--;
            return;
        }
        
        // TEST MODE STATE MACHINE
        if (testMode.get()) {
            switch (state) {
                case TEST_WAITING -> {
                    state = State.TEST_CLICKING;
                }
                
                case TEST_CLICKING -> {
                    performTestClick();
                    state = State.IDLE;
                }
            }
            return;
        }
        
        // NORMAL MODE STATE MACHINE
        switch (state) {
            case IDLE -> {}
            
            case TRIGGERING -> {
                if (isCaptchaScreen()) {
                    if (!hasScanned || System.currentTimeMillis() - lastSolveTime > solveCooldown.get() * 1000L) {
                        createTriggerFile();
                        state = State.WAITING_AI;
                        timer = aiTimeout.get() * 20;
                    } else {
                        if (debugMode.get()) info("CAPTCHA already solved recently, skipping.");
                        state = State.IDLE;
                    }
                } else {
                    if (debugMode.get()) info("Not a CAPTCHA screen.");
                    state = State.IDLE;
                }
            }
            
            case WAITING_AI -> {
                String item = readResponseFile();
                if (item != null && !item.isEmpty()) {
                    detectedItem = item;
                    info("📦 Received item: '" + detectedItem + "'");
                    state = State.FINDING_SLOT;
                } else if (timer == 0) {
                    warning("AI response timeout - no item received");
                    state = State.COOLDOWN;
                    timer = 20;
                }
            }
            
            case FINDING_SLOT -> {
                foundSlot = findSlotByItemName(detectedItem);
                if (foundSlot != -1) {
                    info("✅ Found '" + detectedItem + "' in container slot " + foundSlot);
                    state = State.CLICKING;
                } else {
                    warning("Could not find slot with item: '" + detectedItem + "'");
                    state = State.COOLDOWN;
                    timer = 20;
                }
            }
            
            case CLICKING -> {
                if (verifySlot(foundSlot, detectedItem)) {
                    clickSlot(foundSlot, ClickType.LEFT_CLICK);
                    info("👆 Clicked container slot " + foundSlot);
                    lastSolveTime = System.currentTimeMillis();
                    hasScanned = true;
                    state = State.WAITING_CLOSE;
                    timer = 40;
                } else {
                    warning("Item no longer in slot " + foundSlot + " - re-scanning");
                    foundSlot = findSlotByItemName(detectedItem);
                    if (foundSlot != -1) {
                        clickSlot(foundSlot, ClickType.LEFT_CLICK);
                        info("👆 Clicked container slot " + foundSlot);
                        lastSolveTime = System.currentTimeMillis();
                        hasScanned = true;
                        state = State.WAITING_CLOSE;
                        timer = 40;
                    } else {
                        warning("Item disappeared from container");
                        state = State.COOLDOWN;
                        timer = 20;
                    }
                }
            }
            
            case WAITING_CLOSE -> {
                if (mc.screen == null) {
                    info("🎉 CAPTCHA solved! Screen closed by server.");
                    detectedItem = null;
                    foundSlot = -1;
                    state = State.COOLDOWN;
                    timer = 10;
                } else if (timer == 0) {
                    warning("Screen didn't close - CAPTCHA may have failed");
                    if (autoClose.get()) {
                        mc.player.closeContainer();
                    }
                    detectedItem = null;
                    foundSlot = -1;
                    state = State.COOLDOWN;
                    timer = 10;
                }
            }
            
            case COOLDOWN -> {
                state = State.IDLE;
            }
            
            default -> {}
        }
    }
    
    private void performTestClick() {
        if (mc.player == null || mc.player.containerMenu == null) {
            error("No container open!");
            return;
        }
        
        int slot = testSlot.get();
        ClickType clickType = testClickType.get();
        int containerSize = getContainerSize();
        
        if (slot < 0 || slot >= containerSize) {
            error("Invalid container slot: " + slot + " (container has " + containerSize + " slots)");
            return;
        }
        
        var slotObj = mc.player.containerMenu.getSlot(slot);
        if (slotObj.hasItem()) {
            ItemStack stack = slotObj.getItem();
            info("🧪 Test clicking container slot " + slot + " containing: " + stack.getCount() + "x " + stack.getDisplayName().getString());
        } else {
            info("🧪 Test clicking empty container slot " + slot);
        }
        
        clickSlot(slot, clickType);
        info("🧪 Performed " + clickType + " on container slot " + slot);
        testClicked = true;
    }
    
    private void clickSlot(int slot, ClickType clickType) {
        if (mc.player == null || mc.player.containerMenu == null) {
            warning("No container open to click!");
            return;
        }
        
        int containerSize = getContainerSize();
        if (slot < 0 || slot >= containerSize) {
            warning("Invalid container slot: " + slot);
            return;
        }
        
        try {
            switch (clickType) {
                case LEFT_CLICK, PICKUP -> InvUtils.click().slotId(slot);
                case SHIFT_CLICK, QUICK_MOVE -> InvUtils.shiftClick().slotId(slot);
                case RIGHT_CLICK -> InvUtils.click().slotId(slot);
            }
            info("👆 Clicked container slot " + slot);
        } catch (Exception e) {
            error("Failed to click slot: " + e.getMessage());
        }
    }
    
    private boolean verifySlot(int slot, String expectedItem) {
        if (mc.player == null || mc.player.containerMenu == null) return false;
        
        int containerSize = getContainerSize();
        if (slot < 0 || slot >= containerSize) return false;
        
        var slotObj = mc.player.containerMenu.getSlot(slot);
        if (!slotObj.hasItem()) return false;
        
        ItemStack stack = slotObj.getItem();
        String slotItemName = stack.getDisplayName().getString().toLowerCase().trim();
        String expected = expectedItem.toLowerCase().trim();
        
        return slotItemName.equals(expected) || 
               slotItemName.contains(expected) || 
               expected.contains(slotItemName);
    }
    
    private void createTriggerFile() {
        try {
            Path path = Paths.get(triggerFile.get());
            Files.createDirectories(path.getParent());
            Files.writeString(path, String.valueOf(System.currentTimeMillis()));
            Files.deleteIfExists(Paths.get(responseFile.get()));
            info("⚡ Trigger file created");
        } catch (IOException e) {
            error("Failed to create trigger file: " + e.getMessage());
        }
    }
    
    private String readResponseFile() {
        try {
            Path path = Paths.get(responseFile.get());
            if (!Files.exists(path)) return null;
            
            String content = Files.readString(path).trim();
            Files.delete(path);
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            return null;
        }
    }
    
    private boolean isCaptchaScreen() {
        if (mc.player == null || mc.player.containerMenu == null) return false;
        
        int containerSize = getContainerSize();
        
        // CAPTCHA screen must be EXACTLY 45 slots - not less, not more
        if (containerSize != 45) {
            if (debugMode.get()) {
                info("Container has " + containerSize + " slots (CAPTCHA requires exactly 45) - skipping");
            }
            return false;
        }
        
        int filledSlots = 0;
        for (int i = 0; i < containerSize; i++) {
            if (i < mc.player.containerMenu.slots.size()) {
                if (mc.player.containerMenu.getSlot(i).hasItem()) {
                    filledSlots++;
                }
            }
        }
        
        boolean isCaptcha = filledSlots >= minFilledSlots.get();
        
        if (debugMode.get() && isCaptcha) {
            info("🔐 CAPTCHA detected: " + filledSlots + " filled slots in 45 container slots");
        }
        
        return isCaptcha;
    }
    
    private int findSlotByItemName(String itemName) {
        if (mc.player == null || mc.player.containerMenu == null) return -1;
        if (itemName == null || itemName.isEmpty()) return -1;
        
        String searchName = itemName.toLowerCase().trim();
        int containerSize = getContainerSize();
        
        for (int i = 0; i < containerSize; i++) {
            if (i < mc.player.containerMenu.slots.size()) {
                var slot = mc.player.containerMenu.getSlot(i);
                if (slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    String slotItemName = stack.getDisplayName().getString().toLowerCase().trim();
                    
                    if (slotItemName.equals(searchName) || 
                        slotItemName.contains(searchName) || 
                        searchName.contains(slotItemName)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }
}