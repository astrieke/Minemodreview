package dev.strata.epochs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * Ein offener Zeitriss, wie er an der Dimension gespeichert wird (Data Attachment), damit er einen Neustart übersteht.
 *
 * @param pos      Position des Rissblocks als {@code BlockPos.asLong()}
 * @param epoch    Epoche, in die er reißt, z. B. {@code strata_epochs:spore_age}
 * @param openedAt Spielzeit, zu der er sich geöffnet hat
 */
public record RiftRecord(long pos, String epoch, long openedAt) {

    public static final Codec<RiftRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("pos").forGetter(RiftRecord::pos),
            Codec.STRING.fieldOf("epoch").forGetter(RiftRecord::epoch),
            Codec.LONG.fieldOf("opened_at").forGetter(RiftRecord::openedAt)
    ).apply(i, RiftRecord::new));

    public static final Codec<List<RiftRecord>> LIST_CODEC = CODEC.listOf();
}
