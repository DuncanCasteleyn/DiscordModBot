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

package net.dunciboy.discord_bot;

import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class contains static methods that can be used to improve the usage of JDA methods
 */
public final class JDALibHelper {
    private static final Pattern MESSAGE_SPLITTER_REGEX = Pattern.compile(".{1,2000}(?:\\n|$)", Pattern.DOTALL);
    private static final Pattern MESSAGE_SPLITTER_REGEX_CODE_BLOCK = Pattern.compile(".{1,1994}(?:\\n|$)", Pattern.DOTALL);

    JDALibHelper() {
        throw new AssertionError("Instantiating this class is not allowed");
    }

    /**
     * Will queue a message to be send.
     *
     * @param messageChannel target channel.
     * @param message        the message to send.
     * @deprecated Use MessageBuilder from JDA instead with buildAll() split policy.
     */
    @Deprecated
    public static void queueSendMessage(MessageChannel messageChannel, String message) {
        if (message.length() > 2000) {
            List<String> splitter = new ArrayList<>();
            Matcher regexMatcher = MESSAGE_SPLITTER_REGEX.matcher(message);
            while (regexMatcher.find()) {
                splitter.add(regexMatcher.group());
            }
            for (String splitMessage : splitter) {
                messageChannel.sendMessage(splitMessage).queue();
            }
        } else {
            messageChannel.sendMessage(message).queue();
        }
    }

    /**
     * Will queue a message with code to be send.
     *
     * @param messageChannel target channel.
     * @param message        the message to send.
     * @deprecated Use MessageBuilder from JDA instead with buildAll() split policy.
     */
    @Deprecated
    public static void queueSendCodeBlock(MessageChannel messageChannel, String message) {
        if (message.length() > 1994) {
            List<String> splitter = new ArrayList<>();
            Matcher regexMatcher = MESSAGE_SPLITTER_REGEX_CODE_BLOCK.matcher(message);
            while (regexMatcher.find()) {
                splitter.add(regexMatcher.group());
            }
            for (String splitMessage : splitter) {
                messageChannel.sendMessage("```" + splitMessage + "```").queue();
            }
        } else {
            messageChannel.sendMessage("```" + message + "```").queue();
        }
    }

    /**
     * Create a string that contains both the username and nickname of a member.
     *
     * @param member the member object to creat the string from.
     * @return A string containing both the nickname and username in form of nickname(username).
     */
    public static String getEffectiveNameAndUsername(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("Member may not be null");
        }
        if (member.getNickname() != null) {
            return member.getNickname() + "(" + member.getUser().getName() + ")";
        } else {
            return member.getUser().getName();
        }
    }

    /**
     * Deletes multiple messages at once, unlike the default method this one will split the ArrayList messages in stacks of 100 messages each automatically
     *
     * @param channel  channel to deletes the messages from.
     * @param messages Messages to deleted. The list you give will be emptied for you.
     */
    public static void limitLessBulkDelete(TextChannel channel, ArrayList<Message> messages) {
        {
            ArrayList<Message> tempList = new ArrayList<>(messages);
            messages.clear();
            messages = tempList;
        }
        if (messages.size() >= 2 && messages.size() <= 100) {
            channel.deleteMessages(messages).queue();
        } else if (messages.size() < 2) {
            for (Message message : messages) {
                message.delete().queue();
            }
        } else {
            ArrayList<Message> messagesStack = new ArrayList<>();
            while (messages.size() > 0) {
                messagesStack.add(messages.remove(0));
                if (messagesStack.size() == 100) {
                    channel.deleteMessages(messagesStack).queue();
                    messagesStack = new ArrayList<>();
                }
            }
            if (messagesStack.size() >= 2) {
                channel.deleteMessages(messagesStack).queue();
            } else {
                for (Message message : messagesStack) {
                    message.delete().queue();
                }
            }
        }
    }

    @Deprecated
    public static void sendPrivateMessage(User user, Message message) {
        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(message));
    }

    @Deprecated
    public static void sendPrivateMessage(User user, String message) {
        sendPrivateMessage(user, new MessageBuilder().append(message).build());
    }
}
