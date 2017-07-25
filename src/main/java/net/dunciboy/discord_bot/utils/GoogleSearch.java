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

package net.dunciboy.discord_bot.utils;

import net.dv8tion.jda.core.utils.SimpleLog;
import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Created by Duncan on 26/01/2017.
 * <p>
 * Google search class.
 * <p>
 * Credits to DV8FromTheWorld
 * <p>
 * For the original see: https://github.com/DV8FromTheWorld/Yui/blob/master/src/main/java/net/dv8tion/discord/util/SearchResult.java
 */
public class GoogleSearch {
    private static final String GOOGLE_URL = "https://www.googleapis.com/customsearch/v1?cx=%s&key=%s&num=%d&q=%s";
    private static final SimpleLog LOG = SimpleLog.getLog(GoogleSearch.class.getSimpleName());
    private static String GOOGLE_API_KEY = null;
    private static LocalDateTime dayStartTime = null;
    private static int currentGoogleUsage = 0;

    public static void setup(String googleApiKey) {
        GOOGLE_API_KEY = googleApiKey;
        dayStartTime = LocalDateTime.now();
    }

    public static List<SearchResult> performSearch(String engineId, String terms) {
        return performSearch(engineId, terms, 1);
    }

    public static List<SearchResult> performSearch(String engineId, String terms, int requiredResultsCount) {
        try {
            if (GOOGLE_API_KEY == null)
                throw new IllegalStateException("Google API Key is null, Cannot preform google search without a key! Set one in the settings!");
            if (engineId == null || engineId.isEmpty())
                throw new IllegalArgumentException("Google Custom Search Engine id cannot be null or empty!");

            LocalDateTime currentTime = LocalDateTime.now();
            if (currentTime.isAfter(dayStartTime.plusDays(1))) {
                dayStartTime = currentTime;
                currentGoogleUsage = 1;
            } else if (currentGoogleUsage >= 80) {
                throw new IllegalStateException("Google usage has reached the premature security cap of 80");
            }

            terms = terms.replace(" ", "%20");
            String searchUrl = String.format(GOOGLE_URL, engineId, GOOGLE_API_KEY, requiredResultsCount, terms);

            URL searchURL = new URL(searchUrl);
            URLConnection conn = searchURL.openConnection();
            currentGoogleUsage++;
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:39.0) Gecko/20100101 " + randomName(10));
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                json.append(line).append("\n");
            }
            in.close();

            JSONArray jsonResults = new JSONObject(json.toString()).getJSONArray("items");
            List<SearchResult> results = new LinkedList<>();
            for (int i = 0; i < jsonResults.length(); i++) {
                results.add(SearchResult.fromGoogle(jsonResults.getJSONObject(i)));
            }
            return results;
        } catch (IOException e) {
            LOG.log(e);
            return null;
        }
    }

    private static String randomName(int randomLength) {
        char[] characters = new char[]
                {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
                        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                        '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'};

        Random rand = new Random();
        StringBuilder builder = new StringBuilder();
        builder.append("DiscordBot/");
        for (int i = 0; i < randomLength; i++) {
            builder.append(characters[rand.nextInt(characters.length)]);
        }
        return builder.toString();
    }

    public static class SearchResult {
        private String title;
        private String content;
        private String url;

        public static SearchResult fromGoogle(JSONObject googleResult) {
            SearchResult result = new SearchResult();
            result.title = cleanString(googleResult.getString("title"));
            result.content = cleanString(googleResult.getString("snippet"));
            try {
                result.url = URLDecoder.decode(cleanString(googleResult.getString("link")), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return result;
        }

        private static String cleanString(String uncleanString) {
            return StringEscapeUtils.unescapeJava(
                    StringEscapeUtils.unescapeHtml4(
                            uncleanString
                                    .replaceAll("\\s+", " ")
                                    .replaceAll("\\<.*?>", "")
                                    .replaceAll("\"", "")));
        }

        public String getTitle() {
            return content;
        }

        public String getContent() {
            return content;
        }

        public String getUrl() {
            return url;
        }

        public String getSuggestedReturn() {
            return url + " - *" + title + "*: \"" + content + "\"";
        }
    }
}
