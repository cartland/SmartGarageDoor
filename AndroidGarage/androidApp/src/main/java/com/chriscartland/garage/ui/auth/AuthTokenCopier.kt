/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

package com.chriscartland.garage.ui.auth

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.chriscartland.garage.R
import com.chriscartland.garage.di.rememberAppComponent
import com.chriscartland.garage.domain.model.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Composable hook returning a click handler that:
 *   1. Fetches the current Firebase ID token via [GetAuthTokenForCopyUseCase].
 *   2. On success, writes the token to the system clipboard with
 *      `ClipDescription.EXTRA_IS_SENSITIVE` set so the OS preview chip
 *      (API 33+) redacts the value instead of displaying it on-screen.
 *   3. On failure (not signed in / token unavailable), shows a Toast
 *      *"Sign in to copy auth token"* so the user knows the copy didn't
 *      happen.
 *
 * Single source of truth for the developer-only token-copy action.
 * Both [DiagnosticsScreen] and [FunctionListContent] call this hook so
 * the two surfaces decode + format + write the token identically — a
 * regression in clipboard behavior breaks both call sites at once,
 * which is the verification property the user asked for ("powered by a
 * UseCase in both places so it should be easy to verify both").
 *
 * No success Toast: the OS clipboard preview chip with content hidden
 * is the confirmation; a Toast on top would be duplicate noise (mirrors
 * the [VersionBottomSheet] API-33+ clipboard pattern, PR #718).
 *
 * **Caller responsibility**: gate the visibility of the *button* that
 * invokes this handler on `Build.VERSION.SDK_INT >= TIRAMISU`. The
 * handler itself runs unconditionally — but on API < 33 the
 * `EXTRA_IS_SENSITIVE` flag has no redaction effect, so the OS preview
 * chip would briefly display the full JWT on every copy. Hiding the
 * button is the only way to keep the action safe on older Android.
 */
@Composable
fun rememberAuthTokenCopier(): () -> Unit {
    val component = rememberAppComponent()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val useCase = component.getAuthTokenForCopyUseCase
    return remember(useCase, context, scope) {
        {
            scope.launch {
                val result = withContext(Dispatchers.IO) { useCase() }
                when (result) {
                    is AppResult.Success -> SensitiveClipboard.write(context, result.data)
                    is AppResult.Error ->
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.auth_token_copier_sign_in_required),
                                Toast.LENGTH_SHORT,
                            ).show()
                }
            }
        }
    }
}

/**
 * Wrapper for the system clipboard write with the sensitive-content
 * flag. Grouped in a named `object` per ADR-009 (no bare top-level
 * functions). Single call site is [rememberAuthTokenCopier]; the
 * separation just makes the platform clipboard interaction
 * unit-greppable / boundary-clear from the UseCase + Composable
 * machinery above.
 */
private object SensitiveClipboard {
    /**
     * Write [token] to the system clipboard with `EXTRA_IS_SENSITIVE`
     * set, so Android 13+ redacts the value from the post-copy preview
     * chip. On older Android the flag is silently ignored — caller
     * must API-gate the button that invokes this writer (see
     * [rememberAuthTokenCopier] kdoc).
     */
    fun write(
        context: Context,
        token: String,
    ) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Firebase ID token", token)
        val extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        clip.description.extras = extras
        cm.setPrimaryClip(clip)
    }
}
