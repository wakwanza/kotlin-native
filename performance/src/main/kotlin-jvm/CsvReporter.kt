package org.jetbrains.ring

import java.io.File


// Gradle will change stdout so we don't bother ourselves with file writing.
fun writeReportToCsv(results: Report) {
    for ((name, result) in results) {
        val (mean, variance) = result
        println("$name,$mean,$variance")
    }
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