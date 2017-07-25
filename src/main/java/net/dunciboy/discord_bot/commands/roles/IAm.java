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
                if (event.getChannel().getId().equals("221686172793962496") && roleCommands.getIAmRoles() != null) {
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
