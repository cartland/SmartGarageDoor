/*
 * Copyright 2021 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage

import android.app.Application
import com.chriscartland.garage.db.AppDatabase
import com.chriscartland.garage.disk.LocalDataSource
import com.chriscartland.garage.internet.RemoteDataSource
import com.chriscartland.garage.repository.AppVersionManager
import com.chriscartland.garage.repository.FirestoreConfigManager
import com.chriscartland.garage.repository.Repository
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class App : Application() {

    private val executors = AppExecutors()

    private val db: AppDatabase
        get() = AppDatabase.getDatabase(this)

    private val localDataSource: LocalDataSource
        get() = LocalDataSource.getInstance(executors, db)

    private val appVersionManager: AppVersionManager
        get() = AppVersionManager(packageManager, packageName)

    private val remoteDataSource: RemoteDataSource
        get() = RemoteDataSource.getInstance(executors)

    private val firestoreConfigManager: FirestoreConfigManager
        get() = FirestoreConfigManager(
            Firebase.firestore.collection("configCurrent").document("current")
        )

    lateinit var repository: Repository

    override fun onCreate() {
        super.onCreate()
        repository = Repository.getInstance(
            localDataSource = localDataSource,
            appVersionManager = appVersionManager,
            firestoreConfigManager = firestoreConfigManager,
            remoteDataSource = remoteDataSource
        )
    }
}
