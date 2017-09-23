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

package be.duncanc.discordmodbot;

import net.dv8tion.jda.core.utils.SimpleLog;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.event.Level;

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
                LOG.log(Level.ERROR, e);
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
                    LOG.log(Level.ERROR, e);
                }
            }
        }
        lastUsedNumber = 0;
    }
}
