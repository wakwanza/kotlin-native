package runtime.workers.atomicLong

import kotlin.test.*

import konan.worker.*

@Test
fun incrementLong() {
    val workers = Array<Worker>(10, { _ -> startWorker() })
    val atomic = AtomicLong(1234567890L)
    val futures = Array(10, { workerIndex ->
        workers[workerIndex].schedule(TransferMode.CHECKED, { atomic }) {
            input -> input.increment()
        }
    })
    futures.forEach {
        it.result()
    }
    assertEquals(atomic.get(), 1234567900L)

    workers.forEach { it.requestTermination().consume { _ -> } }
}