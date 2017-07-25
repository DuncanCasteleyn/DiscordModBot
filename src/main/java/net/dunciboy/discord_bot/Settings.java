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
