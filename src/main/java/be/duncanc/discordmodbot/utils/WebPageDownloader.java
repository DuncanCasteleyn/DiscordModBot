/*
 * Copyright 2015-2016 Austin Keener
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

package be.duncanc.discordmodbot.utils;

import net.dv8tion.jda.core.utils.SimpleLog;
import org.slf4j.event.Level;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;

/**
 * Created by dunci on 29/01/2017.
 * <p>
 * web page downloader
 * <p>
 * Credits to DV8FromTheWorld
 * <p>
 * For the original see: https://github.com/DV8FromTheWorld/Yui/blob/master/src/main/java/net/dv8tion/discord/util/Downloader.java
 */
public class WebPageDownloader {
    private static final SimpleLog LOG = SimpleLog.getLog(WebPageDownloader.class);

    public static String webPage(String urlText) {
        String webpageText = "";
        URL url;
        InputStream is = null;
        BufferedReader br;
        String line;

        try {
            url = new URL(urlText);
            is = url.openStream();  // throws an IOException
            br = new BufferedReader(new InputStreamReader(is));

            while ((line = br.readLine()) != null) {
                webpageText += line;
            }
        } catch (IOException ioe) {
            LOG.log(Level.ERROR, ioe);
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException ignored) {
            }
        }
        return webpageText;
    }

    public static BufferedImage image(String urlText) {
        try {
            return ImageIO.read(new URL(urlText));
        } catch (IOException e) {
            System.out.printf("Could not find image at URL: %s\n", urlText);
            e.printStackTrace();
            return null;
        }
    }

    public static File file(String urlText, String fileName) throws IOException {
        File file = new File(fileName);
        boolean mkdirs = file.getParentFile().mkdirs();

        URL link = new URL(urlText); //The file that you want to download

        //Code to download
        InputStream in = new BufferedInputStream(link.openStream());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n = 0;
        while (-1 != (n = in.read(buf))) {
            out.write(buf, 0, n);
        }
        out.close();
        in.close();
        byte[] response = out.toByteArray();

        FileOutputStream fos = new FileOutputStream(file);
        fos.write(response);
        fos.close();
        return file;
    }
}
