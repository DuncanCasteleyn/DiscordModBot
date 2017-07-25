/*
 * Copyright 2017 Duncan C.
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

import net.dunciboy.discord_bot.CaseSystem
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.io.IOException

/**
 * Created by Duncan on 28/06/2017.
 */

internal class CaseSystemTest {
    @DisplayName("net.dunciboy.discord_bot.Test requesting numbers")
    @RepeatedTest(5)
    @Throws(IOException::class)
    fun requestCaseNumberTest() {
        println(CaseSystem(-99).newCaseNumber)
    }

    @DisplayName("net.dunciboy.discord_bot.Test if reset of count works")
    @RepeatedTest(2)
    fun resetTest() {
        CaseSystem(-99).reset()
    }
}