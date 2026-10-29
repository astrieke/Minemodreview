package dev.strata.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strata.core.mc.Mc;
import java.util.List;

/**
 * Ein Knoten im Erdkodex, im Datapack unter {@code data/<ns>/strata_core/codex_node/<name>.json}.
 *
 * @param requires Knoten, die vorher freigeschaltet sein müssen
 */
public record CodexNode(List<String> requires) {

    public static final Codec<CodexNode> CODEC = RecordCodecBuilder.create(i -> i.group(
            Mc.ID_CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(CodexNode::requires)
    ).apply(i, CodexNode::new));
}
