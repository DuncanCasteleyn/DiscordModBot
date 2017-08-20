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

package net.dunciboy.discord_bot.utils;

import net.dv8tion.jda.core.utils.SimpleLog;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by Duncan on 30/12/2016.
 * <p>
 * This class is used to store data in a text channel so it can be used later
 *
 * @deprecated Deprecated because each class should store it's date to it's own file and manage it by themselves, also to much static abuse.
 */
@Deprecated
public final class DataStorageParser {
    private static final SimpleLog LOG = SimpleLog.getLog(DataStorageParser.class.getSimpleName());
    private static final Runnable autoSaver;
    private static final Runnable jsonWriter;
    private static final Path file = Paths.get("DiscordBot.json");
    private static final ArrayList<StorageAutoSavable> autoSavableList = new ArrayList<>();
    private static final JSONObject jsonObject;
    private static Thread writeToFileThread = null;

    static {
        JSONObject notFinalJSONObject;
        try {
            List<String> input = Files.readAllLines(file);
            StringBuilder inputString = new StringBuilder();
            input.forEach(inputString::append);
            notFinalJSONObject = new JSONObject(inputString.toString());
        } catch (JSONException | NoSuchFileException e) {
            notFinalJSONObject = new JSONObject();
        } catch (Exception e) {
            LOG.log(e);
            throw new InstantiationError(e.getMessage());
        }
        jsonObject = notFinalJSONObject;

        autoSaver = new Runnable() {
            /**
             * Start a thread running all the method from StorageAutoSavable interface.
             *
             * @see StorageAutoSavable
             */
            @Override
            public synchronized void run() {
                do {
                    try {
                        TimeUnit.HOURS.timedWait(autoSaver, 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    synchronized (autoSavableList) {
                        autoSavableList.forEach(StorageAutoSavable::saveAllData);
                        autoSavableList.notifyAll();
                    }
                } while (!Thread.currentThread().isInterrupted());
            }
        };


        Thread autoSaveThread = new Thread(autoSaver, "Static auto saver for dataStorageParser");
        autoSaveThread.setDaemon(true);
        autoSaveThread.start();

        jsonWriter = new Runnable() {
            /**
             * Start a thread running all the method from StorageAutoSavable interface.
             * <p>
             * Will send a notifyAll when it finishes
             *
             * @see StorageAutoSavable
             */
            @Override
            public void run() {
                List<String> lines = Collections.singletonList(jsonObject.toString());
                try {
                    Files.write(file, lines, Charset.forName("UTF-8"));
                } catch (ClosedByInterruptException ignored) {
                } catch (Exception e) {
                    LOG.log(e);
                }
            }
        };
    }

    private DataStorageParser() {
        throw new AssertionError("Instating this class is not allowed.");
    }

//    /**
//     * Stores the HashMap in a text channel that can be queried back later
//     * <p>
//     * Do not use the following signs in your value string: '!','#',';' and '|'
//     *
//     * @param name the name for the data structure
//     * @param data stores this HashMap.
//     * @throws IOException When HashMap contains characters that are not allowed.
//     * @deprecated Should not be used because it's not meant for big data storage, use json instead.
//     */
//    @Deprecated
//    static void writeDataToTextChannel(String name, HashMap<String, String> data, TextChannel databaseChannel) throws IOException {
//        for (String s : data.values()) {
//            if (s.contains("!") || s.contains("#") || s.contains("=") || s.contains("|")) {
//                throw new IOException("one of the values of the HashMap contains a character that breaks the data structure");
//            }
//        }
//        for (String s : data.keySet()) {
//            if (s.contains("!") || s.contains("#") || s.contains("=") || s.contains("|")) {
//                throw new IOException("one of the keys of the HashMap contains a character that breaks the data structure");
//            }
//        }
//        StringBuilder dataStringBuilder = new StringBuilder("!" + name + "#");
//        data.forEach((field, fieldData) -> dataStringBuilder.append(field).append("=").append(fieldData).append("|"));
//        dataStringBuilder.replace(dataStringBuilder.length() - 1, dataStringBuilder.length(), "!");
//        databaseChannel.sendMessage(dataStringBuilder.toString()).queue();
//    }
//
//    /**
//     * Reads the most recent data structure from the channel with the given name.
//     *
//     * @param name name of the data structure.
//     * @return A HashMap with the data set.
//     * @throws IOException When no data is found or parsing fails.
//     * @deprecated Should not be used because it's not meant for big data storage, use json instead.
//     */
//    @Deprecated
//    public static HashMap<String, String> readDataFromTextChannel(String name, TextChannel databaseChannel) throws IOException {
//        int startDataStructure = -1;
//        List<Message> messages;
//        try {
//            messages = databaseChannel.getHistory().retrievePast(100).complete();
//
//            for (int i = 0; i < messages.size(); i++) {
//                if (messages.get(i).getContent().charAt(0) == '!' && messages.get(i).getContent().length() > 1 &&
//                        messages.get(i).getContent().replace("!", "").split("#")[0].equals(name)) {
//                    startDataStructure = i;
//                    break;
//                }
//            }
//        } catch (Exception e) {
//            throw new IOException(e);
//        }
//
//        if (startDataStructure == -1) {
//            throw new IOException("Can't find data in channel");
//        }
//
//        try {
//            StringBuilder dataStringBuilder = new StringBuilder();
//            for (int i = startDataStructure; i >= 0; i--) {
//                dataStringBuilder.append(messages.get(i).getContent());
//                if (messages.get(i).getContent().charAt(messages.get(i).getContent().length() - 1) == '!') {
//                    break;
//                }
//            }
//            String[] dataString = dataStringBuilder.toString().replace("!", "").split("#")[1].split("\\|");
//            HashMap<String, String> hashMap = new HashMap<>();
//            for (String aDataLine : dataString) {
//                String[] dataLine = aDataLine.split("=");
//                hashMap.put(dataLine[0], dataLine.length >= 2 ? dataLine[1] : null);
//            }
//
//            return hashMap;
//
//        } catch (Exception e) {
//            throw new IOException(e);
//        }
//    }

    /**
     * Writes the Map into a json file.
     *
     * @param className Name to store the objects under.
     * @param name      The name for this data will be stored under.
     * @param data      The actual data you want to store.
     */
    public static void writeToJson(String className, String name, Map<String, String> data) {
        if (jsonObject.has(className)) {
            JSONObject classJSONObject = jsonObject.getJSONObject(className);
            classJSONObject.put(name, data);
        } else {
            jsonObject.put(className, new JSONObject().put(name, data));
        }

        writeJSONToFile();
    }

    private static void writeJSONToFile() {
        if (writeToFileThread != null) {
            if (writeToFileThread.isAlive()) {
                writeToFileThread.interrupt();
                try {
                    writeToFileThread.join();
                } catch (InterruptedException ignored) {
                }
            }
        }


        writeToFileThread = new Thread(jsonWriter, "JSON to file jsonWriter thread.");
        writeToFileThread.setDaemon(false);
        writeToFileThread.start();
    }

    /**
     * Writes the list into a json file.
     *
     * @param className Name to store the objects under.
     * @param name      The name for this data will be stored under.
     * @param data      The actual data you want to store.
     */
    public static void writeToJson(String className, String name, List<String> data) {
        if (jsonObject.has(className)) {
            JSONObject classJSONObject = jsonObject.getJSONObject(className);
            classJSONObject.put(name, data);
        } else {
            jsonObject.put(className, new JSONObject().put(name, data));
        }

        writeJSONToFile();
    }

    public static Map<String, String> readMapFromJson(String className, String name) {
        HashMap<String, String> returnHashMap = new HashMap<>();
        try {
            jsonObject.getJSONObject(className).getJSONObject(name).toMap().forEach((s, o) -> returnHashMap.put(s, o.toString()));
        } catch (JSONException e) {
            writeToJson(className, name, returnHashMap);
        }

        return returnHashMap;
    }

    public static List<String> readListFromJson(String className, String name) {
        List<String> returnList = new ArrayList<>();
        try {
            jsonObject.getJSONObject(className).getJSONArray(name).toList().forEach((o) -> returnList.add(o.toString()));
        } catch (JSONException e) {
            writeToJson(className, name, returnList);
        }

        return returnList;
    }

    /**
     * Adds the object implementing the StorageAutoSavable interface to the auto save list.
     * It's main purpose it to save data from classes that use the DataStorageParser, but other objects could implement this as well to become executed automatically.
     * All objects their saveAllData method will be executed every 1 hour.
     *
     * @param storageAutoSavable an object implementing the StorageAutoSavable interface.
     */
    public static void addToAutoSave(StorageAutoSavable... storageAutoSavable) {
        synchronized (autoSavableList) {
            Collections.addAll(autoSavableList, storageAutoSavable);
        }
    }

    /**
     * Removes the object from the auto save list and performs a final save before doing so.
     *
     * @param storageAutoSavable an object implementing the StorageAutoSavable interface.
     */
    public static void removeFromAutoSave(StorageAutoSavable... storageAutoSavable) {
        synchronized (autoSaver) {
            autoSaver.notifyAll();
        }
        synchronized (autoSavableList) {
            try {
                TimeUnit.SECONDS.timedWait(autoSavableList, 15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            autoSavableList.removeAll(Arrays.asList(storageAutoSavable));
        }
    }

    /**
     * Implementing this interface allow you to add it to the auto save method included with this dataStorageParser
     */
    public interface StorageAutoSavable {

        /**
         * This method is to be used to save all the data that needs to be stored by the DataStorageParser auto saver
         */
        void saveAllData();
    }
}