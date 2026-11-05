package dev.strata.epochs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * Eine laufende Transplantation, wie sie an der Dimension gespeichert wird (Data Attachment),
 * damit sie einen Neustart des Servers übersteht.
 *
 * @param center Mittelpunkt als {@code BlockPos.asLong()}
 * @param owner  UUID des Spielers, der sie gestartet hat (leer = Befehl/Test)
 * @param phase  erreichte Phase ({@link TransplantJob.Phase#ordinal()})
 */
public record TransplantRecord(long center, int radius, String epoch, String owner, int phase) {

    public static final Codec<TransplantRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("center").forGetter(TransplantRecord::center),
            Codec.INT.fieldOf("radius").forGetter(TransplantRecord::radius),
            Codec.STRING.fieldOf("epoch").forGetter(TransplantRecord::epoch),
            Codec.STRING.optionalFieldOf("owner", "").forGetter(TransplantRecord::owner),
            Codec.INT.optionalFieldOf("phase", 0).forGetter(TransplantRecord::phase)
    ).apply(i, TransplantRecord::new));

    public static final Codec<List<TransplantRecord>> LIST_CODEC = CODEC.listOf();
}
