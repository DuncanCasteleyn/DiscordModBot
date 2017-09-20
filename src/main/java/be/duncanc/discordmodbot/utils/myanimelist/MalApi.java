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

package be.duncanc.discordmodbot.utils.myanimelist;

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
