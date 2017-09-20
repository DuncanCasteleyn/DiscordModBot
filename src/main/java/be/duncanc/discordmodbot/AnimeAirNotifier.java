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
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.OffsetTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * Created by Duncan on 1/03/2017.
 * <p>
 * This class will notify users about airing anime.
 */
public class AnimeAirNotifier {
    private static final SimpleLog LOG = SimpleLog.getLog(AnimeAirNotifier.class.getSimpleName());
    private static final Path file = Paths.get("AnimeAirNotifier.json");
    private static final HashMap<Integer, AnimeAirInfo> animeAirInfoMap;
    private static final HashMap<Long, ArrayList<Subscription>> subscriptions;

    static {
        List<String> input = null;
        try {
            input = Files.readAllLines(file);
        } catch (IOException e) {
            LOG.log(e);
        }
        JSONObject jsonObject;
        if (input != null) {
            StringBuilder inputString = new StringBuilder();
            input.forEach(inputString::append);
            jsonObject = new JSONObject(inputString.toString());
        } else {
            jsonObject = null;
        }

        HashMap<Integer, AnimeAirInfo> animeAirInfoHashMapTry;
        try {
            final HashMap<Integer, AnimeAirInfo> animeAirInfoMapAttempt = new HashMap<>();
            if (jsonObject != null) {
                jsonObject.getJSONObject("animeAirInfoMap").toMap().forEach((s, o) -> animeAirInfoMapAttempt.put(Integer.parseInt(s), AnimeAirInfo.parse(o.toString())));
            }
            animeAirInfoHashMapTry = animeAirInfoMapAttempt;
        } catch (Exception e) {
            animeAirInfoHashMapTry = new HashMap<>();
        }
        animeAirInfoMap = animeAirInfoHashMapTry;

        HashMap<Long, ArrayList<Subscription>> subscriptionsTry;
        try {
            final HashMap<Long, ArrayList<Subscription>> subscriptionsAttempt = new HashMap<>();
            if (jsonObject != null) {
                jsonObject.getJSONObject("subscriptions").toMap().forEach((s, o) -> {
                    JSONArray jsonArray = new JSONArray(o);
                    ArrayList<Subscription> subscriptions1 = new ArrayList<>();
                    jsonArray.forEach(o1 -> subscriptions1.add(Subscription.parse(o1.toString())));
                });
            }
            subscriptionsTry = subscriptionsAttempt;
        } catch (Exception e) {
            subscriptionsTry = new HashMap<>();
        }

        subscriptions = subscriptionsTry;
    }

    public static void saveToJson() {
        synchronized (animeAirInfoMap) {
            synchronized (subscriptions) {
                JSONObject jsonObject = new JSONObject();
                HashMap<String, JSONObject> animeAirInfoJsonMap = new HashMap<>();
                animeAirInfoMap.forEach((integer, animeAirInfo) -> animeAirInfoJsonMap.put(integer.toString(), animeAirInfo.toJsonObject()));
                jsonObject.put("animeAirInfoMap", animeAirInfoJsonMap);
                HashMap<Long, JSONArray> subscriptionsJsonMap = new HashMap<>();
                subscriptions.forEach((s, subscriptions1) -> {
                    JSONArray jsonArray = new JSONArray();
                    subscriptions1.forEach(subscription -> jsonArray.put(subscription.toJsonObject()));
                    subscriptionsJsonMap.put(s, jsonArray);
                });
                jsonObject.put("subscriptions", subscriptionsJsonMap);
                List<String> lines = Collections.singletonList(jsonObject.toString());
                try {
                    Files.write(file, lines, Charset.forName("UTF-8"));
                } catch (ClosedByInterruptException ignored) {
                } catch (Exception e) {
                    LOG.log(e);
                }
            }
        }
    }

    public static Map<Integer, AnimeAirInfo> getAnime() {
        return new HashMap<>(animeAirInfoMap);
    }

    public static void addAnime(AnimeAirInfo animeAirInfo) {
        synchronized (animeAirInfoMap) {
            for (int i = 0; i < Integer.MAX_VALUE && i >= 0; i++) {
                if (!animeAirInfoMap.keySet().contains(i)) {
                    animeAirInfoMap.put(i, animeAirInfo);
                    return;
                }
            }
        }
        throw new IllegalStateException("Reached maximum available integer values for HasMap");
    }

    public static boolean removeAnime(int animeId) {
        synchronized (animeAirInfoMap) {
            synchronized (subscriptions) {
                subscriptions.forEach((s, subList) -> subList.stream().filter(subscription -> subscription.getAnimeId() == animeId).forEach(subList::remove));
            }
            return animeAirInfoMap.remove(animeId) != null;
        }
    }

    public static boolean subscribe(long userId, int animeId) {
        synchronized (animeAirInfoMap) {
            if (!animeAirInfoMap.containsKey(animeId)) {
                return false;
            }
        }
        synchronized (subscriptions) {
            if (subscriptions.containsKey(userId)) {
                ArrayList<Subscription> subList = subscriptions.get(userId);
                return subList.stream().noneMatch(subscription -> subscription.getAnimeId() == animeId) && subList.add(new Subscription(animeId));
            } else {
                subscriptions.put(userId, new ArrayList<>());
                if (subscribe(userId, animeId)) {
                    return true;
                } else {
                    subscriptions.remove(userId);
                    return false;
                }
            }
        }
    }

    public static boolean removeSubscription(long userId, int animeId) {
        synchronized (subscriptions) {
            if (subscriptions.containsKey(userId)) {
                ArrayList<Subscription> subList = subscriptions.get(userId);
                Stream<Subscription> stream = subList.stream().filter(subscription -> subscription.getAnimeId() == animeId);
                if (stream.count() > 0) {
                    stream.forEach(subList::remove);
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    public static Map<Integer, AnimeAirInfo> getSubscriptionList(long userId) {
        Map<Integer, AnimeAirInfo> animeAirInfoMap = new HashMap<>();
        subscriptions.get(userId).forEach(subscription -> animeAirInfoMap.put(subscription.getAnimeId(), animeAirInfoMap.get(subscription.getAnimeId())));
        return animeAirInfoMap;
    }

    public static AnimeAirInfo[] animeAired(long userId) {
        synchronized (subscriptions) {
            ArrayList<Subscription> subscriptionArrayList = subscriptions.get(userId);
            synchronized (animeAirInfoMap) {
                return (AnimeAirInfo[]) subscriptionArrayList.stream().filter(subscription -> animeAirInfoMap.get(subscription.getAnimeId()).getAiredEpisodes() < subscription.getNotified()).toArray();
            }
        }
    }


    public static class AnimeAirInfo {
        private DayOfWeek airDay;
        private int airedEpisodes;
        private int maxEpisodes;
        private String animeName;
        private OffsetTime airTime;
        private String creator;
        private String malUrl;
        private boolean accepted;
        private boolean aired;
        private boolean notAiring;

        public AnimeAirInfo(DayOfWeek airDay, String animeName, OffsetTime airTime, String creator, String malUrl) {
            init(airDay, animeName, airTime, 0, 0, creator, malUrl, false, false, false);
        }

        /**
         * Constructor that creates this object based on a string that should represent a JSONObject of this object.
         *
         * @param jsonObject JSONObject of this object.
         */
        AnimeAirInfo(JSONObject jsonObject) {
            init(DayOfWeek.of(jsonObject.getInt("airDay")), jsonObject.getString("animeName"), (OffsetTime) jsonObject.get("airTime"), jsonObject.getInt("airedEpisodes"), jsonObject.getInt("maxEpisodes"), jsonObject.getString("creator"), jsonObject.getString("malUrl"), jsonObject.getBoolean("accepted"), jsonObject.getBoolean("aired"), jsonObject.getBoolean("notAiring"));
        }

        static AnimeAirInfo parse(String string) {
            return new AnimeAirInfo(new JSONObject(string));
        }

        public String getCreator() {
            return creator;
        }

        void setCreator(String creator) {
            this.creator = creator;
        }

        private void init(DayOfWeek airDay, String animeName, OffsetTime airTime, int airedEpisodes, int maxEpisodes, String creator, String malUrl, boolean accepted, boolean aired, boolean notAiring) {
            setAirDay(airDay);
            setAnimeName(animeName);
            setAirTime(airTime);
            setAiredEpisodes(airedEpisodes);
            setCreator(creator);
            setMalUrl(malUrl);
            setAccepted(false);
            setAired(aired);
            setNotAiring(notAiring);
            setMaxEpisodes(this.maxEpisodes);
        }

        public boolean isAccepted() {
            return accepted;
        }

        private void setAccepted(boolean accepted) {
            if (this.accepted && !accepted) {
                throw new IllegalArgumentException("An accepted anime cannot be unaccepted");
            }
            this.accepted = accepted;
        }

        public void accept() {
            setAccepted(true);
        }

        boolean isAired() {
            return aired;
        }

        public void setAired(boolean aired) {
            this.aired = aired;
        }

        public JSONObject toJsonObject() {
            return new JSONObject()
                    .put("airDay", airDay.getValue())
                    .put("animeName", animeName)
                    .put("airTime", airTime)
                    .put("airedEpisodes", airedEpisodes)
                    .put("maxEpisodes", maxEpisodes)
                    .put("creator", creator)
                    .put("malUrl", malUrl)
                    .put("accepted", accepted)
                    .put("aired", aired)
                    .put("notAiring", notAiring);
        }

        public byte getAirDay() {
            return ((byte) airDay.getValue());
        }

        public void setAirDay(DayOfWeek airDay) {
            this.airDay = airDay;
        }

        public String getAnimeName() {
            return animeName;
        }

        public void setAnimeName(String animeName) {
            this.animeName = animeName;
        }

        OffsetTime getAirTime() {
            return airTime;
        }

        public void setAirTime(OffsetTime airTime) {
            this.airTime = airTime;
        }

        public boolean isNotAiring() {
            return notAiring;
        }

        public void setNotAiring(boolean notAiring) {
            this.notAiring = notAiring;
        }

        public int getAiredEpisodes() {
            return airedEpisodes;
        }

        public void setAiredEpisodes(int airedEpisodes) {
            this.airedEpisodes = airedEpisodes;
        }

        public int getMaxEpisodes() {
            return maxEpisodes;
        }

        public void setMaxEpisodes(int maxEpisodes) {
            this.maxEpisodes = maxEpisodes;
        }

        public String getMalUrl() {
            return malUrl;
        }

        public void setMalUrl(String malUrl) {
            this.malUrl = malUrl;
        }
    }

    public static class Subscription {
        private int animeId;
        private int notified;

        public Subscription(int animeId) {
            setAnimeId(animeId);
            setNotified(0);
        }

        public Subscription(JSONObject jsonObject) {
            setAnimeId(jsonObject.getInt("animeId"));
            setNotified(jsonObject.getInt("notified"));
        }

        static Subscription parse(String string) {
            return new Subscription(new JSONObject(string));
        }

        public JSONObject toJsonObject() {
            return new JSONObject()
                    .put("animeId", animeId)
                    .put("notified", notified);
        }

        public int getAnimeId() {
            return animeId;
        }

        public void setAnimeId(int animeId) {
            this.animeId = animeId;
        }

        public int getNotified() {
            return notified;
        }

        public void setNotified(int notified) {
            this.notified = notified;
        }


    }
}
