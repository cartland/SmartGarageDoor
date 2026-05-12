package com.chriscartland.garage.datalocal

/**
 * Platform-specific factory for the app's Room [AppDatabase].
 *
 * Android `actual` takes a `Context` constructor parameter to resolve
 * the database file path via `context.getDatabasePath("database")`.
 * iOS `actual` resolves the path via `NSDocumentDirectory` and takes
 * no constructor arguments.
 *
 * Construction is cheap; cache the result of [createDatabase] via the
 * DI provider's `@Singleton` scope (not inside the factory). Calling
 * [createDatabase] twice on the same factory instance would build two
 * Room databases for the same file — which is fine for SQLite but
 * defeats Room's identity-map and write-conflict guarantees.
 */
expect class DatabaseFactory {
    fun createDatabase(): AppDatabase
}
