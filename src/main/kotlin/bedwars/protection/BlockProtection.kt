package bedwars.protection

import bedwars.util.then
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityChangeBlockEvent
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Created by DEDZTBH on 2019/12/25.
 * Project bedwars
 */

typealias Vec3 = Triple<Float, Float, Float>

sealed class ProtectionArea {
    abstract fun isInArea(b: Block): Boolean

    data class Rect(val low: Vec3, val high: Vec3): ProtectionArea() {
        override fun isInArea(b: Block) =
                low.first <= b.x && b.x <= high.first &&
                low.second <= b.y && b.y <= high.second &&
                low.third <= b.z && b.z <= high.third
    }

    data class Sphere(val center: Vec3, val radius: Float): ProtectionArea() {
        override fun isInArea(b: Block) =
                sqrt((b.x - center.first).pow(2)
                        + (b.y - center.second).pow(2)
                        + (b.z - center.third).pow(2)) <= radius
    }
}


object BlockProtection : Listener {
    val protectedAreas = mutableSetOf<ProtectionArea>()

    @JvmStatic
    fun loadProtectedAreas(config: ConfigurationSection, path: String = "protected") =
            config.getConfigurationSection(path).getKeys(false).forEach {
                when (config.getString("$path.$it.type")) {
                    "sphere" -> ProtectionArea.Sphere(getVec3(config, "$path.$it.center"), config.getDouble("$path.$it.radius").toFloat())
                    else -> ProtectionArea.Rect(getVec3(config, "$path.$it.low"), getVec3(config, "$path.$it.high"))
                }.run(protectedAreas::add)
            }

    fun getVec3(config: ConfigurationSection, path: String): Vec3 = config.run {
        val x = getDouble("$path.x").toFloat()
        val y = getDouble("$path.y").toFloat()
        val z = getDouble("$path.z").toFloat()
        Vec3(x, y, z)
    }

    private fun <T> processEvent(evt: T): Boolean where T : BlockEvent, T : Cancellable =
            processBlock(evt.block).also {
                if (it) evt.isCancelled = true
            }

    private fun processBlock(b: Block): Boolean =
            protectedAreas.any { b.run(it::isInArea) }

    @EventHandler fun blockEvent(evt: BlockBreakEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: BlockBurnEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: BlockDamageEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: BlockExplodeEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: BlockFormEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: BlockFadeEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: BlockFromToEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: BlockGrowEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: BlockIgniteEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: BlockMultiPlaceEvent) =
            evt.replacedBlockStates.any { processBlock(it.block) } then { evt.isCancelled = true }
    @EventHandler fun blockEvent(evt: BlockPlaceEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: BlockSpreadEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: EntityBlockFormEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: LeavesDecayEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: SignChangeEvent) = processEvent(evt)
    @EventHandler fun blockEvent(evt: EntityChangeBlockEvent) =
            processBlock(evt.block) then { evt.isCancelled = true }
}