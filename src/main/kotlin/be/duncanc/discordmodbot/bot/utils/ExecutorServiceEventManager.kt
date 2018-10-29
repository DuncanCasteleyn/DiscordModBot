/*
 * Copyright 2018 Duncan Casteleyn
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.hooks.InterfacedEventManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import kotlin.concurrent.thread

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
    companion object {
        val LOG: Logger = LoggerFactory.getLogger(ExecutorServiceEventManager::class.java)
    }

    private val executor = Executors.newSingleThreadExecutor {
        thread(name = "${ExecutorServiceEventManager::class.java.simpleName}: $name", start = false, isDaemon = true) {
            it.run()
        }
    }.asCoroutineDispatcher()

    /**
     * Executes the handle function of the super class using the ExecutorService.
     *
     * @see InterfacedEventManager.handle
     */
    override fun handle(event: Event) {
        GlobalScope.launch(executor) {
            try {
                super.handle(event)
            } catch (e: Exception) {
                LOG.error("One of the EventListeners had an uncaught exception", e)
            } catch (e: Error) {
                LOG.error("One of the EventListeners encountered an error", e)
                throw e
            }
        }
    }
}