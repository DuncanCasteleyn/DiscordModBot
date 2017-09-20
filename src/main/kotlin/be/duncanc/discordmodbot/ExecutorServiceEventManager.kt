/*
 * MIT License
 *
 * Copyright (c) 2017 Duncan Casteleyn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package be.duncanc.discordmodbot

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
internal class ExecutorServiceEventManager : InterfacedEventManager() {

    private val executor: ExecutorService

    /**
     * Creates a single thread executor service.
     */
    init {
        executor = Executors.newSingleThreadExecutor {
            val t = Thread(it, this.toString())
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