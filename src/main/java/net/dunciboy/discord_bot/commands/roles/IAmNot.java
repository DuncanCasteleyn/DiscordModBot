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
            if (event.getChannel().getIdLong() == 263725615675342849L && arguments != null && (event.getGuild().getRoles().stream().anyMatch(role -> roleCommands.getIAmRoles()[0].isListed(role) && role.getName().toLowerCase().contains(arguments.toLowerCase())) || event.getGuild().getRoles().stream().anyMatch(role -> roleCommands.getIAmRoles()[1].isListed(role) && role.getName().toLowerCase().contains(arguments.toLowerCase()))) /*|| Permissions.hasPermission(event.getMember(), 0)*/) {
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
