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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this module file, choose Tools | Templates
 * and open the module in the editor.
 */
package net.dunciboy.discord_bot;

/**
 * @author Duncan
 */

class Settings {

    private boolean logMessageDelete;
    private boolean logMessageUpdate;
    private boolean logMemberRemove;
    private boolean logMemberBan;
    private boolean logMemberAdd;
    private boolean logMemberRemoveBan;

    Settings() {
        this.logMessageUpdate = true;
        this.logMessageDelete = true;
        this.logMemberBan = true;
        this.logMemberRemove = true;
        this.logMemberAdd = true;
        this.logMemberRemoveBan = true;
    }

    public boolean isLogMessageUpdate() {
        return logMessageUpdate;
    }

    public void setLogMessageUpdate(boolean logMessageUpdate) {
        this.logMessageUpdate = logMessageUpdate;
    }

    boolean isLogMemberRemoveBan() {
        return logMemberRemoveBan;
    }

    void setLogMemberRemoveBan(boolean logMemberRemoveBan) {
        this.logMemberRemoveBan = logMemberRemoveBan;
    }

    boolean isLogMessageDelete() {
        return logMessageDelete;
    }

    void setLogMessageDelete(boolean logMessageDelete) {
        this.logMessageDelete = logMessageDelete;
    }

    boolean isLogMemberRemove() {
        return logMemberRemove;
    }

    void setLogMemberRemove(boolean logMemberRemove) {
        this.logMemberRemove = logMemberRemove;
    }

    boolean isLogMemberBan() {
        return logMemberBan;
    }

    public void setLogMemberBan(boolean logMemberBan) {
        this.logMemberBan = logMemberBan;
    }

    boolean isLogMemberAdd() {
        return logMemberAdd;
    }

    void setLogMemberAdd(boolean logMemberAdd) {
        this.logMemberAdd = logMemberAdd;
    }

    boolean isExceptedFromLogging(long channelId) {
        return channelId == 231422572011585536L || channelId == 205415791238184969L || channelId == 204047108318298112L;
    }
}
