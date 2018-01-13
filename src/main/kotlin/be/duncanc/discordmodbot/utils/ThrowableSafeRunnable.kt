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

package be.duncanc.discordmodbot.utils

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
class ThrowableSafeRunnable constructor(private val runnable: Runnable, private val logger: Logger) : Runnable {

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
