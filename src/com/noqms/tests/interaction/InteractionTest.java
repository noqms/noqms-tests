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

package com.noqms.tests.interaction;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.noqms.LogListener;
import com.noqms.MicroService;
import com.noqms.Starter;

// This is a self-contained test and may run many microservices in the same process. 
// Under normal circumstances a microservice is hosted singularly in its own (virtual) environment.
// Starting multiple microservices linearly typically takes seconds each.

public class InteractionTest {
    private static final Gson gson = new Gson();
    private static final Random random = new Random();

    private final int microServices;
    private final int threadsPerMicroService;
    private final int messages;

    public InteractionTest(int microServices, int threadsPerMicroService, int messages) {
        this.microServices = microServices;
        this.threadsPerMicroService = threadsPerMicroService;
        this.messages = messages;
    }

    public void run() throws Exception {
        MyLogListener logListener = new MyLogListener();
        MicroService incoming = startIncoming(logListener);

        MicroService[] micros = new MicroService[microServices];

        for (int ix = 1; ix <= microServices; ix++) {
            String microServiceName = "MS#" + String.valueOf(ix);
            micros[ix - 1] = startMicroTest(microServiceName, threadsPerMicroService, logListener);
        }

        // load up messages in the system and let them bounce around

        Model model = new Model();
        model.microServices = microServices;
        byte[] data = gson.toJson(model).getBytes(StandardCharsets.UTF_8);
        for (int ix = 0; ix < messages; ix += 10) {
            String microServiceName = "MS#" + String.valueOf(1 + random.nextInt(microServices));
            incoming.sendRequestExpectResponse(microServiceName, data);
            sleepMillis(1);
        }

        sleepMillis(TimeUnit.SECONDS.toMillis(30));

        // drain one service so that it eventually shows as unresponsive - there is no replacement service
        micros[0].drain();

        for (int ix = 0; ix < messages; ix += 10) {
            String microServiceName = "MS#" + String.valueOf(1 + random.nextInt(microServices));
            incoming.sendRequestExpectResponse(microServiceName, data);
            sleepMillis(1);
        }

        sleepMillis(TimeUnit.SECONDS.toMillis(30));
    }

    private static class Model {
        private int microServices;
    }

    private MicroService startMicroTest(String name, int threads, LogListener logListener) throws Exception {
        Properties props = new Properties();
        props.setProperty(Starter.ARG_GROUP_NAME, "InteractionTest");
        props.setProperty(Starter.ARG_SERVICE_NAME, name);
        props.setProperty(Starter.ARG_SERVICE_PATH, "com.noqms.tests.interaction.InteractionTest$MicroTest");
        props.setProperty(Starter.ARG_THREADS, String.valueOf(threads));
        props.setProperty(Starter.ARG_TYPICAL_MILLIS, "10");
        props.setProperty(Starter.ARG_TIMEOUT_MILLIS, "100");
        props.setProperty(Starter.ARG_MAX_MESSAGE_IN_BYTES, "100");
        props.setProperty(Starter.ARG_MAX_MESSAGE_OUT_BYTES, "100");
        return Starter.start(props, logListener);
    }

    private MicroService startIncoming(LogListener logListener) throws Exception {
        Properties props = new Properties();
        props.setProperty(Starter.ARG_GROUP_NAME, "InteractionTest");
        props.setProperty(Starter.ARG_SERVICE_NAME, "Incoming");
        props.setProperty(Starter.ARG_SERVICE_PATH, "com.noqms.tests.interaction.InteractionTest$MicroIncoming");
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
        public void processRequest(Long requestId, String serviceNameFrom, byte[] data) {
            sendResponse(requestId, null, null, null, null);
            Model model = gson.fromJson(new String(data, StandardCharsets.UTF_8), Model.class);
            data = gson.toJson(model).getBytes(StandardCharsets.UTF_8);
            String microServiceName = "MS#" + String.valueOf(1 + random.nextInt(model.microServices));
            sendRequestExpectResponse(microServiceName, data);
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
            if (text.contains("is not responsive: MS#1"))
                System.out.println("Expecting service is not responsive: " + text);
            else {
                System.err.println(text);
                sleepMillis(100);
                System.exit(-1); // end the test
            }
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
}
