/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package be.duncanc.discordmodbot.bot.utils

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * General IO stream manipulation utilities.
 *
 *
 * This class provides static utility methods for input/output operations.
 *
 *  * closeQuietly - these methods close a stream ignoring nulls and exceptions
 *  * toXxx/read - these methods read data from a stream
 *  * write - these methods write data to a stream
 *  * copy - these methods copy all the data from one stream to another
 *  * contentEquals - these methods compare the content of two streams
 *
 *
 *
 * The byte-to-char methods and char-to-byte methods involve a conversion step.
 * Two methods are provided in each case, one that uses the platform default
 * encoding and the other which allows you to specify an encoding. You are
 * encouraged to always specify an encoding because relying on the platform
 * default can lead to unexpected results, for example when moving from
 * development to production.
 *
 *
 * All the methods in this class that read a stream are buffered internally.
 * This means that there is no cause to use a `BufferedInputStream`
 * or `BufferedReader`. The default buffer size of 4K has been shown
 * to be efficient in tests.
 *
 *
 * Wherever possible, the methods in this class do *not* flush or close
 * the stream. This is to avoid making non-portable assumptions about the
 * streams' origin and further use. Thus the caller is still responsible for
 * closing streams after use.
 *
 *
 * Origin of code: Excalibur.
 */
object IOUtils {
    // NOTE: This class is focused on InputStream, OutputStream, Reader and
    // Writer. Each method should take at least one of these as a parameter,
    // or return one of them.
    /**
     * Represents the end-of-file (or stream).
     * @since 2.5 (made public)
     */
    const val EOF = -1

    /**
     * The default buffer size ({@value}) to use for
     * [.copyLarge].
     */
    private const val DEFAULT_BUFFER_SIZE = 1024 * 4

    // copy from InputStream
    //-----------------------------------------------------------------------
    /**
     * Copies bytes from an `InputStream` to an
     * `OutputStream`.
     *
     *
     * This method buffers the input internally, so there is no need to use a
     * `BufferedInputStream`.
     *
     *
     * Large streams (over 2GB) will return a bytes copied value of
     * `-1` after the copy has completed since the correct
     * number of bytes cannot be returned as an int. For large streams
     * use the `copyLarge(InputStream, OutputStream)` method.
     *
     * @param input the `InputStream` to read from
     * @param output the `OutputStream` to write to
     * @return the number of bytes copied, or -1 if &gt; Integer.MAX_VALUE
     * @throws NullPointerException if the input or output is null
     * @throws IOException          if an I/O error occurs
     * @since 1.1
     */
    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream): Int {
        val count = copyLarge(input, output)
        return if (count > Int.MAX_VALUE) {
            -1
        } else count.toInt()
    }

    /**
     * Copies bytes from a large (over 2GB) `InputStream` to an
     * `OutputStream`.
     *
     *
     * This method buffers the input internally, so there is no need to use a
     * `BufferedInputStream`.
     *
     *
     * The buffer size is given by [.DEFAULT_BUFFER_SIZE].
     *
     * @param input the `InputStream` to read from
     * @param output the `OutputStream` to write to
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException          if an I/O error occurs
     * @since 1.3
     */
    @Throws(IOException::class)
    fun copyLarge(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count: Long = 0
        var n: Int
        while (EOF != input.read(buffer).also { n = it }) {
            output.write(buffer, 0, n)
            count += n.toLong()
        }
        return count
    }
}
