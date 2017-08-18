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

import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.utils.SimpleLog;
import org.apache.commons.collections4.map.LinkedMap;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Duncan on 14/01/2017.
 * <p>
 * This class duplicates embeds and links to images to keep them alive for logging.
 */
class AttachmentProxyCreator {
    private static final long CACHE_CHANNEL = 310006048595509248L;
    private static final int CACHE_SIZE = 500;
    private static final SimpleLog LOG = SimpleLog.getLog(AttachmentProxyCreator.class.getSimpleName());

    private final LinkedMap<Long, Message> attachmentCache;

    AttachmentProxyCreator() {
        attachmentCache = new LinkedMap<>();
    }

    synchronized String getAttachmentUrl(long id) {
        if (attachmentCache.containsKey(id)) {
            StringBuilder stringBuilder = new StringBuilder();
            Message message = attachmentCache.remove(id);
            if (message != null) {
                message.getAttachments().forEach(attachment -> stringBuilder.append("[").append(attachment.getFileName()).append("](").append(attachment.getUrl()).append(")\n"));
                Message subMessage = message;
                do {
                    if (attachmentCache.containsKey(subMessage.getIdLong())) {
                        subMessage = attachmentCache.remove(subMessage.getIdLong());
                        if (subMessage != null) {
                            subMessage.getAttachments().forEach(attachment -> stringBuilder.append("[").append(attachment.getFileName()).append("](").append(attachment.getUrl()).append(")\n"));
                        } else {
                            stringBuilder.append("The message either contained an attachment larger then 8MB and could not be uploaded again, or failed to create a proxy.");
                        }
                    } else {
                        subMessage = null;
                    }
                } while (subMessage != null);
            } else {
                stringBuilder.append("The message either contained an attachment larger then 8MB and could not be uploaded again, or failed to create a proxy.");
            }
            return stringBuilder.toString();
        } else {
            return null;
        }
    }

    synchronized void informDeleteFromCache(long id) {
        if (attachmentCache.containsKey(id)) {
            Message message = attachmentCache.remove(id);
            if (message != null) {
                message.delete().queue();
                informDeleteFromCache(message.getIdLong());
            }
        }
    }

    private synchronized void addToCache(long id, Message message) {
        while (attachmentCache.size() > CACHE_SIZE) {
            Message messageToClean = attachmentCache.remove(attachmentCache.firstKey());
            messageToClean.delete().queue();
            informDeleteFromCache(message.getIdLong());
        }
        if (attachmentCache.containsKey(id)) {
            addToCache(attachmentCache.get(id).getIdLong(), message);
        } else {
            attachmentCache.put(id, message);
        }
    }

    void storeMessageAttachments(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        event.getMessage().getAttachments().forEach(attachment -> {
            if (attachment.getSize() < 8 << 20) {  //8MB
                try {
                    File file = File.createTempFile(String.valueOf(System.nanoTime()), attachment.getFileName());

                    if (file.delete() && attachment.download(file)) {
                        try {
                            event.getJDA().getTextChannelById(CACHE_CHANNEL).sendFile(file, attachment.getFileName(), new MessageBuilder().append(event.getMessage().getId()).build()).queue(message -> {
                                addToCache(event.getMessage().getIdLong(), message);
                                if (file.exists() && !file.delete()) {
                                    file.deleteOnExit();
                                }
                            });
                        } catch (Exception e) {
                            LOG.log(e);
                            if (file.exists() && !file.delete()) {
                                file.deleteOnExit();
                            }
                        }
                    } else {
                        addToCache(event.getMessage().getIdLong(), null);
                        LOG.warn("Something went wrong with either the download or removing the temp file.");
                    }
                } catch (Exception e) {
                    LOG.log(e);
                    addToCache(event.getMessage().getIdLong(), null);
                }
            } else {
                LOG.warn("The file was larger than 8MB.");
                addToCache(event.getMessage().getIdLong(), null);
            }
        });
    }

    synchronized void cleanCache() {
        if (attachmentCache.size() > 0) {
            JDALibHelper.INSTANCE.limitLessBulkDelete(attachmentCache.get(attachmentCache.firstKey()).getJDA().getTextChannelById(CACHE_CHANNEL), new ArrayList<>(attachmentCache.values()));
        }
    }
}
