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

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.IOException;
import java.net.URL;

/**
 * Created by Duncan on 27/02/2017.
 * <p>
 * Utility to fetch data from the mal api
 */
public class MalApi {
    public static MalAnime[] searchAnime(String searchTerm) {
        searchTerm = searchTerm.replace(" ", "+");
        try {
            URL url = new URL("https://myanimelist.net/api/anime/search.xml?q=" + searchTerm);
            XMLReader myReader = XMLReaderFactory.createXMLReader();
            myReader.parse(new InputSource(url.openStream()));
            //todo get properties from reader and then put into MalApi objects.
        } catch (IOException | SAXException e) {
            return null;
        }
        throw new UnsupportedOperationException("Not yet fully implemented");
    }
}
