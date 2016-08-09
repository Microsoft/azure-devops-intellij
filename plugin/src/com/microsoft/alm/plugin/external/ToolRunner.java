// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.external;

import com.microsoft.alm.common.utils.ArgumentHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is used to run an external command line tool and listen to the output.
 * Use the nested class ArgumentBuilder to build up the list of arguments and the nested interface Listener to
 * get callbacks for processing output, exceptions and completion events.
 */
public class ToolRunner {
    private static final Logger logger = LoggerFactory.getLogger(ToolRunner.class);

    private Process toolProcess;
    private String toolLocation;
    private ArgumentBuilder argumentBuilder;
    private StreamProcessor standardErrorProcessor;
    private StreamProcessor standardOutProcessor;
    private ProcessWaiter processWaiter;

    /**
     * Implement this class to get callbacks on events triggered by the ToolRunner.
     */
    public interface Listener {
        void processStandardOutput(final String line);

        void processStandardError(final String line);

        void processException(final Throwable throwable);

        void completed(int returnCode);
    }

    /**
     * Create an instance of this class to build up the arguments that should be passed to the Tool.
     * These arguments are logged by the tool runner. If an argument should not be written to the log,
     * Call addSecret instead of add on that argument. This will produce ****** in the log in place of the
     * actual argument value.
     */
    public static class ArgumentBuilder {
        private static final String STARS = "********";
        List<String> arguments = new ArrayList<String>(5);
        Set<Integer> secretArgumentIndexes = new HashSet<Integer>(5);

        public ArgumentBuilder() {
        }

        public ArgumentBuilder add(final String argument) {
            arguments.add(argument);
            return this;
        }

        public ArgumentBuilder addSecret(final String argument) {
            add(argument);
            secretArgumentIndexes.add(arguments.size()-1);
            return this;
        }

        public List<String> build() {
            return Collections.unmodifiableList(arguments);
        }

        /**
         * This method returns an unmodifiable list of arguments.
         * The toolLocation passed in is inserted as the very first argument.
         */
        public List<String> build(final String toolLocation) {
            final List<String> commandLineParts = new ArrayList<String>(arguments);
            commandLineParts.add(0, toolLocation);
            return Collections.unmodifiableList(commandLineParts);
        }

        /**
         * Use this method to easily log all of the arguments.
         * Secret arguments will be shown as *******
         */
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            for(int i = 0; i < arguments.size(); i++) {
                final String arg;
                if (secretArgumentIndexes.contains(i)) {
                    arg = STARS;
                } else {
                    arg = arguments.get(i);
                }
                builder.append(arg);
                builder.append(" ");
            }
            return builder.toString();
        }
    }

    public ToolRunner(final String toolLocation, final ArgumentBuilder argumentBuilder) {
        this.toolLocation = toolLocation;
        this.argumentBuilder = argumentBuilder;
    }

    public Process start(final Listener listener) {
        logger.info("ToolRunner.start: toolLocation = " + toolLocation);
        logger.info("ToolRunner.start: arguments = " + argumentBuilder.toString());

        // Create the process builder from the tool location and all the arguments
        final ProcessBuilder pb = new ProcessBuilder(argumentBuilder.build(toolLocation));
        try {
            toolProcess = pb.start();
            if (listener != null) {
                // We have a listener object so create the listener threads and hook up the listener
                final InputStream stderr = toolProcess.getErrorStream();
                final InputStream stdout = toolProcess.getInputStream();
                standardErrorProcessor = new StreamProcessor(stderr, true, listener);
                standardErrorProcessor.start();
                standardOutProcessor = new StreamProcessor(stdout, false, listener);
                standardOutProcessor.start();
                processWaiter = new ProcessWaiter(toolProcess, listener);
                processWaiter.start();
            }
            return toolProcess;
        } catch (final IOException e) {
            logger.error("Failed to start tool process or redirect output.", e);
            if (listener != null) {
                listener.processException(e);
            }
        }

        return null;
    }

    /**
     * Call the dispose method to make sure all threads are cleaned up and disposed of properly.
     */
    public void Dispose() {
        try {
            if (processWaiter != null) {
                processWaiter.cleanUp();
                processWaiter = null;
            }
            if (standardErrorProcessor != null) {
                standardErrorProcessor.cleanUp();
                standardErrorProcessor = null;
            }
            if (standardOutProcessor != null) {
                standardOutProcessor.cleanUp();
                standardOutProcessor = null;
            }
        } catch (final InterruptedException e) {
            logger.error("Failed to dispose ToolRunner.", e);
        }
    }

    /**
     * This internal class is used to manage the thread that waits on the process to finish.
     * It takes in the process to wait on and the listener to issue callbacks to.
     */
    private class ProcessWaiter extends Thread {
        private final Process process;
        private final Listener listener;

        public ProcessWaiter(final Process process, final Listener listener) {
            ArgumentHelper.checkNotNull(process, "process");
            ArgumentHelper.checkNotNull(listener, "listener");
            this.process = process;
            this.listener = listener;
        }

        @Override
        public void run() {
            // Don't let exceptions escape from this top level method
            try {
                // Wait for the process to finish
                process.waitFor();
                // Call the completed event on the listener with the exit code
                // Note that all the standard output/error may not have finished
                listener.completed(process.exitValue());
            } catch (Throwable e) {
                logger.error("Failed to wait for process exit.", e);
                listener.processException(e);
            }
        }

        /**
         * This method forces the thread to end by interrupting it and joining with the calling thread.
         * @throws InterruptedException
         */
        public void cleanUp() throws InterruptedException {
            this.interrupt();
            this.join();
        }
    }

    /**
     * This internal class is used to manage the threads that receive the output from the process.
     * One thread is created to listen for standard output and one is created to listen to standard error.
     * The constructor takes in the stream to listen to, what kind of stream it is, and the listener to
     * issue callbacks to.
     */
    private class StreamProcessor extends Thread {

        private final InputStream stream;
        private final boolean isStandardError;
        private final Listener listener;

        public StreamProcessor(InputStream stream, boolean isStandardError, Listener listener) {
            ArgumentHelper.checkNotNull(stream, "stream");
            ArgumentHelper.checkNotNull(listener, "listener");
            this.stream = stream;
            this.isStandardError = isStandardError;
            this.listener = listener;
        }

        @Override
        public void run() {
            BufferedReader bufferedReader = null;

            // Don't let exceptions escape from this top level method
            try {
                // Create a buffered reader so that we can process the output one line at a time
                bufferedReader = new BufferedReader(new InputStreamReader(stream));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    // Call the appropriate event with the line that was read
                    if (isStandardError) {
                        listener.processStandardError(line);
                    } else {
                        listener.processStandardOutput(line);
                    }
                }
            } catch (Throwable e) {
                logger.error("Failed to process output.", e);
                listener.processException(e);
            } finally {
                // Don't let exceptions escape from this top level method
                try {
                    // Make sure to close the buffered reader
                    if (bufferedReader != null) {
                        bufferedReader.close();
                    }
                } catch (Throwable e) {
                    logger.error("Failed to close buffer.", e);
                    listener.processException(e);
                }
            }
        }

        /**
         * This method forces the thread to end by interrupting it and joining with the calling thread.
         * @throws InterruptedException
         */
        public void cleanUp() throws InterruptedException {
            this.interrupt();
            this.join();
        }
    }
}
