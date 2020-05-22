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

package com.noqms.tests.roundtrip;

import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.noqms.LogListener;
import com.noqms.MicroService;
import com.noqms.RequestStatus;
import com.noqms.ResponseFuture;
import com.noqms.Starter;

// This is a self-contained test and may run many microservices in the same process. 
// Under normal circumstances a microservice is hosted singularly in its own (virtual) environment.
// Starting multiple microservices linearly typically takes seconds each.

public class RoundTripTest {
    private final int threads;

    public RoundTripTest(int threads) {
        this.threads = threads;
    }

    public void run() throws Exception {
        AtomicInteger requestsAtom = new AtomicInteger();
        MyLogListener logListener = new MyLogListener();
        long oneMinuteMillis = TimeUnit.MINUTES.toMillis(1);
        long oneMinuteSeconds = TimeUnit.MINUTES.toSeconds(1);

        MicroService incoming = startMicroIncoming(logListener);
        startMicroTest(threads, logListener);

        TestThread[] testThreads = new TestThread[threads];
        for (int ix = 0; ix < threads; ix++)
            testThreads[ix] = new TestThread(incoming, requestsAtom);
        for (int ix = 0; ix < threads; ix++)
            testThreads[ix].start();

        new Timer(true).schedule(new PrintProgressTask(requestsAtom), 1000, 1000);

        sleepMillis(oneMinuteMillis);
        for (int ix = 0; ix < threads; ix++)
            testThreads[ix].stop = true;
        sleepMillis(100);

        long messages = 2 * requestsAtom.get();
        System.out.println("messagesPerSecond=" + (messages / oneMinuteSeconds) + " threads=" + threads
                + " messagesPerSecondPerThread=" + (messages / oneMinuteSeconds / threads));
    }

    private static class TestThread extends Thread {
        private volatile boolean stop;
        private final MicroService incoming;
        private final AtomicInteger requestsAtom;

        public TestThread(MicroService incoming, AtomicInteger requestsAtom) {
            this.incoming = incoming;
            this.requestsAtom = requestsAtom;
            setDaemon(true);
        }

        public void run() {
            while (!stop) {
                requestsAtom.incrementAndGet();
                ResponseFuture responseFuture = incoming.sendRequestExpectResponse("Test", null);
                if (responseFuture.getRequestStatus() == RequestStatus.Ok)
                    responseFuture.await();
                else
                    break;
            }
        }
    }

    private void startMicroTest(int threads, LogListener logListener) throws Exception {
        Properties props = new Properties();
        props.setProperty(Starter.ARG_GROUP_NAME, "RoundTripTest");
        props.setProperty(Starter.ARG_SERVICE_NAME, "Test");
        props.setProperty(Starter.ARG_SERVICE_PATH, "com.noqms.tests.roundtrip.RoundTripTest$MicroTest");
        props.setProperty(Starter.ARG_THREADS, String.valueOf(threads));
        props.setProperty(Starter.ARG_TYPICAL_MILLIS, "10");
        props.setProperty(Starter.ARG_TIMEOUT_MILLIS, "100");
        props.setProperty(Starter.ARG_MAX_MESSAGE_IN_BYTES, "0");
        props.setProperty(Starter.ARG_MAX_MESSAGE_OUT_BYTES, "0");
        Starter.start(props, logListener);
    }

    private MicroService startMicroIncoming(LogListener logListener) throws Exception {
        Properties props = new Properties();
        props.setProperty(Starter.ARG_GROUP_NAME, "RoundTripTest");
        props.setProperty(Starter.ARG_SERVICE_NAME, "Incoming");
        props.setProperty(Starter.ARG_SERVICE_PATH, "com.noqms.tests.roundtrip.RoundTripTest$MicroIncoming");
        props.setProperty(Starter.ARG_THREADS, "1");
        props.setProperty(Starter.ARG_TYPICAL_MILLIS, "10");
        props.setProperty(Starter.ARG_TIMEOUT_MILLIS, "100");
        props.setProperty(Starter.ARG_MAX_MESSAGE_IN_BYTES, "100");
        props.setProperty(Starter.ARG_MAX_MESSAGE_OUT_BYTES, "100");
        return Starter.start(props, logListener);
    }

    public static class MicroIncoming extends MicroService {
    }

    public static class MicroTest extends MicroService {
        @Override
        public void processRequest(Long requestId, String serviceNameFrom, byte[] data, int threadIndex) {
            sendResponse(requestId, null, null, null, null);
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
