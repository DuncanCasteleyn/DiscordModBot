/*
 * Copyright 2018.  Duncan Casteleyn
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
package be.duncanc.discordmodbot.bot.utils

import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.hooks.InterfacedEventManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * This event manager uses a single thread executor service.
 * <p>
 * Created by Duncan on 12/06/2017.
 *
 * @property executor provides the service to execute event handling.
 * @since 1.1.0
 */
internal class ExecutorServiceEventManager
constructor(
        name: String
) : InterfacedEventManager() {

    private val executor: ExecutorService

    /**
     * Creates a single thread executor service.
     */
    init {
        executor = Executors.newSingleThreadExecutor {
            val t = Thread(it, ExecutorServiceEventManager::class.java.simpleName + ": " + name)
            t.isDaemon = true
            t
        }
    }

    /**
     * Executes the handle function of the super class using the ExecutorService.
     *
     * @see InterfacedEventManager.handle
     */
    override fun handle(event: Event) {
        executor.submit { super.handle(event) }
    }
}