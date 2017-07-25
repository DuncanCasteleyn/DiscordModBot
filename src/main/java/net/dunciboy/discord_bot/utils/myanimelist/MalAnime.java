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

package net.dunciboy.discord_bot.utils.myanimelist;

import java.time.OffsetDateTime;

/**
 * Created by Duncan on 27/02/2017.
 * <p>
 * Represent a data set of anime fetch from the mal api
 */
public class MalAnime {
    private int id;
    private String title;
    private String english;
    private String synonyms;
    private int episodes;
    private double score;
    private String type;
    private String status;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private String synopsis;

    public MalAnime(int id, String title, String english, String synonyms, int episodes, double score, String type, String status, OffsetDateTime startDate, OffsetDateTime endDate, String synopsis) {
        this.id = id;
        this.title = title;
        this.english = english;
        this.synonyms = synonyms;
        this.episodes = episodes;
        this.score = score;
        this.type = type;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.synopsis = synopsis;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getEnglish() {
        return english;
    }

    public String getSynonyms() {
        return synonyms;
    }

    public int getEpisodes() {
        return episodes;
    }

    public double getScore() {
        return score;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getStartDate() {
        return startDate;
    }

    public OffsetDateTime getEndDate() {
        return endDate;
    }

    public String getSynopsis() {
        return synopsis;
    }
}
