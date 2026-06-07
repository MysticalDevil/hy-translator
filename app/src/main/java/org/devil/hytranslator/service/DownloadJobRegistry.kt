package org.devil.hytranslator.service

import kotlinx.coroutines.Job

internal class DownloadJobRegistry<T> {
    private val jobs = mutableMapOf<T, Job>()

    fun put(target: T, job: Job) {
        synchronized(jobs) {
            jobs[target] = job
        }
    }

    fun remove(target: T) {
        synchronized(jobs) {
            jobs.remove(target)
        }
    }

    fun cancel(target: T): List<T> =
        synchronized(jobs) {
            jobs.remove(target)?.cancel()
            listOf(target)
        }

    fun cancelAll(): List<T> =
        synchronized(jobs) {
            val targets = jobs.keys.toList()
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            targets
        }

    fun cancelActiveAndClear(): List<T> =
        synchronized(jobs) {
            val targets = jobs.filterValues { it.isActive }.keys.toList()
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            targets
        }

    fun isActive(target: T): Boolean =
        synchronized(jobs) {
            jobs[target]?.isActive == true
        }

    fun hasActive(): Boolean =
        synchronized(jobs) {
            jobs.values.any { it.isActive }
        }
}
