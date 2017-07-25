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

package net.dunciboy.discord_bot;

import net.dv8tion.jda.core.utils.SimpleLog;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * Case system class that allows you to store items under case number.
 * Each guild has their own last number.
 * <p>
 * Created by Duncan on 7/06/2017.
 */
public class CaseSystem {
    private static final SimpleLog LOG = SimpleLog.getLog(CaseSystem.class.getSimpleName());
    private static final Path fileStorage = Paths.get("CaseSystem.json");

    private final long guildId;
    private long lastUsedNumber;

    /**
     * Constructs a case system for a guild
     */
    public CaseSystem(long guildId) {
        this.guildId = guildId;
        try {
            JSONObject jsonObject = readJsonObject();
            lastUsedNumber = jsonObject.getJSONObject(String.valueOf(guildId)).getLong("lastUsedNumber");
        } catch (IOException | JSONException e) {
            lastUsedNumber = 0;
        }
    }

    private JSONObject readJsonObject() throws IOException {
        synchronized (fileStorage) {
            StringBuilder stringBuilder = new StringBuilder();
            Files.newBufferedReader(fileStorage).lines().forEachOrdered(stringBuilder::append);
            return new JSONObject(stringBuilder.toString());
        }
    }

    /**
     * This method returns a new case number.
     *
     * @return A new case number
     * @throws IOException When the new case number can't be saved to the file for caching.
     */
    public long getNewCaseNumber() throws IOException {
        lastUsedNumber++;
        synchronized (fileStorage) {
            JSONObject jsonObject;
            try {
                jsonObject = readJsonObject();
            } catch (IOException | JSONException e) {
                jsonObject = new JSONObject();
            }
            String guildId = String.valueOf(this.guildId);
            if (jsonObject.has(guildId)) {
                jsonObject.getJSONObject(guildId).put("lastUsedNumber", lastUsedNumber);
            } else {
                jsonObject.put(guildId, new JSONObject().put("lastUsedNumber", lastUsedNumber));
            }
            try {
                Files.write(fileStorage, Collections.singletonList(jsonObject.toString()), Charset.defaultCharset());
            } catch (IOException e) {
                LOG.log(e);
                lastUsedNumber--;
                throw e;
            }
        }
        return lastUsedNumber;
    }

    public void reset() {
        JSONObject jsonObject;
        try {
            jsonObject = readJsonObject();
        } catch (IOException | JSONException e) {
            jsonObject = new JSONObject();
        }
        String guildId = String.valueOf(this.guildId);
        if (jsonObject.has(guildId)) {
            jsonObject.getJSONObject(guildId).put("lastUsedNumber", 0);
            synchronized (fileStorage) {
                try {
                    Files.write(fileStorage, Collections.singletonList(jsonObject.toString()), Charset.defaultCharset());
                } catch (IOException e) {
                    LOG.log(e);
                }
            }
        }
        lastUsedNumber = 0;
    }
}
