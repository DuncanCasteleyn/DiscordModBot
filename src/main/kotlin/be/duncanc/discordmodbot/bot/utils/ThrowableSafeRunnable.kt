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

import org.slf4j.Logger

/**
 * This object wraps a runnable making it possible to easily create a runnable that catches a `Throwable` and logs it.
 *
 *
 * Created by Duncan on 12/06/2017.
 *
 * @property runnable The Runnable we need to run and secure from throwing exceptions.
 * @property logger A logger to log any throwable that we might catch so it be resolved later.
 * @since 1.1.0
 */
class ThrowableSafeRunnable
constructor(
        private val runnable: Runnable,
        private val logger: Logger
) : Runnable {

    /**
     * Runs the runnable given when this object was created and secures it against a `Throwable`.

     * @see Thread.run
     */
    override fun run() {
        try {
            runnable.run()
        } catch (t: Throwable) {
            logger.error("An error occurred during the execution", t)
        }
    }
}
