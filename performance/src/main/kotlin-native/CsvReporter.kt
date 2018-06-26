package org.jetbrains.ring

import kotlinx.cinterop.*
import platform.posix.*


// Gradle will change stdout so we don't bother ourselves with file writing.
fun writeReportToCsv(results: Map<String, Results>) {
    for ((name, result) in results) {
        val (mean, variance) = result
        println("$name,$mean,$variance")
    }
}

fun readReportFromCsv(fileName: String): Report {
    val report = mutableMapOf<String, Results>()
    val file = fopen(fileName, "r")
    if (file == null) {
        perror("cannot open input file $fileName")
        return report
    }
    try {
        memScoped {
            val bufferLength = 64 * 1024
            val buffer = allocArray<ByteVar>(bufferLength)
            while (true) {
                val nextLine = fgets(buffer, bufferLength, file)?.toKString()
                if (nextLine != null && !nextLine.isEmpty()) {
                    val results = nextLine.split(',')
                    report[results[0]] = Results(results[1].toDouble(), results[1].toDouble())
                } else {
                    break
                }
            }
        }
    } finally {
        fclose(file)
    }
    return report
}