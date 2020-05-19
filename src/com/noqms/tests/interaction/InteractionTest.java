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
import com.noqms.Runner;

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

        for (int ix = 1; ix <= microServices; ix++) {
            String microServiceName = "MS#" + String.valueOf(ix);
            startMicroTest(microServiceName, threadsPerMicroService, logListener);
        }

        // load up messages in the system and let them bounce around for a minute

        Model model = new Model();
        model.microServices = microServices;
        byte[] data = gson.toJson(model).getBytes(StandardCharsets.UTF_8);
        for (int ix = 0; ix < messages; ix += 10) {
            String microServiceName = "MS#" + String.valueOf(1 + random.nextInt(microServices));
            incoming.sendRequestExpectResponse(microServiceName, data);
            sleepMillis(1);
        }

        sleepMillis(TimeUnit.MINUTES.toMillis(1));
    }

    private static class Model {
        private int microServices;
    }

    private void startMicroTest(String name, int threads, LogListener logListener) throws Exception {
        Properties props = new Properties();
        props.setProperty(Runner.ARG_GROUP_NAME, "InteractionTest");
        props.setProperty(Runner.ARG_SERVICE_NAME, name);
        props.setProperty(Runner.ARG_SERVICE_PATH, "com.noqms.tests.interaction.InteractionTest$MicroTest");
        props.setProperty(Runner.ARG_THREADS, String.valueOf(threads));
        props.setProperty(Runner.ARG_TYPICAL_MILLIS, "10");
        props.setProperty(Runner.ARG_TIMEOUT_MILLIS, "100");
        props.setProperty(Runner.ARG_MAX_MESSAGE_IN_BYTES, "100");
        props.setProperty(Runner.ARG_MAX_MESSAGE_OUT_BYTES, "100");
        Runner.start(props, logListener);
    }

    private MicroService startIncoming(LogListener logListener) throws Exception {
        Properties props = new Properties();
        props.setProperty(Runner.ARG_GROUP_NAME, "InteractionTest");
        props.setProperty(Runner.ARG_SERVICE_NAME, "Incoming");
        props.setProperty(Runner.ARG_SERVICE_PATH, "com.noqms.tests.interaction.InteractionTest$MicroIncoming");
        props.setProperty(Runner.ARG_THREADS, "1");
        props.setProperty(Runner.ARG_TYPICAL_MILLIS, "10");
        props.setProperty(Runner.ARG_TIMEOUT_MILLIS, "100");
        props.setProperty(Runner.ARG_MAX_MESSAGE_IN_BYTES, "100");
        props.setProperty(Runner.ARG_MAX_MESSAGE_OUT_BYTES, "100");
        return Runner.start(props, logListener);
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
        public void logInfo(String text) {
            System.out.println(text);
        }

        @Override
        public void logWarn(String text) {
            System.exit(-1); // end the test
        }

        @Override
        public void logError(String text, Throwable th) {
            System.exit(-1); // end the test
        }

        @Override
        public void logFatal(String text, Throwable th) {
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
