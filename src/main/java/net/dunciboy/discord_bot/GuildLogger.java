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


import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.audit.ActionType;
import net.dv8tion.jda.core.audit.AuditLogEntry;
import net.dv8tion.jda.core.audit.AuditLogOption;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.guild.GuildBanEvent;
import net.dv8tion.jda.core.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent;
import net.dv8tion.jda.core.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.events.user.UserNameUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.utils.SimpleLog;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This file will create the listeners and the appropriate action to be taken
 * upon each of the listener.
 * <p>
 * IMPORTANT READ BEFORE MODIFYING CODE:
 * The modifying the lastCheckedMessageDeleteEntries HashMap needs to happen using the guildLoggerExecutor because it's
 * thread are executed sequentially there is no need to lock the object, however if you try to do this without using the
 * service the chances of hitting a ConcurrentModificationException are 100%.
 *
 * @author Duncan
 * @since 1.0
 */
public class GuildLogger extends ListenerAdapter {

    //private static final String SEPARATOR = "\n------------------------------------------------------------";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-M-yyyy hh:mm a O");
    private static final SimpleLog LOG = SimpleLog.getLog(GuildLogger.class.getSimpleName());
    private static final Color LIGHT_BLUE = new Color(52, 152, 219);
    private static final int LOG_ENTRY_CHECK_LIMIT = 5;

    private final ScheduledExecutorService guildLoggerExecutor;
    private final LogToChannel logger;
    private final Settings settings;
    private final HashMap<Long, AuditLogEntry> lastCheckedMessageDeleteEntries;

    GuildLogger(LogToChannel logger, Settings settings) {
        this.guildLoggerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(new ThrowableSafeRunnable(r, LOG), GuildLogger.class.getSimpleName());
            thread.setDaemon(true);
            return thread;
        });
        this.logger = logger;
        this.settings = settings;
        this.lastCheckedMessageDeleteEntries = new HashMap<>();
    }

    public static Serializable getCaseNumberSerializable(long guildId) {
        long caseNumber;
        try {
            caseNumber = new CaseSystem(guildId).getNewCaseNumber();
        } catch (IOException e) {
            caseNumber = -1;
        }
        return caseNumber != -1 ? caseNumber : "IOException - failed retrieving number";
    }

    @Override
    public void onReady(ReadyEvent event) {
        logger.initChannelList(event.getJDA());
        logger.getLogChannels().forEach(textChannel -> {
            textChannel.getGuild().getAuditLogs().type(ActionType.MESSAGE_DELETE).limit(1).cache(false).queue(auditLogEntries -> {
                AuditLogEntry auditLogEntry = auditLogEntries.get(0);
                lastCheckedMessageDeleteEntries.put(auditLogEntry.getGuild().getIdLong(), auditLogEntry);
            });
        });
    }

    @Override
    public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
        if (!settings.isLogMessageUpdate()) {
            return;
        }

        Guild guild = event.getGuild();
        TextChannel channel = event.getChannel();
        if (settings.isExceptedFromLogging(channel.getIdLong())) {
            return;
        }

        MessageHistory history = null;
        for (MessageHistory messageHistory : MessageHistory.getInstanceList()) {
            try {
                if (event.getJDA() == messageHistory.getInstance()) {
                    history = messageHistory;
                }
            } catch (MessageHistory.EmptyCacheException ignored) {
            }
        }

        if (history == null) {
            return;
        }

        Message oldMessage = history.getMessage(Long.parseUnsignedLong(event.getMessageId()), false);
        if (oldMessage != null) {
            final String name;
            {
                String tempName;
                try {
                    tempName = JDALibHelper.INSTANCE.getEffectiveNameAndUsername(oldMessage.getGuild().getMember(oldMessage.getAuthor()));
                } catch (IllegalArgumentException e) {
                    tempName = oldMessage.getAuthor().getName();
                }
                name = tempName;
            }
            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setTitle("#" + channel.getName() + ": Message was modified!")
                    .setDescription("Old message was:\n" + oldMessage.getContent())
                    .setColor(LIGHT_BLUE)
                    .addField("Author", name, true);
            guildLoggerExecutor.execute(() -> logger.log(logEmbed, oldMessage.getAuthor(), guild, oldMessage.getEmbeds()));
        }
    }

    /**
     * This functions will be called each time a message is deleted on a discord
     * server.
     *
     * @param event The event that trigger this method
     */
    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        if (!settings.isLogMessageDelete()) {
            return;
        }
        Guild guild = event.getGuild();
        TextChannel channel = event.getChannel();
        if (settings.isExceptedFromLogging(channel.getIdLong())) {
            return;
        }

        MessageHistory history = null;
        for (MessageHistory messageHistory : MessageHistory.getInstanceList()) {
            try {
                if (event.getJDA() == messageHistory.getInstance()) {
                    history = messageHistory;
                }
            } catch (MessageHistory.EmptyCacheException ignored) {
            }
        }

        if (history == null) {
            guildLoggerExecutor.execute(new ThrowableSafeRunnable(() -> {
                AuditLogEntry logEntry = event.getGuild().getAuditLogs().type(ActionType.MESSAGE_DELETE).cache(false).limit(1).complete().get(0);
                lastCheckedMessageDeleteEntries.put(event.getGuild().getIdLong(), logEntry);
            }, LOG));
            return;
        }

        Message oldMessage = history.getMessage(Long.parseUnsignedLong(event.getMessageId()));
        if (oldMessage != null) {
            String attachmentString = history.getAttachmentsString(Long.parseUnsignedLong(event.getMessageId()));
            final String name;
            {
                String tempName;
                try {
                    tempName = JDALibHelper.INSTANCE.getEffectiveNameAndUsername(oldMessage.getGuild().getMember(oldMessage.getAuthor()));
                } catch (IllegalArgumentException e) {
                    tempName = oldMessage.getAuthor().getName();
                }
                name = tempName;
            }
            guildLoggerExecutor.schedule(() -> {
                        User moderator = null;
                        {
                            int i = 0;
                            AuditLogEntry firstCheckedAuditLogEntry = null;
                            for (AuditLogEntry logEntry : event.getGuild().getAuditLogs().type(ActionType.MESSAGE_DELETE).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                                if (i == 0) {
                                    firstCheckedAuditLogEntry = logEntry;
                                }
                                if (!lastCheckedMessageDeleteEntries.containsKey(event.getGuild().getIdLong())) {
                                    i = LOG_ENTRY_CHECK_LIMIT;
                                } else {
                                    AuditLogEntry cachedAuditLogEntry = lastCheckedMessageDeleteEntries.get(event.getGuild().getIdLong());
                                    if (logEntry.getIdLong() == cachedAuditLogEntry.getIdLong()) {
                                        if (logEntry.getTargetIdLong() == oldMessage.getAuthor().getIdLong() && !logEntry.getOption(AuditLogOption.COUNT).equals(cachedAuditLogEntry.getOption(AuditLogOption.COUNT))) {
                                            moderator = logEntry.getUser();
                                        }
                                        break;
                                    }
                                }
                                if (logEntry.getTargetIdLong() == oldMessage.getAuthor().getIdLong()) {
                                    moderator = logEntry.getUser();
                                    break;
                                }
                                i++;
                                if (i >= LOG_ENTRY_CHECK_LIMIT) {
                                    break;
                                }
                            }
                            if (firstCheckedAuditLogEntry != null) {
                                lastCheckedMessageDeleteEntries.put(event.getGuild().getIdLong(), firstCheckedAuditLogEntry);
                            }
                        }

                        EmbedBuilder logEmbed = new EmbedBuilder()
                                .setTitle("#" + channel.getName() + ": Message was deleted!")
                                .setDescription("Old message was:\n" + oldMessage.getContent());
                        if (attachmentString != null) {
                            logEmbed.addField("Attachment(s)", attachmentString, false);
                        }
                        logEmbed.addField("Author", name, true);
                        if (moderator != null) {
                            logEmbed.addField("Deleted by", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getGuild().getMember(moderator)), true)
                                    .setColor(Color.YELLOW);
                        } else {
                            logEmbed.setColor(LIGHT_BLUE);
                        }
                        logger.log(logEmbed, oldMessage.getAuthor(), guild, oldMessage.getEmbeds());
                    }
                    , 1, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        if (!settings.isLogMessageDelete()) {
            return;
        }
        TextChannel channel = event.getChannel();
        if (settings.isExceptedFromLogging(channel.getIdLong())) {
            return;
        }

        EmbedBuilder logEmbed = new EmbedBuilder()
                .setColor(LIGHT_BLUE)
                .setTitle("#" + event.getChannel().getName() + ": Bulk delete")
                .addField("Amount of deleted messages", String.valueOf(event.getMessageIds().size()), false);

        MessageHistory history;
        MessageHistory[] messageHistoryArray = MessageHistory.getInstanceList().stream().filter(messageHistory -> {
            try {
                return messageHistory.getInstance() == event.getJDA();
            } catch (MessageHistory.EmptyCacheException e) {
                return false;
            }
        }).toArray(MessageHistory[]::new);
        if (messageHistoryArray.length == 1) {
            history = messageHistoryArray[0];
        } else if (messageHistoryArray.length > 1) {
            logBulkDelete(event, logEmbed);
            throw new IllegalStateException("Multiple MessageHistory classes found for same JDA instance");
        } else {
            history = null;
        }

        if (history == null) {
            logBulkDelete(event, logEmbed);
            return;
        }

        Path bulkDeleteLog = null;
        final BufferedWriter logWriter;
        try {
            bulkDeleteLog = Files.createTempFile(event.getChannel().getName() + " " + OffsetDateTime.now().format(DATE_TIME_FORMATTER), ".log");
            logWriter = Files.newBufferedWriter(bulkDeleteLog, Charset.forName("UTF-8"), StandardOpenOption.WRITE);
        } catch (IOException e) {
            if (bulkDeleteLog != null) {
                ioCleanup(bulkDeleteLog.toFile(), e);
            } else {
                LOG.log(e);
            }
            logBulkDelete(event, logEmbed);
            return;
        }
        try {
            logWriter.append(event.getChannel().toString()).append("\n");
        } catch (IOException e) {
            LOG.log(e);
        }
        final Boolean[] messageLogged = {false};
        event.getMessageIds().forEach(id -> {
            Message message = history.getMessage(Long.parseUnsignedLong(id));
            if (message != null) {
                messageLogged[0] = true;
                try {
                    logWriter.append(message.getAuthor().toString()).append(":\n").append(message.getContent()).append("\n");
                    String attachmentString = history.getAttachmentsString(Long.parseUnsignedLong(id));
                    if (attachmentString != null) {
                        logWriter.append("Attachment(s):\n").append(attachmentString).append("\n");
                    } else {
                        logWriter.append("\n");
                    }
                } catch (IOException e) {
                    LOG.log(e);
                }
            }
        });
        try {
            logWriter.close();
            if(messageLogged[0]) {
                logBulkDelete(event, logEmbed, bulkDeleteLog);
            } else {
                logBulkDelete(event, logEmbed);
                ioCleanup(bulkDeleteLog.toFile(), null);
            }
        } catch (IOException e) {
            ioCleanup(bulkDeleteLog.toFile(), e);
            logBulkDelete(event, logEmbed);
        }
    }

    private void ioCleanup(File file, IOException e) {
        if(e != null) {
            LOG.log(e);
        }
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private void logBulkDelete(MessageBulkDeleteEvent event, EmbedBuilder logEmbed, Path file) {
        guildLoggerExecutor.execute(() -> logger.log(logEmbed, null, event.getGuild(), null, file));
    }

    private void logBulkDelete(MessageBulkDeleteEvent event, EmbedBuilder logEmbed) {
        logBulkDelete(event, logEmbed, null);
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        if (!settings.isLogMemberRemove()) {
            return;
        }

        guildLoggerExecutor.schedule(() -> {
            User moderator = null;
            String reason = null;
            {
                int i = 0;
                for (AuditLogEntry logEntry : event.getGuild().getAuditLogs().type(ActionType.KICK).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.getTargetIdLong() == event.getMember().getUser().getIdLong()) {
                        moderator = logEntry.getUser();
                        reason = logEntry.getReason();
                        break;
                    }
                    i++;
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break;
                    }
                }
            }

            if (moderator != null && moderator == event.getJDA().getSelfUser()) {
                return; //Bot is kicking no need to log, if needed it will be placed in the module that is issuing the kick.
            }

            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setColor(Color.RED)
                    .addField("User", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true);
            if (moderator == null) {
                logEmbed.setTitle("SERVER NOTIFICATION: User left");
            } else {
                logEmbed.setTitle("SERVER NOTIFICATION: User kicked | Case: " + getCaseNumberSerializable(event.getGuild().getIdLong()));
                logEmbed.addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getGuild().getMember(moderator)), true);
                if (reason != null) {
                    logEmbed.addField("Reason", reason, false);
                }
            }
            logger.log(logEmbed, event.getMember().getUser(), event.getGuild(), null);
        }, 1, TimeUnit.SECONDS);

    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        if (!settings.isLogMemberBan()) {
            return;
        }

        guildLoggerExecutor.schedule(() -> {
            User moderator = null;
            String reason = null;
            {
                int i = 0;
                for (AuditLogEntry logEntry : event.getGuild().getAuditLogs().type(ActionType.BAN).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.getTargetIdLong() == event.getUser().getIdLong()) {
                        moderator = logEntry.getUser();
                        reason = logEntry.getReason();
                        break;
                    }
                    i++;
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break;
                    }
                }
            }

            if (moderator != null && moderator == event.getJDA().getSelfUser()) {
                return; //Bot is banning no need to log, if needed it will be placed in the module that is issuing the ban.
            }

            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("SERVER NOTIFICATION: User banned | Case: " + getCaseNumberSerializable(event.getGuild().getIdLong()))
                    .addField("User", event.getUser().getName(), true);
            if (moderator != null) {
                logEmbed.addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getGuild().getMember(moderator)), true);
                if (reason != null) {
                    logEmbed.addField("Reason", reason, false);
                }
            }
            logger.log(logEmbed, event.getUser(), event.getGuild(), null);
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (!settings.isLogMemberAdd()) {
            return;
        }

        EmbedBuilder logEmbed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("SERVER NOTIFICATION: User joined", null)
                .addField("User", event.getMember().getUser().getName(), false)
                .addField("Account created", event.getMember().getUser().getCreationTime().format(DATE_TIME_FORMATTER), false);
        guildLoggerExecutor.execute(() -> logger.log(logEmbed, event.getMember().getUser(), event.getGuild(), null));
    }


    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        if (!settings.isLogMemberRemoveBan()) {
            return;
        }

        guildLoggerExecutor.schedule(() -> {
            User moderator = null;
            {
                int i = 0;
                for (AuditLogEntry logEntry : event.getGuild().getAuditLogs().type(ActionType.UNBAN).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.getTargetIdLong() == event.getUser().getIdLong()) {
                        moderator = logEntry.getUser();
                        break;
                    }
                    i++;
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break;
                    }
                }
            }

            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("SERVER NOTIFICATION: User unbanned", null)
                    .addField("User", event.getUser().getName(), true);
            if (moderator != null) {
                logEmbed.addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getGuild().getMember(moderator)), true);
            }
            logger.log(logEmbed, event.getUser(), event.getGuild(), null);
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onUserNameUpdate(UserNameUpdateEvent event) {
        for (Guild guild : logger.userOnGuilds(event.getUser())) {
            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setColor(LIGHT_BLUE)
                    .setTitle("User has changed username")
                    .addField("Old username & discriminator", event.getOldName() + "#" + event.getOldDiscriminator(), false)
                    .addField("New username & discriminator", event.getUser().getName() + "#" + event.getUser().getDiscriminator(), false);
            guildLoggerExecutor.execute(() -> logger.log(logEmbed, event.getUser(), guild, null));
        }
    }

    @Override
    public void onGuildMemberNickChange(GuildMemberNickChangeEvent event) {
        guildLoggerExecutor.schedule(() -> {
            User moderator = null;
            {
                int i = 0;
                for (AuditLogEntry logEntry : event.getGuild().getAuditLogs().type(ActionType.MEMBER_UPDATE).cache(false).limit(LOG_ENTRY_CHECK_LIMIT)) {
                    if (logEntry.getTargetIdLong() == event.getMember().getUser().getIdLong()) {
                        moderator = logEntry.getUser();
                        break;
                    }
                    i++;
                    if (i >= LOG_ENTRY_CHECK_LIMIT) {
                        break;
                    }
                }
            }

            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setColor(LIGHT_BLUE)
                    .addField("User", event.getMember().getUser().getName(), false)
                    .addField("Old nickname", event.getPrevNick() != null ? event.getPrevNick() : "None", true)
                    .addField("New nickname", event.getNewNick() != null ? event.getNewNick() : "None", true);
            if (moderator == null || moderator == event.getMember().getUser()) {
                logEmbed.setTitle("User has changed nickname");
            } else {
                logEmbed.setTitle("Moderator has changed nickname")
                        .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getGuild().getMember(moderator)), false);
            }
            logger.log(logEmbed, event.getMember().getUser(), event.getGuild(), null);
        }, 1, TimeUnit.SECONDS);
    }
}
