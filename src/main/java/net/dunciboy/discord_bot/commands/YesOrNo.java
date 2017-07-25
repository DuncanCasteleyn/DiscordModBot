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

package net.dunciboy.discord_bot.commands;

import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Duncan on 25/04/2017.
 * <p>
 * this class is immutable and thread safe.
 *
 * @since 1.0.0
 * @deprecated Was never used because something better was created.
 */
@Deprecated
public class YesOrNo {

    private static final List<Long> list;
    private static final String[] YES_ALIASES = new String[]{"yes"};
    private static final String[] NO_ALIASES = new String[]{"no"};

    static {
        list = new ArrayList<>();
    }

    private final Yes yes;
    private final No no;
    private final long userId;
    private final QuestionAnswerActions questionAnswerActions;

    public YesOrNo(MessageReceivedEvent event, QuestionAnswerActions questionAnswerActions) throws AQuestionIsStillUnansweredException {
        long authorId = event.getAuthor().getIdLong();
        if (list.contains(authorId)) {
            throw new AQuestionIsStillUnansweredException();
        }

        list.add(authorId);
        this.userId = authorId;
        this.yes = new Yes();
        this.no = new No();
        this.questionAnswerActions = questionAnswerActions;
    }


    public interface QuestionAnswerActions {
        /**
         * Action to perform when a person answers yes to a question related to one of the previous command that was executed that activated a yes or no question.
         *
         * @param event The event that came when the question was answered.
         */
        void onYes(MessageReceivedEvent event);

        /**
         * Action to perform when a person answers no to a question related to one of the previous command that was executed that activated a yes or no question.
         *
         * @param event The event that came when the question was answered.
         */
        void onNo(MessageReceivedEvent event);
    }

    /**
     * Created by Duncan on 25/04/2017.
     */
    private class Yes extends CommandModule {
        /**
         * Constructor for class
         */
        Yes() {
            super(YES_ALIASES, null, null, false);
        }

        /**
         * Do something with the event, command and arguments.
         *
         * @param event     A MessageReceivedEvent that came with the command
         * @param command   The command alias that was used to trigger this commandExec
         * @param arguments The arguments that where entered after the command alias
         */
        @Override
        public void commandExec(MessageReceivedEvent event, String command, String arguments) {
            if (event.getAuthor().getIdLong() != userId) {
                return;
            }
            event.getJDA().removeEventListener(this, no);
            list.remove(event.getAuthor().getIdLong());
            if (event.isFromType(ChannelType.TEXT)) {
                event.getMessage().delete().queue();
            }
            questionAnswerActions.onYes(event);
        }
    }

    /**
     * Created by Duncan on 25/04/2017.
     */
    private class No extends CommandModule {
        /**
         * Constructor for class
         */
        No() {
            super(NO_ALIASES, null, null, false);
        }

        /**
         * Do something with the event, command and arguments.
         *
         * @param event     A MessageReceivedEvent that came with the command
         * @param command   The command alias that was used to trigger this commandExec
         * @param arguments The arguments that where entered after the command alias
         */
        @Override
        public void commandExec(MessageReceivedEvent event, String command, String arguments) {
            if (event.getAuthor().getIdLong() != userId) {
                return;
            }
            event.getJDA().removeEventListener(this, yes);
            list.remove(event.getAuthor().getIdLong());
            if (event.isFromType(ChannelType.TEXT)) {
                event.getMessage().delete().queue();
            }
            questionAnswerActions.onNo(event);
        }
    }

    private class AQuestionIsStillUnansweredException extends Exception {
        /**
         * Constructs a new exception with {@code null} as its detail message.
         * The cause is not initialized, and may subsequently be initialized by a
         * call to {@link #initCause}.
         */
        AQuestionIsStillUnansweredException() {
            super("There is a question still pending to be answered by \"!yes\" or \"!no\", you can't use any commands that require \"!yes\" or \"!no\" answers until you either answer it or wait for the previous question to expire.");
        }
    }
}
