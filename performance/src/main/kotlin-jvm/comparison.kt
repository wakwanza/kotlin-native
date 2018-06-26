import org.jetbrains.ring.readReportFromCsv
import kotlin.math.sqrt

fun main(args: Array<String>) {
    val jvmReport = readReportFromCsv(args[0])
    val konanReport = readReportFromCsv(args[1])
    jvmReport.forEach { k, v ->
        val (konanMean, konanVar) = konanReport[k]!!
        val ratio = konanMean / v.mean
        val minRatio = (konanMean - konanVar) / (v.mean + v.variance)
        val maxRatio = (konanMean + konanVar) / (v.mean - v.variance)
        val ratioVar = Math.min(Math.abs(minRatio - ratio), Math.abs(maxRatio - ratio))
        val formattedKonanValue = "${(konanVar / 1000).toString(4)} us ± ${(sqrt(konanVar / 1000)).toString(4)} us"
        val formattedRatio = "${ratio.toString(2)} ± ${sqrt(ratioVar).toString(2)}"

        println("$k : absolute = $formattedKonanValue, ratio = $formattedRatio")
        if (System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE") != null)
            println("##teamcity[buildStatisticValue key='$k' value='$ratio']")
    }

    val totalBaselineMean = jvmReport.toList().fold(0.0) { acc, r -> acc + r.second.mean }
    val totalKonanMean = konanReport.toList().fold(0.0) { acc, r -> acc + r.second.mean }
    val totalBaselineStdDev = sqrt(jvmReport.toList().fold(0.0) { acc, r -> acc + r.second.variance })
    val totalKonanStdDev = sqrt(konanReport.toList().fold(0.0) { acc, r -> acc + r.second.variance })

    println()
    val averageMean = totalKonanMean / konanReport.size
    val averageStdDev = totalKonanStdDev / konanReport.size
    val averageRatioMean = totalKonanMean / totalBaselineMean
    val averageRatioStdDev = totalKonanStdDev / totalBaselineStdDev
    println("Average Ring score: absolute = ${averageMean.toString(2)} ± ${averageStdDev.toString(2)}, " +
            "ratio = ${averageRatioMean.toString(2)} ± ${averageRatioStdDev.toString(2)}")
}