package bedwars.shop

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*


/**
 * Created by DEDZTBH on 2019/12/23.
 * Project bedwars
 */
data class ShopKeeper(
        val name: String,
        val location: Location,
        val onInteract: (PlayerInteractEntityEvent, ShopKeeper) -> Unit
)

object ShopKeeperManager : Listener {

    val shopKeepers = mutableMapOf<UUID, ShopKeeper>()

    // Spawning the ShopKeeper
    // Also add it to shopKeepers
    fun spawn(name: String, location: Location, onInteract: (PlayerInteractEntityEvent, ShopKeeper) -> Unit): ShopKeeper =
            ShopKeeper(name, location, onInteract).also {
                it.location.run {
                    world.spawn(this, Villager::class.java).apply {
                        customName = it.name
                        isCustomNameVisible = true
                        setAdult()
                        ageLock = true
                        profession = Villager.Profession.FARMER
                        addPotionEffect(PotionEffect(PotionEffectType.SLOW, Int.MAX_VALUE, 0xBADBED, false, false))
                        shopKeepers[uniqueId] = it
                    }
                }
            }

    fun forAllEntities(thatAre: (Entity) -> Boolean, doThis: (Entity) -> Unit) {
        Bukkit.getWorlds()
                .map { it.entities }
                .fold(mutableSetOf<Entity>()) { acc, ls ->
                    acc.addAll(ls.filter { thatAre(it) })
                    acc
                }.forEach { doThis(it) }
    }

    // Despawn the ShopKeeper
    fun despawn(shopKeeper: ShopKeeper) {
        forAllEntities(thatAre = { shopKeepers[it.uniqueId] == shopKeeper }, doThis = {
            shopKeepers.remove(it.uniqueId)
            it.remove()
        })
    }

    // Clean up all shopkeepers
    fun cleanUp() {
        forAllEntities(thatAre = { shopKeepers[it.uniqueId] != null }, doThis = { it.remove() })
        shopKeepers.clear()
    }

    // Disable Damage
    @EventHandler
    fun entityDamage(evt: EntityDamageEvent) =
            shopKeepers[evt.entity.uniqueId]?.run {
                evt.isCancelled = true
            }

    @EventHandler
    fun entityDamage(evt: EntityDamageByEntityEvent) = entityDamage(evt as EntityDamageEvent)

    // Disable Trading
    @EventHandler
    fun entityInteract(evt: PlayerInteractEntityEvent) =
            shopKeepers[evt.rightClicked.uniqueId]?.run {
                evt.isCancelled = true
                onInteract(evt, this)
            }
}
