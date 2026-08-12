package io.github.freshglitch.vanguardspirits.block.entity

import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * The one thing a placed Epitaph knows: whose name is on it.
 *
 * Null until somebody engraves it, which is also the whole of the "is it
 * finished" question -- there is no blockstate for it, because the stone does
 * not change. Nothing draws the name either: a six pixel face cannot hold one,
 * so it is reported on the action bar instead and this class exists only to
 * remember it. The blockstate stays at four variants of FACING.
 *
 * Still synced to clients even though nothing renders it, because it is one
 * short string and a block entity whose contents are server-only is a trap the
 * next person to add a renderer would fall into.
 */
class EpitaphBlockEntity(pos: BlockPos, state: BlockState) :
	BlockEntity(ModBlockEntities.EPITAPH, pos, state) {

	var engraved: String? = null
		private set

	val isEngraved: Boolean get() = engraved != null

	/**
	 * Cuts a name into the stone. Once only.
	 *
	 * Returns whether it took, so the caller can tell "I just did that" from "it
	 * already said that" and answer differently. Refusing here rather than
	 * trusting the block to ask first means no future route to engraving -- a
	 * command, a dispenser, anything -- can quietly overwrite somebody else's
	 * name.
	 */
	fun engrave(name: String): Boolean {
		if (engraved != null) return false
		engraved = name
		published()
		return true
	}

	override fun saveAdditional(output: ValueOutput) {
		super.saveAdditional(output)
		engraved?.let { output.putString(NAME_KEY, it) }
	}

	override fun loadAdditional(input: ValueInput) {
		super.loadAdditional(input)
		engraved = input.getString(NAME_KEY).orElse(null)
	}

	/**
	 * The name has to reach clients or the renderer draws a blank stone forever.
	 *
	 * `setChanged()` alone only marks the chunk unsaved -- it sends nothing --
	 * which looks exactly like engraving having failed. This is vanilla's
	 * `CampfireBlockEntity.markUpdated` shape, passing the same state twice
	 * because nothing about the block itself changed.
	 */
	private fun published() {
		setChanged()
		level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_ALL)
	}

	override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
		saveCustomOnly(registries)

	override fun getUpdatePacket(): Packet<ClientGamePacketListener>? =
		ClientboundBlockEntityDataPacket.create(this)

	companion object {
		private const val NAME_KEY = "Engraved"
	}
}
