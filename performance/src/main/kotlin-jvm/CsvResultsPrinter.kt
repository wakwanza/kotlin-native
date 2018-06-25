package org.jetbrains.ring

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths


fun writeResultsToFile(results: Report, outputFileName: String) {
    val file = Paths.get(outputFileName)
    val lines = results.map { (name, result) ->
        val (mean, variance) = result
        "$name,$mean,$variance"
    }
    Files.write(file, lines, Charset.forName("UTF-8"))
}

fun readReportFromCsv(inputFileName: String): Report {
    val report = mutableMapOf<String, Results>()
    File(inputFileName).useLines { lines ->
        for (line in lines) {
            val results = line.split(',')
            report[results[0]] = Results(results[1].toDouble(), results[1].toDouble())
        }
    }
    return report
}