package me.aleksilassila.litematica.printer.actions;

import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Direction;

public class PrepareAction extends Action {
    public final PrinterPlacementContext context;
    public boolean modifyYaw = true;
    public boolean modifyPitch = true;
    public float yaw = 0;
    public float pitch = 0;
    private boolean inventorySelectionChanged = false;

    public PrepareAction(PrinterPlacementContext context) {
        this.context = context;
        Direction lookDirection = context.lookDirection;

        if (lookDirection != null && lookDirection.getAxis().isHorizontal()) {
            this.yaw = lookDirection.getPositiveHorizontalDegrees();
        } else {
            this.modifyYaw = false;
        }

        if (lookDirection == Direction.UP) {
            this.pitch = -90;
        } else if (lookDirection == Direction.DOWN) {
            this.pitch = 90;
        } else if (lookDirection != null) {
            this.pitch = 0;
        } else {
            this.modifyPitch = false;
        }
    }

    public PrepareAction(PrinterPlacementContext context, float yaw, float pitch) {
        this.context = context;

        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void send(MinecraftClient client, ClientPlayerEntity player) {
        inventorySelectionChanged = false;
        ItemStack itemStack = context.getStack();
        int slot = context.requiredItemSlot;

        if (itemStack != null && client.interactionManager != null) {
            PlayerInventory inventory = player.getInventory();

            // Vanilla's own PlayerInventory#addPickBlock and
            // ClientPlayerInteractionManager#pickFromInventory were removed upstream
            // (creative pick-block is now server-authoritative via PickItemFromBlockC2SPacket,
            // which needs a real world block and can't conjure an arbitrary ItemStack), so both
            // are reimplemented locally below using their old, still-available building blocks.
            if (player.getAbilities().creativeMode) {
                addPickBlock(inventory, itemStack);
                client.interactionManager.clickCreativeStack(player.getStackInHand(Hand.MAIN_HAND),
                        36 + inventory.getSelectedSlot());
                inventorySelectionChanged = true;
            } else if (slot != -1) {
                if (PlayerInventory.isValidHotbarIndex(slot)) {
                    if (inventory.getSelectedSlot() != slot) {
                        inventory.setSelectedSlot(slot);
                        inventorySelectionChanged = true;
                    }
                } else {
                    client.interactionManager.clickSlot(player.playerScreenHandler.syncId, slot,
                            inventory.getSelectedSlot(), SlotActionType.SWAP, player);
                    inventorySelectionChanged = true;
                }
            }
        }

        if (modifyPitch || modifyYaw) {
            float yaw = modifyYaw ? this.yaw : player.getYaw();
            float pitch = modifyPitch ? this.pitch : player.getPitch();

            PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.Full(player.getX(), player.getY(), player.getZ(), yaw,
                    pitch, player.isOnGround(), player.horizontalCollision);

            player.networkHandler.sendPacket(packet);
        }

        // Sneaking is no longer a discrete ClientCommandC2SPacket mode; it's part of the
        // continuous PlayerInput record, sent explicitly here instead of waiting for the
        // client's own per-tick input sync so the server sees it before the next action.
        if (context.shouldSneak) {
            player.input.playerInput = new PlayerInput(player.input.playerInput.forward(), player.input.playerInput.backward(), player.input.playerInput.left(), player.input.playerInput.right(), player.input.playerInput.jump(), true, player.input.playerInput.sprint());
        } else {
            player.input.playerInput = new PlayerInput(player.input.playerInput.forward(), player.input.playerInput.backward(), player.input.playerInput.left(), player.input.playerInput.right(), player.input.playerInput.jump(), false, player.input.playerInput.sprint());
        }
        player.networkHandler.sendPacket(new PlayerInputC2SPacket(player.input.playerInput));
    }

    /**
     * Reimplementation of the removed PlayerInventory#addPickBlock: selects the given stack in
     * the hotbar, or conjures it into a swappable hotbar slot if the player doesn't have it.
     */
    private static void addPickBlock(PlayerInventory inventory, ItemStack stack) {
        int slot = inventory.getSlotWithStack(stack);

        if (PlayerInventory.isValidHotbarIndex(slot)) {
            inventory.setSelectedSlot(slot);
        } else if (slot == -1) {
            int hotbarSlot = inventory.getSwappableHotbarSlot();
            inventory.setSelectedSlot(hotbarSlot);

            if (!inventory.getStack(hotbarSlot).isEmpty()) {
                int emptySlot = inventory.getEmptySlot();
                if (emptySlot != -1) {
                    inventory.setStack(emptySlot, inventory.getStack(hotbarSlot));
                }
            }

            inventory.setStack(hotbarSlot, stack);
        } else {
            inventory.swapSlotWithHotbar(slot);
        }
    }

    @Override
    public int getPostActionDelayTicks() {
        return inventorySelectionChanged ? 1 : 0;
    }

    @Override
    public String toString() {
        return "PrepareAction{" +
                "context=" + context +
                '}';
    }
}
