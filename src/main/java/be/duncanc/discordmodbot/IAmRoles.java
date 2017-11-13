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
import net.dv8tion.jda.core.entities.TextChannel;
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
    private final HashMap<Long, ArrayList<IAmRolesCategory>> iAmRoles;

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

    @Override
    public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
        event.getJDA().addEventListener(new IAmRolesSequence(event.getAuthor(), event.getChannel()));
    }

    public class IAmRolesSequence extends Sequence {

        private byte sequenceNumber = 0;
        private String newCategoryName = null;
        private IAmRolesCategory iAmRolesCategory = null;
        private final ArrayList<IAmRolesCategory> iAmRolesCategories;

        IAmRolesSequence(@NotNull User user, @NotNull MessageChannel channel) {
            super(user, channel);
            if (!(channel instanceof TextChannel)) {
                super.destroy();
                throw new UnsupportedOperationException("This command must be executed in a guild.");
            }
            synchronized (iAmRoles) {
                iAmRolesCategories = iAmRoles.computeIfAbsent(((TextChannel) channel).getGuild().getIdLong(), k -> new ArrayList<>());
            }
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
                            for (int i = 0; i < iAmRolesCategories.size(); i++) {
                                deleteCategoryMessage.append('\n').append(i).append(". ").append(iAmRolesCategories.get(i).categoryName);
                            }
                            deleteCategoryMessage.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach(message -> super.getChannel().sendMessage(message).queue(message1 -> super.addMessageToCleaner(message1)));
                            sequenceNumber = 3;
                            break;
                        case 2:
                            MessageBuilder modifyCategoryMessage = new MessageBuilder().append("Please select which role category you'd like to modify.");
                            for (int i = 0; i < iAmRolesCategories.size(); i++) {
                                modifyCategoryMessage.append('\n').append(i).append(". ").append(iAmRolesCategories.get(i).categoryName);
                            }
                            modifyCategoryMessage.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach(message -> super.getChannel().sendMessage(message).queue(message1 -> super.addMessageToCleaner(message1)));
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
                        synchronized (iAmRolesCategories) {
                            iAmRolesCategories.add(new IAmRolesCategory(newCategoryName, Boolean.parseBoolean(event.getMessage().getRawContent())));
                        }
                        super.getChannel().sendMessage("Successfully added new category.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
                        super.destroy();
                    }
                    break;
                case 3:
                    synchronized (iAmRolesCategories) {
                        iAmRolesCategories.remove(Integer.parseInt(event.getMessage().getRawContent()));
                    }
                    super.getChannel().sendMessage("Successfully removed the category").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
                    super.destroy();
                    break;
                case 4:
                    iAmRolesCategory = iAmRolesCategories.get(Integer.parseInt(event.getMessage().getRawContent()));
                    super.getChannel().sendMessage("Please enter the number of the action you'd like to perform.\n" +
                            "0. Modify the name. Current value: " + iAmRolesCategory.categoryName + "\n" +
                            "1. Invert if the users can only have one role. Current value" + iAmRolesCategory.canOnlyHaveOne + "\n" +
                            "2. Add or remove roles.").queue(message -> super.addMessageToCleaner(message));
                    sequenceNumber = 5;
                    break;
                case 5:
                    switch(Byte.parseByte(event.getMessage().getRawContent())) {
                        case 0:
                            super.getChannel().sendMessage("Please type a new name for the category.").queue(message -> super.addMessageToCleaner(message));
                            sequenceNumber = 6;
                            break;
                        case 1:
                        case 2:
                            //todo
                    }
                    break;
                case 6:
                    iAmRolesCategory.setCategoryName(event.getMessage().getContent());
                    super.getChannel().sendMessage("Name successfully changed.").queue(message -> message.delete().queueAfter(1, TimeUnit.MINUTES));
                    super.destroy();
            }
        }
    }

    /**
     * A role category
     *
     * This class is Thread safe.
     */
    class IAmRolesCategory {
        private String categoryName;
        private boolean canOnlyHaveOne;
        private final ArrayList<Long> roles;

        /**
         * Constructor for a new IAmRolesCategory.
         *
         * @param categoryName The name of the category.
         * @param canOnlyHaveOne If only one role can be self assigned from this category or multiple.
         */
        IAmRolesCategory(@NotNull String categoryName, boolean canOnlyHaveOne) {
            this.categoryName = categoryName;
            this.canOnlyHaveOne = canOnlyHaveOne;
            this.roles = new ArrayList<>();
        }

        /**
         * A constructor to load an existing IAmRolesCategory from a JSON file.
         *
         * @param categoryName The name of the category.
         * @param canOnlyHaveOne If only one role can be self assigned from this category or multiple.
         * @param roles
         */
        IAmRolesCategory(@JSONKey(jsonKey = "categoryName") String categoryName, @JSONKey(jsonKey = "canOnlyHaveOne") boolean canOnlyHaveOne, @JSONKey(jsonKey = "roles") JSONArray roles) {
            this.categoryName = categoryName;
            this.canOnlyHaveOne = canOnlyHaveOne;
            this.roles = new ArrayList<>(JSONToJavaObject.INSTANCE.toTypedList(roles, Long.class));
        }

        @JSONKey(jsonKey = "guildId")
        public synchronized String getCategoryName() {
            return categoryName;
        }

        @JSONKey(jsonKey = "roles")
        public synchronized List<Long> getRoles() {
            return Collections.unmodifiableList(roles);
        }

        @JSONKey(jsonKey = "canOnlyHaveOne")
        public synchronized boolean canOnlyHaveOne() {
            return canOnlyHaveOne;
        }

        synchronized void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        synchronized void setCanOnlyHaveOne(boolean canOnlyHaveOne) {
            this.canOnlyHaveOne = canOnlyHaveOne;
        }

        @Override
        public synchronized boolean equals(Object o) {
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
        public synchronized int hashCode() {
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
