import kotlin.math.*
fun main() {
    val springiness = 7.0
    val oscillations = 1.0
    for (i in 0..20) {
        val t = i / 20.0
        val tIn = 1.0 - t
        val expo = (exp(springiness * tIn) - 1.0) / (exp(springiness) - 1.0)
        val valIn = expo * sin((PI * 2.0 * oscillations + PI / 2.0) * tIn)
        val valOut = 1.0 - valIn
        println("t=" + String.format("%.2f", t) + ": " + String.format("%.4f", valOut))
    }
}
