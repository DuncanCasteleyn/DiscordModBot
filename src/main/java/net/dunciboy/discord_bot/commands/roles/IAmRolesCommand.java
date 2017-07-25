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
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.PrivateChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Control command for IAmRolesCommand
 * <p>
 * Created by Duncan on 23/02/2017.
 */
class IAmRolesCommand extends CommandModule {
    private static final String[] ALIASES = new String[]{"IAmRoles"};
    private static final String DESCRIPTION = "Controller for IAmRoles.";

    private final RoleCommands roleCommands;

    IAmRolesCommand(RoleCommands roleCommands) {
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
    public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
        String[] args = arguments.split(" ");
        PrivateChannel privateChannel = event.getAuthor().openPrivateChannel().complete();
        if (roleCommands.getIAmRoles() != null && event.isFromType(ChannelType.TEXT) && args.length >= 3 && event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            byte iAmRoleSelector;
            switch (args[0]) {
                case "waifus":
                    iAmRoleSelector = 0;
                    break;
                case "others":
                    iAmRoleSelector = 1;
                    break;
                default:
                    iAmRoleSelector = -1;
            }
            if (iAmRoleSelector >= 0 && iAmRoleSelector < roleCommands.getIAmRoles().length) {
                switch (args[1]) {
                    case "add":
                        if (event.getGuild().getRolesByName(arguments.substring(args[0].length() + args[1].length() + 2), true).size() == 1) {
                            roleCommands.getIAmRoles()[iAmRoleSelector].roleAdd(event.getGuild().getRolesByName(arguments.substring(args[0].length() + args[1].length() + 2), true).get(0));
                            privateChannel.sendMessage("The role has been added.").queue();
                        } else if (event.getGuild().getRolesByName(arguments.substring(args[0].length() + args[1].length() + 2), true).size() > 1) {
                            privateChannel.sendMessage("The role name you provided has multiple matches").queue();
                        } else {
                            privateChannel.sendMessage("No role found by that name").queue();
                        }
                        break;
                    case "remove":
                        if (event.getGuild().getRolesByName(arguments.substring(args[0].length() + args[1].length() + 2), true).size() == 1) {
                            roleCommands.getIAmRoles()[iAmRoleSelector].roleDel(event.getGuild().getRolesByName(arguments.substring(args[0].length() + args[0].length() + 2), true).get(0).getId());
                            privateChannel.sendMessage("The role has been removed.").queue();
                        } else if (event.getGuild().getRolesByName(arguments.substring(args[0].length() + args[1].length() + 2), true).size() > 1) {
                            privateChannel.sendMessage("The role name you provided has multiple matches").queue();
                        } else {
                            privateChannel.sendMessage("No role found by that name").queue();
                        }
                        break;
                    /*case "help":
                        privateChannel.sendMessage(I_AM_ROLES_HELP).queue();
                        break;*/
                    default:
                        privateChannel.sendMessage("Unknown action").queue();
                        break;
                }
            } else {
                privateChannel.sendMessage("Unknown iam role category").queue();
            }
        } else if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            privateChannel.sendMessage("You need manage roles permission to use this command.").queue();
        } else {
            privateChannel.sendMessage("Invalid amount of arguments 3 are required at least.").queue();
        }
    }
}
