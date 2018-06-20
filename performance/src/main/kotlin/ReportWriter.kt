import org.jetbrains.ring.Report
import org.jetbrains.ring.writeResultsToFile

interface ReportWriter {
    fun write(report: Report)
}

class CsvReportWriter(private val outputFileName: String) : ReportWriter {
    override fun write(report: Report) = writeResultsToFile(report, outputFileName)
}

class PrintReportWriter : ReportWriter {
    override fun write(report: Report) = printResultsNormalized(report)
}

private fun printResultsNormalized(results: Report) {
    var totalMean = 0.0
    var totalVariance = 0.0
    results.asSequence().sortedBy { it.key }.forEach {
        val niceName  = it.key.padEnd(50, ' ')
        val (mean, variance) = it.value
        println("$niceName : ${mean.toString(9)} : ${kotlin.math.sqrt(variance).toString(9)}")

        totalMean += mean
        totalVariance += variance
    }
    val averageMean = totalMean / results.size
    val averageStdDev = kotlin.math.sqrt(totalVariance) / results.size
    println("\nRingAverage: ${averageMean.toString(9)} : ${averageStdDev.toString(9)}")
}

private fun Double.toString(n: Int): String {
    val str = this.toString()
    if (str.contains('e', ignoreCase = true)) return str

    val len      = str.length
    val pointIdx = str.indexOf('.')
    val dropCnt  = len - pointIdx - n - 1
    if (dropCnt < 1) return str
    return str.dropLast(dropCnt)
}