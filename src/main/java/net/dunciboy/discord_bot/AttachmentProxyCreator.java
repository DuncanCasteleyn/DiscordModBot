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
import net.dv8tion.jda.core.entities.impl.JDAImpl;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.requests.Requester;
import net.dv8tion.jda.core.utils.SimpleLog;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.collections4.map.LinkedMap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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

    /**
     * @deprecated Replaced with proxyMessageAttachment
     */
    @Deprecated
    void storeMessageAttachments(GuildMessageReceivedEvent event) {
        proxyMessageAttachments(event);
    }

    void proxyMessageAttachments(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        event.getMessage().getAttachments().forEach(attachment -> {
            if (attachment.getSize() < 8 << 20) {  //8MB
                InputStream in = null;
                try {
                    Request request = new Request.Builder().addHeader("user-agent", Requester.USER_AGENT).url(attachment.getUrl()).build();
                    Response response = ((JDAImpl) event.getJDA()).getRequester().getHttpClient().newCall(request).execute();
                    //noinspection ConstantConditions
                    in = response.body().byteStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();


                    int nRead;
                    byte[] data = new byte[8192];

                    while ((nRead = in.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }

                    buffer.flush();

                    event.getJDA().getTextChannelById(CACHE_CHANNEL).sendFile(buffer.toByteArray(), attachment.getFileName(), new MessageBuilder().append(event.getMessage().getId()).build()).queue(message -> addToCache(event.getMessage().getIdLong(), message));
                } catch (Exception e) {
                    LOG.log(e);
                    addToCache(event.getMessage().getIdLong(), null);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Exception ignored) {
                        }
                    }
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
