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

package net.dunciboy.discord_bot.commands.roles;

import net.dunciboy.discord_bot.commands.CommandModule;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Iam command to assign yourself roles.
 * <p>
 * Created by Duncan on 23/02/2017.
 */
class IAm extends CommandModule {
    private static final String[] ALIASES = new String[]{"iam"};
    private static final String DESCRIPTION = "Command can be used to self assign a role.";

    private final RoleCommands roleCommands;

    IAm(RoleCommands roleCommands) {
        super(ALIASES, null, DESCRIPTION);
        this.roleCommands = roleCommands;
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
        if (event.isFromType(ChannelType.TEXT)) {
            if (arguments != null) {
                            /*if (Permissions.hasPermission(event.getMember(), 0)) {
                                if (addToRole(event, allArguments)) break;
                            } else */
                if (event.getChannel().getIdLong() == 263725615675342849L && roleCommands.getIAmRoles() != null) {
                    if (event.getGuild().getRoles().stream().anyMatch(role -> roleCommands.getIAmRoles()[0].isListed(role) && role.getName().toLowerCase().contains(arguments.toLowerCase()))) {
                        if (event.getMember().getRoles().stream().noneMatch(r -> roleCommands.getIAmRoles()[0].isListed(r))) {
                            addToRole(event, arguments);
                        } else {
                            List<Role> characterRolesToRemove = event.getMember().getRoles().stream().filter(role -> roleCommands.getIAmRoles()[0].isListed(role)).collect(Collectors.toList());
                            addToRole(event, arguments, characterRolesToRemove);
                        }
                    } else if (event.getGuild().getRoles().stream().anyMatch(role -> roleCommands.getIAmRoles()[1].isListed(role) && role.getName().toLowerCase().contains(arguments.toLowerCase()))) {
                        addToRole(event, arguments);
                    } else {
                        event.getChannel().sendMessage(event.getAuthor().getAsMention() + " No self assignable role found matching this name.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
                    }
                } else {
                    event.getChannel().sendMessage(event.getAuthor().getAsMention() + " You do not have permission to add yourself to a role in this channel.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
                }
            } else {
                event.getChannel().sendMessage(event.getAuthor().getAsMention() + " So what are you?").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
            }
        } else {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " This command only works in a text channel in a guild.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        }
    }

    /**
     * Adds a member to a role.
     *
     * @param event    the event where this was called from.
     * @param roleName The role name we are trying to search.
     */
    private void addToRole(MessageReceivedEvent event, String roleName) {
        addToRole(event, roleName, null);
    }

    /**
     * Adds a member to a role.
     *
     * @param event    the event where this was called from.
     * @param roleName The role name we are trying to search.
     */
    private void addToRole(MessageReceivedEvent event, String roleName, List<Role> rolesToRemove) {
        List<Role> newRolesToAdd = event.getGuild().getRoles().stream().filter(role -> (roleCommands.getIAmRoles()[0].isListed(role) || roleCommands.getIAmRoles()[1].isListed(role)) && role.getName().toLowerCase().contains(roleName.toLowerCase())).collect(Collectors.toList());
        if (newRolesToAdd.isEmpty()) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " Invalid role name.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
            return;
        }
        if (newRolesToAdd.size() > 1) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " The role name you provided is to broad and matches multiple roles.\n" + newRolesToAdd.toString()).queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        } else if (event.getMember().getRoles().containsAll(newRolesToAdd)) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " You already have the role " + newRolesToAdd.get(0).getName() + ".").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        } else {
            if (rolesToRemove == null) {
                event.getGuild().getController().addRolesToMember(event.getMember(), newRolesToAdd).reason("Requested by user with !IAm command").queue();
            } else {
                event.getGuild().getController().modifyMemberRoles(event.getMember(), newRolesToAdd, rolesToRemove).reason("Requested by user with !IAm command").queue(aVoid -> event.getChannel().sendMessage(event.getAuthor().getAsMention() + " You're already part of a Character Role, so it has been removed automatically.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES)));
            }
            StringBuilder addedRoles = new StringBuilder("The following role has been added to " + event.getAuthor().getAsMention() + ":\n");
            newRolesToAdd.forEach(role -> addedRoles.append(role.getName()).append("\n"));
            event.getChannel().sendMessage(addedRoles.toString()).queue();
        }
    }
}
