package anticope.rejects.modules;

import anticope.rejects.MeteorRejectsAddon;
import anticope.rejects.utils.PauseOnGUIUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;

import java.util.List;
import java.util.Random;

public class AntiAFK extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgActions = settings.createGroup("Actions");
    private final SettingGroup sgMessages = settings.createGroup("Messages");

    // General Settings
    private final Setting<Boolean> pauseOnGui = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-on-gui")
            .description("Pause actions when a GUI is open.")
            .defaultValue(false)
            .build()
    );

    // Actions
    private final Setting<Boolean> jump = sgActions.add(new BoolSetting.Builder()
        .name("jump")
        .description("Jump randomly.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgActions.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings your hand.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SneakMode> sneakMode = sgActions.add(new EnumSetting.Builder<SneakMode>()
        .name("sneak-mode")
        .description("How to handle sneaking.")
        .defaultValue(SneakMode.Disabled)
        .build()
    );

    private final Setting<Integer> sneakTime = sgActions.add(new IntSetting.Builder()
        .name("sneak-time")
        .description("How many ticks to stay sneaked.")
        .defaultValue(5)
        .min(1)
        .sliderMin(1)
        .sliderMax(20)
        .visible(() -> sneakMode.get() == SneakMode.Tap)
        .build()
    );

    private final Setting<Boolean> strafe = sgActions.add(new BoolSetting.Builder()
        .name("strafe")
        .description("Strafe right and left.")
        .defaultValue(false)
        .onChanged(aBoolean -> {
            strafeTimer = 0;
            direction = false;

            if (isActive()) {
                mc.options.keyLeft.setDown(false);
                mc.options.keyRight.setDown(false);
            }
        })
        .build()
    );

    private final Setting<Integer> strafeTime = sgActions.add(new IntSetting.Builder()
        .name("strafe-time")
        .description("How many ticks to strafe in each direction.")
        .defaultValue(20)
        .min(1)
        .sliderMin(1)
        .sliderMax(60)
        .visible(strafe::get)
        .build()
    );

    private final Setting<Boolean> spin = sgActions.add(new BoolSetting.Builder()
        .name("spin")
        .description("Spins the player in place.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smoothSpin = sgActions.add(new BoolSetting.Builder()
        .name("smooth-spin")
        .description("Use smooth interpolation for spinning.")
        .defaultValue(true)
        .visible(spin::get)
        .build()
    );

    private final Setting<SpinMode> spinMode = sgActions.add(new EnumSetting.Builder<SpinMode>()
        .name("spin-mode")
        .description("The method of rotating.")
        .defaultValue(SpinMode.Server)
        .visible(spin::get)
        .build()
    );

    private final Setting<Double> spinSpeed = sgActions.add(new DoubleSetting.Builder()
        .name("speed")
        .description("The speed to spin you (degrees per tick).")
        .defaultValue(7.0)
        .min(0.1)
        .max(30.0)
        .decimalPlaces(1)
        .visible(spin::get)
        .build()
    );

    private final Setting<Integer> pitch = sgActions.add(new IntSetting.Builder()
        .name("pitch")
        .description("The pitch to send to the server.")
        .defaultValue(0)
        .range(-90, 90)
        .sliderRange(-90, 90)
        .visible(() -> spin.get() && spinMode.get() == SpinMode.Server)
        .build()
    );

    // Messages
    private final Setting<Boolean> sendMessages = sgMessages.add(new BoolSetting.Builder()
        .name("send-messages")
        .description("Sends messages to prevent getting kicked for AFK.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> randomMessage = sgMessages.add(new BoolSetting.Builder()
        .name("random")
        .description("Selects a random message from your message list.")
        .defaultValue(false)
        .visible(sendMessages::get)
        .build()
    );

    private final Setting<Integer> delay = sgMessages.add(new IntSetting.Builder()
        .name("delay")
        .description("The delay between specified messages in seconds.")
        .defaultValue(15)
        .min(0)
        .sliderMax(30)
        .visible(sendMessages::get)
        .build()
    );

    private final Setting<List<String>> messages = sgMessages.add(new StringListSetting.Builder()
        .name("messages")
        .description("The messages to choose from.")
        .defaultValue(
            "Meteor Rejects on top!",
            "Meteor Rejects on crack!"
        )
        .visible(sendMessages::get)
        .build()
    );

    public AntiAFK() {
        super(MeteorRejectsAddon.CATEGORY, "anti-afk", "Performs different actions to prevent getting kicked while AFK.");
    }

    private final Random random = new Random();
    private int messageTimer = 0;
    private int messageI = 0;
    private int sneakTimer = 0;
    private int strafeTimer = 0;
    private boolean direction = false;
    private double lastYaw;
    private boolean wasPaused = false;

    @Override
    public void onActivate() {
        if (sendMessages.get() && messages.get().isEmpty()) {
            warning("Message list is empty, disabling messages...");
            sendMessages.set(false);
        }

        lastYaw = mc.player.getYRot();
        messageTimer = delay.get() * 20;
        wasPaused = false;
    }

    @Override
    public void onDeactivate() {
        if (strafe.get()) {
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
        }
        
        // Release sneak if we were holding it
        if (sneakMode.get() == SneakMode.Hold) {
            mc.options.keyShift.setDown(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;
        
        // Check if we should pause
        boolean shouldPause = PauseOnGUIUtils.shouldPause(pauseOnGui.get());
        
        // If we just unpaused, reactivate timers and states
        if (wasPaused && !shouldPause) {
            onUnpause();
        }
        wasPaused = shouldPause;
        
        // Pause if GUI is open (exempts Meteor GUI, Chat, Pause)
        if (shouldPause) {
            return;
        }

        // Jump
        if (jump.get()) {
            if (mc.options.keyJump.isDown()) mc.options.keyJump.setDown(false);
            else if (random.nextInt(99) == 0) mc.options.keyJump.setDown(true);
        }

        // Swing
        if (swing.get() && random.nextInt(99) == 0) {
            mc.player.swing(mc.player.getUsedItemHand());
        }

        // Sneak
        switch (sneakMode.get()) {
            case Disabled -> {}
            case Hold -> mc.options.keyShift.setDown(true);
            case Tap -> {
                if (sneakTimer++ >= sneakTime.get()) {
                    mc.options.keyShift.setDown(false);
                    if (random.nextInt(99) == 0) sneakTimer = 0;
                } else {
                    mc.options.keyShift.setDown(true);
                }
            }
        }

        // Strafe
        if (strafe.get() && strafeTimer-- <= 0) {
            mc.options.keyLeft.setDown(!direction);
            mc.options.keyRight.setDown(direction);
            direction = !direction;
            strafeTimer = strafeTime.get();
        }

        // Spin
        if (spin.get()) {
            double targetYaw = lastYaw + spinSpeed.get();
            
            if (smoothSpin.get()) {
                // Smooth interpolation - spread rotation over multiple ticks
                double smoothSpeed = spinSpeed.get() / 20.0;
                
                switch (spinMode.get()) {
                    case Client -> {
                        double currentYaw = mc.player.getYRot();
                        double yawDiff = targetYaw - currentYaw;
                        
                        // Handle wrap-around
                        while (yawDiff > 180) yawDiff -= 360;
                        while (yawDiff < -180) yawDiff += 360;
                        
                        // Smooth step
                        if (Math.abs(yawDiff) < smoothSpeed) {
                            mc.player.setYRot((float) targetYaw);
                            lastYaw = targetYaw;
                        } else {
                            mc.player.setYRot((float) (currentYaw + Math.signum(yawDiff) * smoothSpeed));
                        }
                    }
                    case Server -> {
                        double currentYaw = mc.player.getYRot();
                        double yawDiff = targetYaw - currentYaw;
                        
                        while (yawDiff > 180) yawDiff -= 360;
                        while (yawDiff < -180) yawDiff += 360;
                        
                        if (Math.abs(yawDiff) < smoothSpeed) {
                            Rotations.rotate(targetYaw, pitch.get(), -15);
                            lastYaw = targetYaw;
                        } else {
                            Rotations.rotate(currentYaw + Math.signum(yawDiff) * smoothSpeed, pitch.get(), -15);
                        }
                    }
                }
            } else {
                // Original instant rotation
                lastYaw += spinSpeed.get();
                switch (spinMode.get()) {
                    case Client -> mc.player.setYRot((float) lastYaw);
                    case Server -> Rotations.rotate(lastYaw, pitch.get(), -15);
                }
            }
        }

        // Messages
        if (sendMessages.get() && !messages.get().isEmpty() && messageTimer-- <= 0) {
            if (randomMessage.get()) messageI = random.nextInt(messages.get().size());
            else if (++messageI >= messages.get().size()) messageI = 0;

            ChatUtils.sendPlayerMsg(messages.get().get(messageI));
            messageTimer = delay.get() * 20;
        }
    }

    private void onUnpause() {
        // Reset timers so actions don't all fire at once
        sneakTimer = 0;
        strafeTimer = 0;
        
        // Reset key states
        if (strafe.get()) {
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
        }
        
        if (sneakMode.get() != SneakMode.Hold) {
            mc.options.keyShift.setDown(false);
        }
        
        info("Unpaused - resuming AntiAFK");
    }

    public enum SpinMode {
        Server,
        Client
    }
    
    public enum SneakMode {
        Disabled,
        Hold,
        Tap
    }
}