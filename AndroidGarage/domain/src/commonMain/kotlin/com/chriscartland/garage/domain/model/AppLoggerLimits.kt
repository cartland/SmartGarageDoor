package com.chriscartland.garage.domain.model

/**
 * Tunables for the AppLogger retention policy.
 *
 * Lives in `domain.model` (not `domain.repository`) so ViewModels can
 * reference the default without crossing the layer-import check that
 * forbids ViewModels from importing repository interfaces.
 */
object AppLoggerLimits {
    /**
     * Default per-key row cap. Single source of truth so the
     * per-write cap (in `RoomAppLoggerRepository.log`) and the
     * startup-prune cap (in `RoomAppLoggerRepository.pruneToLimit`)
     * cannot drift.
     */
    const val DEFAULT_PER_KEY_LIMIT: Int = 1000
}
