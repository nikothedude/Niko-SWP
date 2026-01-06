package data.scripts.weapons

import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.ShieldAPI
import java.awt.Color

class MPC_shieldNodeSmallScript: MPC_shieldNodeScript() {
    override val id: String = "MPC_shieldNodeSmall"

    override fun getShieldType(): ShieldAPI.ShieldType {
        return ShieldAPI.ShieldType.FRONT
    }

    override fun getShieldArc(): Float {
        return 90f
    }

    override fun getFluxCapacity(): Float {
        return 1200f
    }

    override fun getFluxDissipation(): Float {
        return 250f
    }

    override fun getHardFluxFraction(): Float {
        return 0.5f
    }

    override fun getShieldOuterColor(): Color? {
        return Color(255, 255, 255, 255)
    }

    override fun getShieldInnerColor(): Color? {
        return Color(215, 125, 255, 95)
    }

    override fun getShieldRadius(entity: CombatEntityAPI?): Float {
        return 95f
    }
}