package org.jetbrains.ring


fun writeResultsToFile(results: Report, outputFileName: String) {
    for ((name, result) in results) {
        val (mean, variance) = result
        println("$name,$mean,$variance")
    }
}