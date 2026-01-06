package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CollisionClass
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.OnFireEffectPlugin
import com.fs.starfarer.api.combat.ShieldAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.util.IntervalUtil
import data.scripts.weapons.MPC_shieldMissileScript.MPC_shieldMissileEveryframeScript.Companion.updateDronePos
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.ceil

abstract class MPC_shieldMissileScript: OnFireEffectPlugin, MPC_shieldProvider {

    companion object {
        const val SHIELD_DRONE_DATA_TAG = "MPC_shieldMissile"
        const val DRONE_DATA_KEY = "MPC_shieldDroneKey"
        const val RAYCAST_FAIL_TIMES_TIL_END = 5
        const val RAYCAST_STEP_MULT = 8f
        const val DAMAGE_NULLIFIED_KEY = "MPC_shieldNullifiedDamage"

        fun addShieldDroneParameters(drone: ShipAPI, id: String) {
            drone.isHoldFire = true
            drone.mutableStats.engineDamageTakenMult.modifyMult(id, 0f)
            drone.mutableStats.dynamic.getStat(Stats.SHIELD_PIERCED_MULT).modifyMult(id, 0f)
            drone.mutableStats.hullDamageTakenMult.modifyMult(id, 0f) // cant kill it

            drone.isRenderEngines = false
            drone.isDoNotRenderSprite = true
            drone.activeLayers.clear()

            drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.KEEP_SHIELDS_ON)

            drone.collisionClass = CollisionClass.FIGHTER
            drone.hullSize = ShipAPI.HullSize.FIGHTER
            drone.addListener(MPC_shieldDroneProjectileNullifier())
        }
    }

    abstract val id: String
    open val variantId: String = "niko_shield_drone_Shield"

    override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI) {
        if (projectile == null) return

        createShieldDrone(projectile, engine)
        projectile.setCustomData(SHIELD_DRONE_DATA_TAG, true)
        if (!Global.getCombatEngine().listenerManager.hasListenerOfClass(MPC_shieldDroneDamageTakenMod::class.java)) {
            Global.getCombatEngine().listenerManager.addListener(MPC_shieldDroneDamageTakenMod())
        }
    }

    open fun createShieldDrone(projectile: DamagingProjectileAPI, engine: CombatEngineAPI): ShipAPI {
        val fleetManager = engine.getFleetManager(projectile.owner)
        val oldSuppress = fleetManager.isSuppressDeploymentMessages
        fleetManager.isSuppressDeploymentMessages = true
        val drone = fleetManager.spawnShipOrWing(variantId, projectile.location, 0f) // we update its movement later
        drone.isAlly = projectile.source?.isAlly == true
        fleetManager.isSuppressDeploymentMessages = oldSuppress

        addShieldDroneParameters(drone, id)

        val shield = createShieldAndFlux(drone, projectile, id)
        if (projectile is MissileAPI) {
            val empResist = ceil((shield.radius * 0.1f)).toInt()
            projectile.empResistance = empResist
        }

        updateDronePos(drone, projectile)
        val script = MPC_shieldMissileEveryframeScript(drone, projectile)
        engine.addPlugin(script)

        projectile.setCustomData(DRONE_DATA_KEY, drone)

        return drone
    }

    class MPC_shieldDroneDamageTakenMod: DamageTakenModifier {
        override fun modifyDamageTaken(
            param: Any?,
            target: CombatEntityAPI?,
            damage: DamageAPI?,
            point: Vector2f?,
            shieldHit: Boolean
        ): String? {
            if (target?.customData[SHIELD_DRONE_DATA_TAG] != true) return null
            val drone = (target.customData[DRONE_DATA_KEY] as? ShipAPI) ?: return null
            val shield = drone.shield

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

    class MPC_shieldDroneProjectileNullifier(): DamageTakenModifier {
        override fun modifyDamageTaken(
            param: Any?,
            target: CombatEntityAPI?,
            damage: DamageAPI?,
            point: Vector2f?,
            shieldHit: Boolean
        ): String? {
            if (param !is DamagingProjectileAPI) return null
            if (!shieldHit) return null

            if (param.projectileSpec?.isPassThroughFighters == true) {
                Global.getCombatEngine().removeEntity(param)
            }

            return null
        }
    }

    open class MPC_shieldMissileEveryframeScript(
        val drone: ShipAPI,
        val projectile: DamagingProjectileAPI
    ): BaseEveryFrameCombatPlugin() {

        var fading = false
        var storedHP = 0f

        companion object {
            fun updateDronePos(drone: ShipAPI, projectile: CombatEntityAPI) {
                val newPos = getTargetDronePos(drone, projectile)
                drone.location.set(newPos)
                if (drone.shield.type != ShieldAPI.ShieldType.OMNI) {
                    drone.facing = projectile.facing
                }
            }

            fun getTargetDronePos(drone: ShipAPI, projectile: CombatEntityAPI): Vector2f {
                val projLocCopy = Vector2f(projectile.location)
                return projLocCopy
            }
        }

        val checkInterval = IntervalUtil(0f, 0f) // seconds

        override fun advance(amount: Float, events: List<InputEventAPI?>?) {
            super.advance(amount, events)

            val engine = Global.getCombatEngine()
            if (engine.isPaused) return

            checkInterval.advance(amount)
            if (checkInterval.intervalElapsed()) {

                val projHpFraction = (projectile.hitpoints / projectile.maxHitpoints).coerceAtLeast(0.000001f)
                val ourNewHp = drone.maxHitpoints * projHpFraction
                drone.hitpoints = ourNewHp // might as well use the status bar for something

                if (drone.shield.activeArc >= 360f) { // so we can be SURE it wont die with a bubble shield
                    storedHP = projectile.maxHitpoints
                    projectile.hitpoints = Float.MAX_VALUE
                } else if (storedHP > 0f) {
                    projectile.hitpoints = storedHP
                    storedHP = 0f
                }

                updateDronePos(drone, projectile)
                if (fading) {
                    drone.aiFlags.removeFlag(ShipwideAIFlags.AIFlags.KEEP_SHIELDS_ON)
                    drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_SHIELDS)
                    if (drone.shield.isOn) drone.shield.toggleOff()

                    if (drone.shield.activeArc <= 0f) {
                        delete()
                        return
                    }
                } else {
                    var forceShield = true
                    if (projectile.isFading) {
                        drone.aiFlags.removeFlag(ShipwideAIFlags.AIFlags.KEEP_SHIELDS_ON)
                        drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_SHIELDS)
                        if (drone.shield.isOn) drone.shield.toggleOff()
                        forceShield = false
                    }

                    if (projectile is MissileAPI) {
                        if (projectile.isFizzling) {
                            drone.aiFlags.removeFlag(ShipwideAIFlags.AIFlags.KEEP_SHIELDS_ON)
                            drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_SHIELDS)
                            if (drone.shield.isOn) drone.shield.toggleOff()
                            forceShield = false
                        }
                    }

                    if (forceShield && drone.shield.isOff && !drone.fluxTracker.isOverloaded) drone.shield.toggleOn() // idk why but its super hesitant to do this otherwise, even with the ai flag


                    if (projectile.isExpired || !Global.getCombatEngine().isEntityInPlay(projectile)) {
                        fading = true
                    }
                }

                val flux = drone.fluxTracker.fluxLevel
                val shieldOpacity = (1 - (flux)).coerceAtLeast(0.01f)
                drone.extraAlphaMult2 = shieldOpacity
            }
        }

        fun delete() {
            Global.getCombatEngine().removePlugin(this)
            Global.getCombatEngine().removeEntity(drone)
        }
    }
}