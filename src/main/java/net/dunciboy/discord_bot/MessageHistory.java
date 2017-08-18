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

package net.dunciboy.discord_bot;


import net.dunciboy.discord_bot.commands.QuitBot;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.commons.collections4.map.LinkedMap;

import java.util.ArrayList;

/**
 * This class provides a buffer that will store Message objects so that they can
 * be accessed after being deleted on discord.
 *
 * @author Dunciboy
 * @version 22 October 2016
 */
public class MessageHistory extends ListenerAdapter implements QuitBot.BeforeBotQuit {

    private static final int HISTORY_SIZE = 1000;
    private static final ArrayList<MessageHistory> messageHistoryInstances = new ArrayList<>();

    private final LinkedMap<Long, Message> messages;
    private final AttachmentProxyCreator attachmentProxyCreator;

    private boolean deleted;

    /**
     * Default constructor
     */
    private MessageHistory() {
        messages = new LinkedMap<>();
        attachmentProxyCreator = new AttachmentProxyCreator();
        deleted = false;
        messageHistoryInstances.add(this);
    }

    /**
     * Creates an instance of the messageHistory and register it as event listener.
     *
     * @param jDA the instance to register this listener instance for.
     */
    static void registerMessageHistory(JDA jDA) {
        MessageHistory messageHistory = new MessageHistory();
        jDA.addEventListener(messageHistory);
        jDA.getRegisteredListeners().stream().filter(o -> o instanceof QuitBot).forEach(o -> ((QuitBot) o).addCallBeforeQuit(messageHistory));
    }

    /**
     * For you convenience do not store any of the object instances from this list in object fields,
     * doing this will cause IllegalStateExceptions to be thrown when you try to access it when it has been deleted and will cause the instance to not be garbage collected.
     *
     * @return a list of existing MessageHistory objects.
     */
    public static ArrayList<MessageHistory> getInstanceList() {
        return new ArrayList<>(messageHistoryInstances);
    }

    /**
     * Used to add a message to the list
     *
     * @param event The event that triggered this method
     */
    @Override
    public synchronized void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (deleted) {
            event.getJDA().removeEventListener(this);
            return;
        }

        while (messages.size() > HISTORY_SIZE) {
            attachmentProxyCreator.informDeleteFromCache(messages.remove(messages.firstKey()).getIdLong());
        }
        Message message = event.getMessage();
        if (message.getContent().length() > 0 && message.getContent().charAt(0) == '!' || message.getAuthor().isBot()) {
            return;
        }
        messages.put(message.getIdLong(), message);
        if (message.getAttachments().size() > 0) {
            attachmentProxyCreator.storeMessageAttachments(event);
        }
    }

    /**
     * Used to remove a message from the list manually
     *
     * @param id Message id to remove
     */
    public synchronized void removeMessage(long id) {
        if (deleted) {
            throw new IllegalStateException("This instance has been marked as deleted and should not be used any longer");
        }

        messages.remove(id);
    }

    /**
     * This method is called to modify an existing object message in the list
     *
     * @param event The event that triggered this method.
     */
    @Override
    public synchronized void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
        if (deleted) {
            event.getJDA().removeEventListener(this);
            return;
        }

        Message message = event.getMessage();
        messages.replace(message.getIdLong(), message);
    }

    /**
     * This message will return an object message or null if not found.
     *
     * @param id     the message id that we want to receive
     * @param delete Should the message be deleted from the cache?
     * @return object Message, returns null if the id is not in the history
     */
    synchronized Message getMessage(long id, boolean delete) {
        if (delete) {
            return messages.remove(id);
        } else {
            return messages.get(id);
        }
    }

    /**
     * This message will return an object message or null if not found and remove the message from the cache.
     *
     * @param id the message id that we want to receive
     * @return object Message, returns null if the id is not in the history
     */
    synchronized Message getMessage(long id) {
        return getMessage(id, true);
    }

    String getAttachmentsString(long id) {
        return attachmentProxyCreator.getAttachmentUrl(id);
    }

    private void cleanAttachmentCache() {
        attachmentProxyCreator.cleanCache();
    }

    /**
     * Removes itself from it's static instance list so it can be garbage collected.
     */
    public void delete() {
        if (deleted) {
            throw new IllegalStateException("This instance has been marked as deleted and should not be used any longer");
        }

        deleted = true;
        messageHistoryInstances.remove(this);
    }

    JDA getInstance() throws EmptyCacheException {
        if (deleted) {
            throw new IllegalStateException("This instance has been marked as deleted and should not be used any longer");
        }

        if (messages.size() > 0) {
            return messages.get(messages.firstKey()).getJDA();
        } else {
            throw new EmptyCacheException("The cache has either not saved any messages yet, or is empty. Please wait util a new message is cached.");
        }
    }

    /**
     * Actions to perform before the quit command is finished with executing.
     */
    @Override
    public void onAboutToQuit() {
        cleanAttachmentCache();
        delete();
    }

    class EmptyCacheException extends Exception {
        /**
         * Constructs a new exception with the specified detail message.  The
         * cause is not initialized, and may subsequently be initialized by
         * a call to {@link #initCause}.
         *
         * @param message the detail message. The detail message is saved for
         *                later retrieval by the {@link #getMessage()} method.
         */
        EmptyCacheException(String message) {
            super(message);
        }
    }
}
