package data.scripts.weapons

import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.ShieldAPI
import com.fs.starfarer.api.combat.ShipAPI
import java.awt.Color

interface MPC_shieldProvider {

    fun getShieldType(): ShieldAPI.ShieldType
    fun getShieldUpkeep(): Float = 0f
    fun getShieldEff(): Float = 1f
    fun getShieldArc(): Float

    fun getFluxCapacity(): Float
    fun getFluxDissipation(): Float
    fun getHardFluxFraction(): Float = 0f
    fun getOverloadTimeMult(): Float = 1f
    fun getShieldUnfoldMult(): Float = 1f

    fun getShieldInnerColor(): Color? = null
    fun getShieldOuterColor(): Color? = null

    fun getShieldRadius(entity: CombatEntityAPI? = null): Float = if (entity != null) entity.collisionRadius * 3f else 50f

    fun createShieldAndFlux(drone: ShipAPI, projectile: CombatEntityAPI?, id: String): ShieldAPI {
        drone.setShield(
            getShieldType(),
            getShieldUpkeep(),
            getShieldEff(),
            getShieldArc(),
        )
        drone.mutableStats.fluxCapacity.modifyFlat(id, getFluxCapacity())
        drone.mutableStats.fluxDissipation.modifyFlat(id, getFluxDissipation())
        drone.mutableStats.hardFluxDissipationFraction.modifyFlat(id, getHardFluxFraction())
        drone.mutableStats.overloadTimeMod.modifyMult(id, getOverloadTimeMult())
        drone.mutableStats.shieldUnfoldRateMult.modifyFlat(id, getShieldUnfoldMult())

        drone.shield.radius = getShieldRadius(projectile)
        drone.collisionRadius = drone.shield.radius * 1.2f

        val shieldInner = getShieldInnerColor()
        val shieldOuter = getShieldOuterColor()

        if (shieldInner != null) {
            drone.shield.innerColor = shieldInner
        }
        if (shieldOuter != null) {
            drone.shield.ringColor = shieldOuter
        }

        return drone.shield
    }
}