package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin
import com.fs.starfarer.api.combat.OnFireEffectPlugin
import com.fs.starfarer.api.combat.ShieldAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.util.Misc
import data.scripts.weapons.MPC_shieldMissileScript.Companion.DAMAGE_NULLIFIED_KEY
import data.scripts.weapons.MPC_shieldMissileScript.Companion.RAYCAST_FAIL_TIMES_TIL_END
import data.scripts.weapons.MPC_shieldMissileScript.Companion.RAYCAST_STEP_MULT
import data.scripts.weapons.MPC_shieldMissileScript.MPC_shieldDroneDamageTakenMod
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f

abstract class MPC_shieldNodeScript: EveryFrameWeaponEffectPlugin, MPC_shieldProvider {

    companion object {
        const val HARDPOINT_EFF_MULT = 0.8f
    }

    abstract val id: String
    val engine = Global.getCombatEngine()
    var droneInitialized = false
    lateinit var drone: ShipAPI
    var ship: ShipAPI? = null
    val listener = ShieldNodeHostDamageTakenMod(this)

    override fun advance(
        amount: Float,
        engine: CombatEngineAPI?,
        weapon: WeaponAPI?
    ) {
        if (weapon == null || engine == null) return
        if (engine.isPaused) return
        ship = weapon.ship
        if (ship != null && !ship!!.hasListener(listener)) {
            ship!!.addListener(listener)
        }

        weapon.setForceFireOneFrame(false)
        addDroneIfNoneExists(weapon)
        updateDrone(weapon)
    }

    private fun disableShield() {
        drone.aiFlags.removeFlag(ShipwideAIFlags.AIFlags.KEEP_SHIELDS_ON)
        drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_SHIELDS)

        if (drone.shield.isOn) {
            drone.shield.toggleOff()
        }
    }

    private fun enableShield() {
        drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.KEEP_SHIELDS_ON)
        drone.aiFlags.removeFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_SHIELDS)

        if (drone.fluxTracker.isOverloadedOrVenting) return
        if (drone.shield.isOff) {
            drone.shield.toggleOn()
        }
    }

    private fun addDroneIfNoneExists(weapon: WeaponAPI) {
        if (!droneInitialized) {
            drone = createDrone(weapon)
            droneInitialized = true
        }
    }

    open fun createDrone(weapon: WeaponAPI): ShipAPI {
        val owner = weapon.ship?.owner ?: 1
        val fleetManager = engine.getFleetManager(owner)
        val oldSuppress = fleetManager.isSuppressDeploymentMessages
        fleetManager.isSuppressDeploymentMessages = true
        val drone = MPC_shieldMissileScript.createProcessingFXDrone()
        //val drone = fleetManager.spawnShipOrWing(variantId, weapon.location, 0f) // we update its movement later
        drone.isAlly = weapon.ship?.isAlly == true
        fleetManager.isSuppressDeploymentMessages = oldSuppress

        MPC_shieldMissileScript.addShieldDroneParameters(drone, id)
        createShieldAndFlux(drone, null, id)
        if (weapon.slot.isHardpoint) {
            drone.setShield(
                drone.shield.type,
                drone.shield.upkeep,
                drone.shield.fluxPerPointOfDamage * HARDPOINT_EFF_MULT,
                drone.shield.arc
            )
        }

        return drone
    }

    private fun updateDrone(weapon: WeaponAPI) {
        drone.location.set(weapon.location.x, weapon.location.y)
        drone.facing = weapon.currAngle

        val ship = weapon.ship
        if (ship != null) {
            if (ship.shield?.isOn == true || ship.phaseCloak?.isOn == true) {
                drone.mutableStats.hardFluxDissipationFraction.modifyMult("${id}_shieldOn", 0f)
            } else {
                drone.mutableStats.hardFluxDissipationFraction.unmodify("${id}_shieldOn")
            }

            if (!engine.isEntityInPlay(ship) || ship.isHulk) {
                disableShield()
                engine.removeEntity(drone)
                return
            }
            if (ship.fluxTracker.isOverloaded || ship.fluxTracker.isVenting || ship.isHoldFire || ship.isHoldFireOneFrame) {
                disableShield()
                return
            }
        }

        if (weapon.isDisabled || weapon.isForceNoFireOneFrame) {
            disableShield()
        } else {
            enableShield()
        }

        val tracker = drone.fluxTracker
        val flux = tracker.fluxLevel
        val shieldOpacity = (1 - (flux)).coerceAtLeast(0.01f)
        drone.extraAlphaMult2 = shieldOpacity
    }

    class ShieldNodeHostDamageTakenMod(val script: MPC_shieldNodeScript): DamageTakenModifier {
        override fun modifyDamageTaken(
            param: Any?,
            target: CombatEntityAPI?,
            damage: DamageAPI?,
            point: Vector2f?,
            shieldHit: Boolean
        ): String? {
            if (target == null || target != script.ship) return null
            val drone = script.drone
            val shield = drone.shield ?: return null
            if (drone.shield.activeArc <= 0f) return null

            var iterPoint = Vector2f(point)
            val dir = VectorUtils.getDirectionalVector(point, target.location)

            var fails = 0f
            while (fails < RAYCAST_FAIL_TIMES_TIL_END) {

                if (!shield.isWithinArc(iterPoint)) {
                    fails++
                    iterPoint = iterPoint.translate(dir.x * RAYCAST_STEP_MULT, dir.y * RAYCAST_STEP_MULT)
                    continue
                }

                damage?.modifier?.modifyMult(DAMAGE_NULLIFIED_KEY, 0f)

                return DAMAGE_NULLIFIED_KEY
            }
            return null
        }
    }
}