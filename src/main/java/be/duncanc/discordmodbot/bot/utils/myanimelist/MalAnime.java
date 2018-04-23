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

package be.duncanc.discordmodbot.bot.utils.myanimelist;

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
