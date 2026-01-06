package data.scripts.weapons

import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.ShieldAPI
import java.awt.Color

class MPC_shieldNodeLargeScript: MPC_shieldNodeScript() {
    override val id: String = "MPC_shieldNodeLarge"

    override fun getShieldType(): ShieldAPI.ShieldType {
        return ShieldAPI.ShieldType.FRONT
    }

    override fun getShieldArc(): Float {
        return 120f
    }

    override fun getFluxCapacity(): Float {
        return 10000f
    }

    override fun getShieldEff(): Float {
        return 0.5f
    }

    override fun getFluxDissipation(): Float {
        return 900f
    }

    override fun getHardFluxFraction(): Float {
        return 0.5f
    }

    override fun getOverloadTimeMult(): Float {
        return 3f
    }

    override fun getShieldOuterColor(): Color? {
        return Color(255, 255, 255, 255)
    }

    override fun getShieldRadius(entity: CombatEntityAPI?): Float {
        return 290f
    }

    override fun getShieldInnerColor(): Color? {
        return Color(215, 125, 255, 95)
    }
}