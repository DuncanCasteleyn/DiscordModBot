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

package be.duncanc.discordmodbot.commands;

import be.duncanc.discordmodbot.services.GuildLogger;
import be.duncanc.discordmodbot.services.LogToChannel;
import be.duncanc.discordmodbot.utils.JDALibHelper;
import be.duncanc.discordmodbot.utils.ThrowableSafeRunnable;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.PrivateChannel;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;

/**
 * Slow mode command for channels.
 * <p>
 * Created by Duncan on 19/02/2017.
 */
public class SlowMode extends CommandModule {
    private static final String[] ALIASES = new String[]{"SlowMode"};
    private static final String ARGUMENTATION_SYNTAX = "[Threshold message limit] [Threshold reset time] [Mute time when threshold hit]";
    private static final String DESCRIPTION = "This command will prevent spamming in channels by temporary revoking permissions on users that spam in a channel.";

    private ArrayList<SlowModeOnChannel> slowedChannels;

    public SlowMode() {
        super(ALIASES, ARGUMENTATION_SYNTAX, DESCRIPTION, true, true);
        slowedChannels = new ArrayList<>();
    }

    @Override
    public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
        String[] args;
        if (arguments != null) {
            args = arguments.split(" ");
        } else {
            args = null;
        }
        if (!event.isFromType(ChannelType.TEXT)) {
            event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("This command only works in a guild.").queue());
        } else if (!event.getMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_MANAGE)) {
            final String noPermissionMessage = event.getAuthor().getAsMention() + " You need manage messages in this channel to toggle SlowMode!";
            event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(noPermissionMessage).queue());
        } else {
            if (event.getGuild().getMember(event.getJDA().getSelfUser()).getPermissions().contains(Permission.MANAGE_PERMISSIONS)) {
                LogToChannel logToChannel = (LogToChannel) event.getJDA().getRegisteredListeners().stream().filter(o -> o instanceof LogToChannel).findFirst().orElse(null);
                boolean wasSlowed = false;
                for (SlowModeOnChannel slowChannel : slowedChannels) {
                    if (slowChannel.getSlowChannel() == event.getChannel()) {
                        slowChannel.disable();
                        slowedChannels.remove(slowChannel);
                        if (logToChannel != null) {
                            EmbedBuilder logEmbed = new EmbedBuilder()
                                    .setColor(Color.GREEN)
                                    .setTitle("Slow mode disabled", null)
                                    .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                                    .addField("Channel", event.getTextChannel().getName(), true);

                            logToChannel.log(logEmbed, event.getAuthor(), event.getGuild(), null, GuildLogger.LogTypeAction.MODERATOR);

                            //logger.log("Slow mode on #" + event.getTextChannel().getName() + " disabled", "toggled by " + JDALibHelper.getEffectiveNameAndUsername(event.getMember()), event.getGuild(), event.getAuthor().getId(), event.getAuthor().getEffectiveAvatarUrl());
                        }
                        wasSlowed = true;
                        break;
                    }
                }
                if (!wasSlowed) {
                    if (args != null && args.length >= 3) {
                        try {
                            slowedChannels.add(new SlowModeOnChannel(event.getTextChannel(), Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2])));
                            if (logToChannel != null) {
                                EmbedBuilder logEmbed = new EmbedBuilder()
                                        .setColor(Color.YELLOW)
                                        .setTitle("Slow mode enabled", null)
                                        .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                                        .addField("Channel", event.getTextChannel().getName(), true)
                                        .addBlankField(false)
                                        .addField("Threshold", args[0], true)
                                        .addField("Threshold time", args[1], true)
                                        .addField("Mute time", args[2], true);

                                logToChannel.log(logEmbed, event.getAuthor(), event.getGuild(), null, GuildLogger.LogTypeAction.MODERATOR);
                            }
                        } catch (NumberFormatException e) {
                            PrivateChannel privateChannel;
                            privateChannel = event.getAuthor().openPrivateChannel().complete();
                            privateChannel.sendMessage("The provided argument for the command !slowmode was incorrect. Provide integer numbers.").queue();
                        }
                    } else {
                        final int threshold = 3;
                        final int thresholdTime = 5;
                        final int muteTime = 5;
                        slowedChannels.add(new SlowModeOnChannel(event.getTextChannel(), threshold, thresholdTime, muteTime));
                        if (logToChannel != null) {
                            EmbedBuilder logEmbed = new EmbedBuilder()
                                    .setColor(Color.YELLOW)
                                    .setTitle("Slow mode enabled", null)
                                    .addField("Moderator", JDALibHelper.INSTANCE.getEffectiveNameAndUsername(event.getMember()), true)
                                    .addField("Channel", event.getTextChannel().getName(), true)
                                    .addBlankField(false)
                                    .addField("Threshold", String.valueOf(threshold), true)
                                    .addField("Threshold time", String.valueOf(thresholdTime), true)
                                    .addField("Mute time", String.valueOf(muteTime), true);

                            logToChannel.log(logEmbed, event.getAuthor(), event.getGuild(), null, GuildLogger.LogTypeAction.MODERATOR);
                        }
                    }
                }
            } else {
                PrivateChannel privateChannel;
                privateChannel = event.getAuthor().openPrivateChannel().complete();
                privateChannel.sendMessage("Cannot perform slow mode due to a lack of Permission. Missing permission: " + Permission.MANAGE_PERMISSIONS).queue();
            }
        }
    }

    /**
     * Created by Dunciboy on 23/10/2016.
     * <p>
     * This class executed activate slow mode on a channel that will prevent people from posting message rapidly after each other to prevent spam and abuse.
     */

    private class SlowModeOnChannel extends ListenerAdapter {
        private final ThreadGroup removeThreads;
        private final ScheduledExecutorService removeThreadsPool;

        private TextChannel slowChannel;
        private HashMap<Long, SlowModeOnChannel.MemberSlowModeCleanerAndDataHolder> memberSlowModeCleanerMap;
        private int thresholdResetTime;
        private int threshold;
        private int muteTime;

        /**
         * Will create an object that is going to slow the channel messages.
         *
         * @param textChannel The channel that needs to be slowed
         * @param muteTime    The amount of time in seconds members need to wait before sending a new message.
         */
        SlowModeOnChannel(TextChannel textChannel, int threshold, int thresholdResetTime, int muteTime) {
            this.slowChannel = textChannel;
            this.memberSlowModeCleanerMap = new HashMap<>();
            this.threshold = threshold;
            this.thresholdResetTime = thresholdResetTime;
            this.muteTime = muteTime;
            this.removeThreads = new ThreadGroup("Slow mode user remove threads");
            this.removeThreads.setDaemon(false);
            this.removeThreadsPool = Executors.newScheduledThreadPool(5, r -> {
                Thread thread = new Thread(removeThreads, new ThrowableSafeRunnable(r, Companion.getLOG()), this.toString());
                thread.setDaemon(true);
                return thread;
            });

            slowChannel.getJDA().addEventListener(this);
        }

        @Override
        public String toString() {
            return "SlowModeOnChannel{" +
                    "slowChannel=" + slowChannel +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SlowModeOnChannel that = (SlowModeOnChannel) o;
            return slowChannel.equals(that.slowChannel);
        }

        @Override
        public int hashCode() {
            return slowChannel.hashCode();
        }

        /**
         * This method will the return the channel that is being slowed by the object.
         *
         * @return the channel object that is being slowed by this object.
         */
        TextChannel getSlowChannel() {
            return slowChannel;
        }

        /**
         * This function must be called before removing the object storage or it will permanently filter the slowed channel in this object!
         */
        void disable() {
            slowChannel.getJDA().removeEventListener(this);
            removeThreadsPool.shutdown();
            boolean terminated;
            try {
                terminated = removeThreadsPool.awaitTermination(6, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                terminated = false;
            }
            if (!terminated) {
                removeThreadsPool.shutdownNow();
            }
            synchronized (removeThreads) {
                while (removeThreads.activeCount() > 0) {
                    try {
                        removeThreads.interrupt();
                        TimeUnit.SECONDS.timedWait(removeThreads, 5);
                    } catch (InterruptedException ignored) {
                    }
                }
                removeThreads.destroy();
            }
        }

        /**
         * This method is used to listen on messages that are send to the channel that needs to be filtered from sending messages rapidly after each other
         *
         * @param event The event that triggered this method call
         */
        @Override
        public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
            if (event.getMember().hasPermission(Permission.MESSAGE_MANAGE) || event.getAuthor().isBot() || event.getAuthor().getIdLong() == 172478011923234816L) {
                return;
            }

            if (event.getChannel() == slowChannel) {
                if (!memberSlowModeCleanerMap.containsKey(event.getAuthor().getIdLong())) {
                    SlowModeOnChannel.MemberSlowModeCleanerAndDataHolder memberSlowModeCleanerAndDataHolder;
                    if (slowChannel.getPermissionOverride(event.getMember()) != null) {
                        memberSlowModeCleanerAndDataHolder = new SlowModeOnChannel.MemberSlowModeCleanerAndDataHolder(event.getMember(), false, event.getChannel().getPermissionOverride(event.getMember()).getAllowed().contains(Permission.MESSAGE_WRITE));
                    } else {
                        memberSlowModeCleanerAndDataHolder = new SlowModeOnChannel.MemberSlowModeCleanerAndDataHolder(event.getMember(), true);
                    }
                    memberSlowModeCleanerMap.put(event.getAuthor().getIdLong(), memberSlowModeCleanerAndDataHolder);
                } else {
                    memberSlowModeCleanerMap.get(event.getAuthor().getIdLong()).newMessage();
                }
            }
        }

        /**
         * This inner class provides the functionality to remove a person that was added to the slowed list so that he will not permanently remain slowed after posting a message.
         */
        private class MemberSlowModeCleanerAndDataHolder implements Runnable {
            private final ScheduledFuture scheduledCleaner;
            private final Member memberToClean;
            private boolean deletePermissionOverride;
            private boolean grantPerm;
            private boolean wasMuted;
            private byte messagesAmount;


            /**
             * Constructor
             *
             * @param member                   member to monitor
             * @param deletePermissionOverride If the user his permission override should be deleted or not.
             * @param grantPerm                If the user should get override write permissions assigned after his Mute.
             */

            MemberSlowModeCleanerAndDataHolder(Member member, boolean deletePermissionOverride, boolean grantPerm) {
                this.memberToClean = member;
                this.deletePermissionOverride = deletePermissionOverride;
                this.grantPerm = grantPerm;
                this.wasMuted = false;
                this.messagesAmount = 0;
                this.scheduledCleaner = removeThreadsPool.schedule(this, thresholdResetTime, TimeUnit.SECONDS);

                newMessage();
            }

            /**
             * Constructor
             *
             * @param member                   The member to monitor and store for
             * @param deletePermissionOverride If the user his permission override should be deleted or not.
             */
            MemberSlowModeCleanerAndDataHolder(Member member, boolean deletePermissionOverride) {
                this(member, deletePermissionOverride, false);
            }

            /**
             * Called when a new message is send to the channel with slow mode
             */
            void newMessage() {
                if (wasMuted) {
                    return;
                }

                messagesAmount++;
                if (messagesAmount >= threshold) {
                    mute();
                }
            }

            /**
             * Mutes the person and makes sure the Mute is cleaned up properly
             */
            private synchronized void mute() {
                if (!scheduledCleaner.isDone()) {
                    wasMuted = true;
                    boolean canceled = scheduledCleaner.cancel(false);
                    if (slowChannel.getPermissionOverride(memberToClean) != null) {
                        slowChannel.getPermissionOverride(memberToClean).getManager().deny(Permission.MESSAGE_WRITE).reason("SlowMode: mute").queue();
                    } else {
                        slowChannel.createPermissionOverride(memberToClean).queue(permissionOverride -> permissionOverride.getManager().deny(Permission.MESSAGE_WRITE).reason("SlowMode: mute").queue());
                    }
                    if (canceled) {
                        try {
                            removeThreadsPool.execute(this);
                        } catch (RejectedExecutionException e) {
                            muteTime = 0;
                            this.run();
                        }
                    }
                }
            }

            /**
             * Will handle the removing of mutes and removing from the map
             *
             * @see Thread#run()
             */
            @Override
            public synchronized void run() {
                try {
                    //TimeUnit.SECONDS.timedWait(this, thresholdResetTime); No longer needed thanks to scheduler service
                    if (wasMuted) {
                        TimeUnit.SECONDS.sleep(muteTime);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    try {
                        memberSlowModeCleanerMap.remove(memberToClean.getUser().getIdLong());
                        if (wasMuted) {
                            if (deletePermissionOverride) {
                                slowChannel.getPermissionOverride(memberToClean).delete().reason("SlowMode: remove mute").queue();
                            } else if (grantPerm) {
                                slowChannel.getPermissionOverride(memberToClean).getManager().grant(Permission.MESSAGE_WRITE).reason("SlowMode: remove mute").queue();
                            } else {
                                slowChannel.getPermissionOverride(memberToClean).getManager().clear(Permission.MESSAGE_WRITE).reason("SlowMode: remove mute").queue();
                            }
                        }
                    } catch (Throwable t) {
                        Companion.getLOG().error("Something went wrong while cleaning users", t);
                    }
                }
            }
        }
    }
}
