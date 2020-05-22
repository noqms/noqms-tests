/*
 * Copyright 2019 Stanley Barzee
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.noqms.tests.load;

import java.util.Properties;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.noqms.LogListener;
import com.noqms.MicroService;
import com.noqms.Starter;

// This is a self-contained test and may run many microservices in the same process. 
// Under normal circumstances a microservice is hosted singularly in its own (virtual) environment.
// Starting multiple microservices linearly typically takes seconds each.

public class LoadTest {
    private static final AtomicInteger requestsAtom = new AtomicInteger();

    private final int microServices;
    private final int threadsPerMicroService;
    private final int messagesPerMilliSecond;
    private final int dataLength;

    public LoadTest(int microServices, int threadsPerMicroService, int messagesPerMilliSecond, int dataLength) {
        this.microServices = microServices;
        this.threadsPerMicroService = threadsPerMicroService;
        this.messagesPerMilliSecond = messagesPerMilliSecond;
        this.dataLength = dataLength;
    }

    public void run() throws Exception {
        MyLogListener logListener = new MyLogListener();
        MicroService incoming = startMicroIncoming(dataLength, logListener);

        for (int ix = 1; ix <= microServices; ix++) {
            String microServiceName = "MS#" + String.valueOf(ix);
            startMicroTest(microServiceName, threadsPerMicroService, dataLength, logListener);
        }

        new Timer(true).schedule(new PrintProgressTask(requestsAtom), 1000, 1000);
        byte[] data = new byte[dataLength];

        // run for one minute
        long startTimeMillis = System.currentTimeMillis();
        int oneMinuteMillis = (int)TimeUnit.MINUTES.toMillis(1);
        Random random = new Random();
        while (System.currentTimeMillis() - startTimeMillis < oneMinuteMillis) {
            for (int ix = 0; ix < messagesPerMilliSecond; ix++) {
                String microServiceName = "MS#" + String.valueOf(1 + random.nextInt(microServices));
                incoming.sendRequestExpectResponse(microServiceName, data);
            }
            sleepMillis(1);
        }
    }

    private void startMicroTest(String name, int threads, int dataLength, LogListener logListener) throws Exception {
        Properties props = new Properties();
        props.setProperty(Starter.ARG_GROUP_NAME, "LoadTest");
        props.setProperty(Starter.ARG_SERVICE_NAME, name);
        props.setProperty(Starter.ARG_SERVICE_PATH, "com.noqms.tests.load.LoadTest$MicroTest");
        props.setProperty(Starter.ARG_THREADS, String.valueOf(threads));
        props.setProperty(Starter.ARG_TYPICAL_MILLIS, "10");
        props.setProperty(Starter.ARG_TIMEOUT_MILLIS, "100");
        props.setProperty(Starter.ARG_MAX_MESSAGE_IN_BYTES, String.valueOf(dataLength));
        props.setProperty(Starter.ARG_MAX_MESSAGE_OUT_BYTES, String.valueOf(dataLength));
        Starter.start(props, logListener);
    }

    private MicroService startMicroIncoming(int dataLength, LogListener logListener) throws Exception {
        Properties props = new Properties();
        props.setProperty(Starter.ARG_GROUP_NAME, "LoadTest");
        props.setProperty(Starter.ARG_SERVICE_NAME, "Incoming");
        props.setProperty(Starter.ARG_SERVICE_PATH, "com.noqms.tests.load.LoadTest$MicroIncoming");
        props.setProperty(Starter.ARG_THREADS, "1");
        props.setProperty(Starter.ARG_TYPICAL_MILLIS, "10");
        props.setProperty(Starter.ARG_TIMEOUT_MILLIS, "100");
        props.setProperty(Starter.ARG_MAX_MESSAGE_IN_BYTES, String.valueOf(dataLength));
        props.setProperty(Starter.ARG_MAX_MESSAGE_OUT_BYTES, String.valueOf(dataLength));
        return Starter.start(props, logListener);
    }

    public static class MicroIncoming extends MicroService {
    }

    public static class MicroTest extends MicroService {
        @Override
        public void processRequest(Long requestId, String serviceNameFrom, byte[] data) {
            sleepMillis(new Random().nextInt(10));
            sendResponse(requestId, null, null, null, data);
            requestsAtom.incrementAndGet();
        }
    }

    public static class MyLogListener implements LogListener {
        @Override
        public void logDebug(String text) {
            System.out.println(text);
        }
        
        @Override
        public void logInfo(String text) {
            System.out.println(text);
        }

        @Override
        public void logWarn(String text) {
            System.err.println(text);
            sleepMillis(100);
            System.exit(-1); // end the test
        }

        @Override
        public void logError(String text, Throwable th) {
            System.err.println(text);
            sleepMillis(100);
            System.exit(-1); // end the test
        }
    }

    private static void sleepMillis(long millis) {
        long sleptMillis = 0;
        while (sleptMillis < millis) {
            long millisStart = System.currentTimeMillis();
            try {
                Thread.sleep(millis - sleptMillis);
            } catch (Exception ex) {
            }
            sleptMillis += System.currentTimeMillis() - millisStart;
        }
    }

    private class PrintProgressTask extends TimerTask {
        private final AtomicInteger requestsAtom;

        private PrintProgressTask(AtomicInteger requestsAtom) {
            this.requestsAtom = requestsAtom;
        }

        @Override
        public void run() {
            System.out.println("Messages: " + 2 * requestsAtom.get()); // request + response
        }
    }
}
