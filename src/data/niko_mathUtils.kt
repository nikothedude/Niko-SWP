package data.utilities

import org.jetbrains.annotations.Contract
import org.lazywizard.lazylib.MathUtils
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.round
import kotlin.math.roundToInt

object niko_mathUtils {

    fun prob(chance: Int, random: Random = MathUtils.getRandom()): Boolean {
        return prob(chance.toDouble(), random)
    }

    fun prob(chance: Float, random: Random = MathUtils.getRandom()): Boolean {
        return prob(chance.toDouble(), random)
    }

    fun prob(chance: Double, random: Random = MathUtils.getRandom()): Boolean {
        return (random.nextFloat() * 100f < chance)
    }
}