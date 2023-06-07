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

package io.element.android.features.login.impl.changeaccountprovider.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import io.element.android.libraries.architecture.Async
import io.element.android.libraries.architecture.Presenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChangeAccountProviderFormPresenter @Inject constructor(
    private val homeserverResolver: HomeserverResolver,
) : Presenter<ChangeAccountProviderFormState> {

    @Composable
    override fun present(): ChangeAccountProviderFormState {
        val localCoroutineScope = rememberCoroutineScope()

        var currentJob: Job? = remember { null }

        val userInput = rememberSaveable {
            mutableStateOf("")
        }
        val userInputResult: MutableState<Async<List<HomeserverData>>> = remember {
            mutableStateOf(Async.Uninitialized)
        }

        fun handleEvents(event: ChangeAccountProviderFormEvents) {
            when (event) {
                is ChangeAccountProviderFormEvents.UserInput -> {
                    currentJob?.cancel()
                    currentJob = localCoroutineScope.userInput(event.input, userInputResult)
                }
            }
        }

        return ChangeAccountProviderFormState(
            userInput = userInput.value,
            userInputResult = userInputResult.value,
            eventSink = ::handleEvents
        )
    }

    // Could be reworked using LaunchedEffect
    private fun CoroutineScope.userInput(userInput: String, state: MutableState<Async<List<HomeserverData>>>) = launch {
        state.value = Async.Uninitialized
        // Debounce
        delay(300)
        state.value = Async.Loading()
        try {
            val result = homeserverResolver.resolve(userInput)
            state.value = Async.Success(result)
        } catch (error: Throwable) {
            state.value = Async.Failure(error)
        }
    }
}
