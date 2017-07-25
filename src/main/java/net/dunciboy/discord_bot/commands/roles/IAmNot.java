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
 * I am not command to allow users to remove roles from them self.
 * <p>
 * Created by Duncan on 23/02/2017.
 */
class IAmNot extends CommandModule {
    private static final String[] ALIASES = new String[]{"IAmNot"};
    private static final String DESCRIPTION = "Can be used to remove a role from yourself.";
    private final RoleCommands roleCommands;

    IAmNot(RoleCommands roleCommands) {
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
            if (event.getChannel().getId().equals("221686172793962496") && arguments != null && (event.getGuild().getRoles().stream().anyMatch(role -> roleCommands.getIAmRoles()[0].isListed(role) && role.getName().toLowerCase().contains(arguments.toLowerCase())) || event.getGuild().getRoles().stream().anyMatch(role -> roleCommands.getIAmRoles()[1].isListed(role) && role.getName().toLowerCase().contains(arguments.toLowerCase()))) /*|| Permissions.hasPermission(event.getMember(), 0)*/) {
                removeFromRole(event, arguments);
            } else {
                event.getChannel().sendMessage(event.getAuthor().getAsMention() + " The role you have requested either does not exist, or you do not have permission to remove yourself from that role.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
            }
        } else {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " This command only works in a text channel in a guild.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        }
    }

    /**
     * removes a member from a role.
     *
     * @param event    the event where this was called from.
     * @param roleName The role name we are trying to search.
     */
    private void removeFromRole(MessageReceivedEvent event, String roleName) {
        List<Role> roleList = event.getGuild().getRoles().stream().filter(role -> role.getName().toLowerCase().contains(roleName.toLowerCase())).collect(Collectors.toList());
        if (roleList.isEmpty()) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " Invalid role name.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
            return;
        }
        if (roleList.size() > 1) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " The role name you provided is to broad and matches multiple roles.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        } else if (!event.getMember().getRoles().containsAll(roleList)) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " You don't have this role.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
        } else {
            event.getGuild().getController().removeRolesFromMember(event.getMember(), roleList).reason("Requested by user with !IAmNot command").queue();
            StringBuilder removedRoles = new StringBuilder("The following role has been removed from " + event.getAuthor().getAsMention() + ":\n");
            roleList.forEach(role -> removedRoles.append(role.getName()).append("\n"));
            event.getChannel().sendMessage(removedRoles.toString()).queue();
        }
    }
}
