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

import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONKey;
import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONToJavaObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IAmRoles {
    private long guildId;
    private boolean canOnlyHaveOne;
    private ArrayList<Long> roles;

    public IAmRoles(long guildId, boolean canOnlyHaveOne) {
        this.guildId = guildId;
        this.canOnlyHaveOne = canOnlyHaveOne;
    }

    public IAmRoles(@JSONKey(jsonKey = "guildId") long guildId, @JSONKey(jsonKey = "canOnlyHaveOne") boolean canOnlyHaveOne, @JSONKey(jsonKey = "roles") JSONArray roles) {
        this.guildId = guildId;
        this.canOnlyHaveOne = canOnlyHaveOne;
        this.roles = new ArrayList<>(JSONToJavaObject.INSTANCE.toTypedList(roles, Long.class));
    }

    @JSONKey(jsonKey = "guildId")
    public long getGuildId() {
        return guildId;
    }

    @JSONKey(jsonKey = "roles")
    public List<Long> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    @JSONKey(jsonKey = "canOnlyHaveOne")
    public boolean isCanOnlyHaveOne() {
        return canOnlyHaveOne;
    }
}
