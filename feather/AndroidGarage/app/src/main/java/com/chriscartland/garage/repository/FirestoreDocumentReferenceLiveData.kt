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

package com.chriscartland.garage.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.chriscartland.garage.MainActivity
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

/**
 * Based on:
 * https://firebase.googleblog.com/2017/12/using-android-architecture-components.html
 */
class FirestoreDocumentReferenceLiveData(
    documentReference: DocumentReference?
) : LiveData<DocumentSnapshot?>() {

    private var active = false
    private var listenerRegistration: ListenerRegistration? = null

    var documentReference: DocumentReference? = documentReference
        set(value) {
            field = value
            Log.d(TAG, "setter: documentReference")
            if (active) {
                listenerRegistration?.remove()
                listenerRegistration = value?.addSnapshotListener(listener)
            }
        }

    private val listener: EventListener<DocumentSnapshot?> =
        Listener(
            this
        )
    private class Listener(
        val liveData: FirestoreDocumentReferenceLiveData
    ) : EventListener<DocumentSnapshot?> {
        override fun onEvent(value: DocumentSnapshot?, error: FirebaseFirestoreException?) {
            if (error != null) {
                Log.w(MainActivity.TAG, "Event listener failed.", error)
            }
            if (value != null && value.exists()) {
                liveData.value = value
            }
        }
    }

    override fun onActive() {
        super.onActive()
        Log.d(TAG, "onActive")
        active = true
        listenerRegistration = documentReference?.addSnapshotListener(listener)
    }

    override fun onInactive() {
        super.onInactive()
        Log.d(TAG, "onInactive")
        active = false
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    companion object {
        val TAG: String = FirestoreDocumentReferenceLiveData::class.java.simpleName
    }
}