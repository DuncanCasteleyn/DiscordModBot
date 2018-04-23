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

import be.duncanc.discordmodbot.bot.services.MemberGate
import org.junit.Test

/**
 * Created by Duncan on 30/06/2017.
 */
class MemberGateTest : MemberGate(175856762677624832L, 319590906523156481L, 175856762677624832L, 319623491970400268L, 218085411686318080L, arrayOf(MemberGate.WelcomeMessage("https://cafekuyer.files.wordpress.com/2016/04/subaru-ftw.gif", "Welcome [user] to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("https://static.tumblr.com/0549f3836351174ea8bba0306ebd2641/cqk1twd/DJcofpaji/tumblr_static_tumblr_static_j2yt5g46evscw4o0scs0co0k_640.gif", "Welcome [user] to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("http://pa1.narvii.com/6165/5e28d55b439501172d35f74bc8fe2ac1665af8cf_hq.gif", "Welcome [user] to the /r/Re_Zero discord server I suppose!"), MemberGate.WelcomeMessage("https://i.imgur.com/6HhzYIL.gif", "Welcome [user] to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("https://68.media.tumblr.com/e100d53dc43d09f624a9bcb930ad6c8c/tumblr_ofs0sbPbS31uqt6z2o1_500.gif", "Welcome [user] to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("https://i.imgbox.com/674W2nGm.gif", "Welcome [user] to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("https://i.imgur.com/1zXLZ6E.gif", "Welcome [user] to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("https://68.media.tumblr.com/14273c92b69c96d4c71104ed5420b2c8/tumblr_o92199bqfv1qehrvso2_500.gif", "Hiya [user]-kyun! Welcome to the /r/Re_Zero discord server nya!"))) {

    @Test
    fun saveQuestionsTest() {
        super.saveQuestions()
    }

    @Test
    fun addQuestionTest() {
        val messageContent = "Testing?\nYes,we,might\nyolo"
        val inputQuestionList: List<String> = messageContent.split('\n')
        if (inputQuestionList.size < 2) {
            throw IllegalArgumentException("Syntax mismatch!")
        }
        val question = Question(inputQuestionList[0])
        for (i in 1 until inputQuestionList.size) {
            question.addKeywords(ArrayList(inputQuestionList[i].split(',')))
        }
        questions.add(question)
        super.saveQuestions()
    }
}