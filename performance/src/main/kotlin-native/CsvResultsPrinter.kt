package org.jetbrains.ring


fun writeResultsToFile(results: Map<String, Results>, outputFileName: String) {
    for ((name, result) in results) {
        val (mean, variance) = result
        println("$name,$mean,$variance")
    }
}