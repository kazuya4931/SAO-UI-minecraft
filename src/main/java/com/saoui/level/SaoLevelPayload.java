package com.saoui.level;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server-to-client packet synchronizing the player's independent SAO level and partial XP. */
public record SaoLevelPayload(int level, int xp) implements CustomPayload {
    public static final CustomPayload.Id<SaoLevelPayload> ID =
            new CustomPayload.Id<>(Identifier.of("saoui", "sao_level"));

    public static final PacketCodec<ByteBuf, SaoLevelPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SaoLevelPayload::level,
            PacketCodecs.VAR_INT, SaoLevelPayload::xp,
            SaoLevelPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
