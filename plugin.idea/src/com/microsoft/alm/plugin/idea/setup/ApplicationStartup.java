// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.setup;

import com.intellij.idea.Main;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.util.containers.HashMap;
import com.microsoft.alm.client.utils.StringUtil;
import com.microsoft.alm.plugin.idea.services.CredentialsPromptImpl;
import com.microsoft.alm.plugin.idea.services.LocalizationServiceImpl;
import com.microsoft.alm.plugin.idea.services.PropertyServiceImpl;
import com.microsoft.alm.plugin.idea.services.ServerContextStoreImpl;
import com.microsoft.alm.plugin.idea.services.TelemetryContextInitializer;
import com.microsoft.alm.plugin.services.PluginServiceProvider;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Initializes and configures plugin at startup
 */
public class ApplicationStartup implements ApplicationComponent {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartup.class);
    private static final String CLASS_EXTENSION = ".class";
    private static final String USER_HOME_DIR = System.getProperty("user.home");
    private static final String VSTS_DIR = File.separator + ".vsts";
    private static final Pattern MAIN_CLASS_PATTERN = Pattern.compile("file:(.*?)lib/bootstrap.jar!/" + Main.class.getName() + CLASS_EXTENSION);
    private static final String LOCATION_FILE = File.separator + "locations.csv";
    private static final String MAC_EXE_DIR = "MacOS";
    private static final String LINUX_EXE_DIR = "bin";
    private static final String CSV_COMMA = ",";

    public ApplicationStartup() {
    }

    public void initComponent() {
        // Setup the services that the core plugin components need
        PluginServiceProvider.getInstance().initialize(
                new ServerContextStoreImpl(),
                new CredentialsPromptImpl(),
                new TelemetryContextInitializer(),
                PropertyServiceImpl.getInstance(),
                LocalizationServiceImpl.getInstance(),
                true);

        final File vstsDirectory = setupPreferenceDir(USER_HOME_DIR);
        final String ideLocation = getIdeLocation();
        doOsSetup(vstsDirectory, ideLocation);
    }

    public void disposeComponent() {
    }

    @NotNull
    public String getComponentName() {
        return "ApplicationStartup";
    }

    /**
     * Create .vsts directory in user's home directory if not already there
     *
     * @param parentDirectory
     * @return the vsts directory
     */
    protected File setupPreferenceDir(final String parentDirectory) {
        final File vstsDirectory = new File(parentDirectory + VSTS_DIR);
        if (!vstsDirectory.exists()) {
            vstsDirectory.mkdir();
        }
        return vstsDirectory;
    }

    /**
     * Find the current location of the IDE running
     *
     * @return the IDE path or an empty string if not found
     */
    protected String getIdeLocation() {
        final String resourcePath = Main.class.getResource(Main.class.getSimpleName() + CLASS_EXTENSION).getPath();
        final Matcher matcher = MAIN_CLASS_PATTERN.matcher(resourcePath);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // if the IDE could not be found an empty string is returned
        return StringUtil.EMPTY;
    }

    /**
     * Create the locations.csv file if it doesn't exist or check it for the IDE location.
     * If no location is found or they mismatch, insert the new location into the file.
     *
     * @param vstsDirectory
     * @param currentLocation
     */
    protected void cacheIdeLocation(final File vstsDirectory, final String currentLocation) {
        final Map<String, String> locationEntries = new HashMap<String, String>();
        final File locationsFile = new File(vstsDirectory.getPath() + LOCATION_FILE);
        BufferedReader bufferedReader = null;
        BufferedWriter bufferedWriter = null;
        String currentEntry = StringUtil.EMPTY;

        try {
            // if file doesn't exist create it else read the entries in it
            if (!locationsFile.exists()) {
                locationsFile.createNewFile();
            } else {
                String line;
                bufferedReader = new BufferedReader(new FileReader(locationsFile));
                while ((line = bufferedReader.readLine()) != null) {
                    final String[] entry = line.split(CSV_COMMA);
                    if (entry.length == 2) {
                        // find existing IDE entry if there is one
                        if (ApplicationNamesInfo.getInstance().getScriptName().equals(entry[0])) {
                            currentEntry = entry[1];
                        }
                        locationEntries.put(entry[0], entry[1]);
                    }
                }
                bufferedReader.close();
            }

            if (!currentEntry.equals(currentLocation) && !currentLocation.isEmpty()) {
                // delete current entry if it exists
                if (!currentEntry.isEmpty()) {
                    locationEntries.remove(ApplicationNamesInfo.getInstance().getScriptName());
                }

                // add current entry
                locationEntries.put(ApplicationNamesInfo.getInstance().getScriptName(), currentLocation);

                // rewrite file with new entry
                bufferedWriter = new BufferedWriter(new FileWriter(locationsFile.getPath()));
                for (String key : locationEntries.keySet()) {
                    bufferedWriter.write(key + CSV_COMMA + locationEntries.get(key) + "\n");
                }
                bufferedWriter.close();
            }
        } catch (FileNotFoundException e) {
            logger.warn("A FileNotFoundException was caught while trying to cache the IDE location: {}", e.getMessage());
        } catch (IOException e) {
            logger.warn("An IOException was caught while trying to cache the IDE location: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("An Exception was caught while trying to cache the IDE location: {}", e.getMessage());
        } finally {
            // try closing the buffered reader/writer in case it was missed
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }

                if (bufferedWriter != null) {
                    bufferedWriter.close();
                }
            } catch (IOException e) {
                logger.warn("An IOException was caught while trying to close the buffered reader/writer: {}", e.getMessage());
            }
        }
    }

    /**
     * Finds the OS type the plugin is running on and calls the setup for it
     */
    protected void doOsSetup(final File vstsDirectory, final String ideLocation) {
        if (Platform.isWindows()) {
            logger.debug("Windows operating system detected");
            WindowsStartup.startup();
        } else if (Platform.isMac()) {
            logger.debug("Mac operating system detected");
            cacheIdeLocation(vstsDirectory, ideLocation + MAC_EXE_DIR);
            MacStartup.startup();
        } else {
            logger.debug("Linux operating system detected ");
            cacheIdeLocation(vstsDirectory, ideLocation + LINUX_EXE_DIR);
            LinuxStartup.startup();
        }
    }
}