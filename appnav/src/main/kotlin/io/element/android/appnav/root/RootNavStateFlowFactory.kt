/*
 * Copyright (c) 2023 New Vector Ltd
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
 */

package io.element.android.appnav.root

import com.bumble.appyx.core.state.MutableSavedStateMap
import com.bumble.appyx.core.state.SavedStateMap
import io.element.android.appnav.di.MatrixClientsHolder
import io.element.android.features.login.api.LoginUserStory
import io.element.android.features.onboarding.api.LoginSharedCredentialUserStory
import io.element.android.features.preferences.api.CacheService
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.sessionstorage.api.LoggedInState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

private const val SAVE_INSTANCE_KEY = "io.element.android.x.RootNavStateFlowFactory.SAVE_INSTANCE_KEY"

/**
 * This class is responsible for creating a flow of [RootNavState].
 * It gathers data from multiple datasource and creates a unique one.
 */
class RootNavStateFlowFactory @Inject constructor(
    private val authenticationService: MatrixAuthenticationService,
    private val cacheService: CacheService,
    private val matrixClientsHolder: MatrixClientsHolder,
    private val loginUserStory: LoginUserStory,
    private val loginSharedCredentialUserStory: LoginSharedCredentialUserStory
) {
    private var currentCacheIndex = 0

    fun create(savedStateMap: SavedStateMap?): Flow<RootNavState> {
        return combine(
            cacheIndexFlow(savedStateMap),
            authenticationService.loggedInStateFlow(),
            loginUserStory.loginFlowIsDone,
            loginSharedCredentialUserStory.loginFlowIsDone
        ) { cacheIndex, loggedInState, loginFlowIsDone, loginSharedCredentialFlowIsDone ->
            Timber.tag("Onboarding").d("loginSharedCredentialFlowIsDone $loginFlowIsDone $loginSharedCredentialFlowIsDone $loggedInState")
            if (loginFlowIsDone || loginSharedCredentialFlowIsDone) {
                RootNavState(cacheIndex = cacheIndex, loggedInState = loggedInState)
            } else {
                RootNavState(cacheIndex = cacheIndex, loggedInState = LoggedInState.NotLoggedIn)
            }
        }
    }

    fun saveIntoSavedState(stateMap: MutableSavedStateMap) {
        stateMap[SAVE_INSTANCE_KEY] = currentCacheIndex
    }

    /**
     * @return a flow of integer, where each time a clear cache is done, we have a new incremented value.
     */
    private fun cacheIndexFlow(savedStateMap: SavedStateMap?): Flow<Int> {
        val initialCacheIndex = savedStateMap.getCacheIndexOrDefault()
        return cacheService.clearedCacheEventFlow
            .onEach { sessionId ->
                matrixClientsHolder.remove(sessionId)
            }
            .toIndexFlow(initialCacheIndex)
            .onEach { cacheIndex ->
                currentCacheIndex = cacheIndex
            }
    }

    /**
     * @return a flow of integer that increments the value by one each time a new element is emitted upstream.
     */
    private fun Flow<Any>.toIndexFlow(initialValue: Int): Flow<Int> = flow {
        var index = initialValue
        emit(initialValue)
        collect {
            emit(++index)
        }
    }

    private fun SavedStateMap?.getCacheIndexOrDefault(): Int {
        return this?.get(SAVE_INSTANCE_KEY) as? Int ?: 0
    }
}
