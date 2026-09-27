package me.aleksilassila.litematica.printer.actions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public abstract class Action {
    abstract public void send(MinecraftClient client, ClientPlayerEntity player);

    /**
     * Number of game ticks the action handler should wait before sending the
     * next queued action. Most actions don't need an additional delay.
     */
    public int getPostActionDelayTicks() {
        return 0;
    }
}
