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
 */

package be.duncanc.discordmodbot;

import be.duncanc.discordmodbot.commands.CommandModule;
import be.duncanc.discordmodbot.sequence.Sequence;
import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONKey;
import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONToJavaObject;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IAmRoles extends CommandModule {
    private static final String[] ALIASES = new String[]{"IAmRoles"};
    private static final String DESCRIPTION = "Controller for IAmRoles.";
    private static final String[] ALIASES_I_AM_NOT = new String[]{"IAmNot"};
    private static final String DESCRIPTION_I_AM_NOT = "Can be used to remove a role from yourself.";
    private static final String[] ALIASES_I_AM = new String[]{"iam"};
    private static final String DESCRIPTION_I_AM = "Can be used to self assign a role.";

    public IAmRoles() {
        super(ALIASES, null, DESCRIPTION);
        iAmRoles = new HashMap<>();
    }

    public IAmRoles(HashMap<String, JSONArray> iAmRoles) {
        super(ALIASES, null, DESCRIPTION);
        throw new UnsupportedOperationException("Not yet implemented");
        /*this.iAmRoles = new HashMap<>();
        iAmRoles.forEach((s, ) -> {
            //this.iAmRoles.put(Long.parseLong(s), );
            //todo
        });*/
    }

    private HashMap<Long, ArrayList<IAmRolesCategory>> iAmRoles;

    @Override
    public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
        event.getJDA().addEventListener(new IAmRolesSequence(event.getAuthor(), event.getChannel()));
    }

    public class IAmRolesSequence extends Sequence {

        private byte sequenceNumber;
        private String newCategoryName = null;
        private ArrayList<IAmRolesCategory> iAmRolesCategories = null;

        IAmRolesSequence(@NotNull User user, @NotNull MessageChannel channel) {
            super(user, channel);
            sequenceNumber = 0;
        }

        @Override
        public void onMessageReceivedDuringSequence(@NotNull MessageReceivedEvent event) {
            switch (sequenceNumber) {
                case 0:
                    super.getChannel().sendMessage("Please select which action you want to perform:\n" +
                            "0. Add a new category\n" +
                            "1. Remove an existing category\n" +
                            "2. Modify an existing category").queue(message -> super.addMessageToCleaner(message));
                    sequenceNumber = 1;
                    break;
                case 1:
                    switch (Byte.parseByte(event.getMessage().getRawContent())) {
                        case 0:
                            super.getChannel().sendMessage("Please enter a unique category name and if you can only have one role of this category (true if the a user can only have on role out of this category). Syntax: \"Role name | true or false\"").queue(message -> super.addMessageToCleaner(message));
                            sequenceNumber = 2;
                            break;
                        case 1:
                            MessageBuilder deleteCategoryMessage = new MessageBuilder().append("Please select which role category you'd like to delete.");
                            iAmRolesCategories = iAmRoles.get(event.getGuild().getIdLong());
                            for (int i = 0; i < iAmRolesCategories.size(); i++) {
                                deleteCategoryMessage.append('\n').append(i).append(". ").append(iAmRolesCategories.get(i).categoryName);
                            }
                            deleteCategoryMessage.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach(message -> super.getChannel().sendMessage(message).queue(message1 -> super.addMessageToCleaner(message1)));
                            sequenceNumber = 3;
                            break;
                        case 2:
                            //todo logic to add roles to existing categories or modify if you can only select one role
                            sequenceNumber = 4;
                            break;
                        default:
                            getChannel().sendMessage("Wrong answer please answer with a valid number").queue(message -> super.addMessageToCleaner(message));
                            break;
                    }
                    break;
                case 2:
                    if (newCategoryName == null) {
                        ArrayList<String> existingCategoryNames = new ArrayList<>();
                        iAmRoles.get(event.getGuild().getIdLong()).forEach(iAmRolesCategory -> existingCategoryNames.add(iAmRolesCategory.categoryName));
                        if (existingCategoryNames.contains(event.getMessage().getRawContent())) {
                            throw new IllegalArgumentException("The name you provided is already being used.");
                        }
                        newCategoryName = event.getMessage().getRawContent();
                        super.getChannel().sendMessage("Please enter if a user can only have one role of this category. true or false?").queue(message -> super.addMessageToCleaner(message));
                    } else {
                        iAmRoles.get(event.getGuild().getIdLong()).add(new IAmRolesCategory(newCategoryName, Boolean.parseBoolean(event.getMessage().getRawContent())));
                        super.getChannel().sendMessage("Successfully added new category.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
                        super.destroy();
                    }
                    break;
                case 3:
                    iAmRolesCategories.remove(Integer.parseInt(event.getMessage().getRawContent()));
                    super.getChannel().sendMessage("Successfully removed the category").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
                    super.destroy();
                    break;
                case 4:
                    //todo
                    break;
            }
        }
    }

    class IAmRolesCategory {
        private String categoryName;
        private boolean canOnlyHaveOne;
        private ArrayList<Long> roles;

        public IAmRolesCategory(@NotNull String categoryName, boolean canOnlyHaveOne) {
            this.categoryName = categoryName;
            this.canOnlyHaveOne = canOnlyHaveOne;
            this.roles = new ArrayList<>();
        }

        public IAmRolesCategory(@JSONKey(jsonKey = "categoryName") String categoryName, @JSONKey(jsonKey = "canOnlyHaveOne") boolean canOnlyHaveOne, @JSONKey(jsonKey = "roles") JSONArray roles) {
            this.categoryName = categoryName;
            this.canOnlyHaveOne = canOnlyHaveOne;
            this.roles = new ArrayList<>(JSONToJavaObject.INSTANCE.toTypedList(roles, Long.class));
        }

        @JSONKey(jsonKey = "guildId")
        public String getCategoryName() {
            return categoryName;
        }

        @JSONKey(jsonKey = "roles")
        public List<Long> getRoles() {
            return Collections.unmodifiableList(roles);
        }

        @JSONKey(jsonKey = "canOnlyHaveOne")
        public boolean canOnlyHaveOne() {
            return canOnlyHaveOne;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            IAmRolesCategory that = (IAmRolesCategory) o;

            return categoryName.equals(that.categoryName);
        }

        @Override
        public int hashCode() {
            return categoryName.hashCode();
        }
    }

    /**
     * I am not command to allow users to remove roles from them self.
     * <p>
     * Created by Duncan on 23/02/2017.
     */
    class IAmNot extends CommandModule {

        IAmNot() {
            super(ALIASES_I_AM_NOT, null, DESCRIPTION_I_AM_NOT);
        }

        @Override
        public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
            //todo rewrite
        }
    }

    /**
     * Iam command to assign yourself roles.
     * <p>
     * Created by Duncan on 23/02/2017.
     */
    class IAm extends CommandModule {

        IAm() {
            super(ALIASES_I_AM, null, DESCRIPTION_I_AM);
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
            //todo rewrite
        }
    }
}
