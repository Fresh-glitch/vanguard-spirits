package io.github.freshglitch.vanguardspirits.item

import io.github.freshglitch.vanguardspirits.charm.CharmAura
import net.minecraft.world.item.Item

/**
 * A charm forged from Fractured Memories. Carrying one anywhere in the
 * inventory keeps its [aura] refreshed on the holder.
 *
 * Flavour text is attached as a default LORE component at registration rather
 * than by overriding `appendHoverText`, which is deprecated.
 */
class CharmItem(
	props: Item.Properties,
	val aura: CharmAura,
) : Item(props)
