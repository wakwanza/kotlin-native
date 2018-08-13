package runtime.workers.freeze4

import kotlin.test.*

object Keys {
    internal val myMap: Map<String, List<String>> = mapOf(
            "val1" to listOf("a1", "a2", "a3"),
            "val2" to listOf("b1", "b2")
    )

    fun getName(name: String): String {
        for (entry in myMap) {
            if (entry.value.contains(name)) {
                return entry.key
            }
        }
        return name
    }
}

@Test fun runTest() {
    println(Keys.getName("a1"))
}
