package io.github.freshglitch.vanguardspirits.item

import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * The raw material every charm is forged from.
 *
 * Behaves like any other item; the only thing it does differently is refuse to
 * settle on a colour. Its texture drifts through the four charms' own rune
 * colours because it has not decided which of them it is going to become, and
 * this carries that drift into the name so the word and the picture agree.
 *
 * [getName] is the single point worth overriding for that: `ItemStack.getItemName`
 * routes through it, and `getHoverName` routes through *that*, so the tooltip
 * title and the hotbar popup both pick the colour up without either being
 * touched directly.
 */
class FracturedMemoryItem(properties: Properties) : Item(properties) {

	/**
	 * Called every frame a tooltip is up, so this allocates per frame -- the same
	 * bargain vanilla already makes for any item whose name is not a constant,
	 * and the alternative is caching a value whose whole purpose is to change.
	 */
	override fun getName(stack: ItemStack): Component =
		Component.translatable(descriptionId).withColor(MemoryHue.current())
}
