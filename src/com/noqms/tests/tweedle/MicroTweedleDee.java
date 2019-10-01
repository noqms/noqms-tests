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

package com.noqms.tests.tweedle;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.noqms.MicroService;
import com.noqms.RequestStatus;
import com.noqms.ResponseFuture;

public class MicroTweedleDee extends MicroService {
    private final Gson gson = new Gson();
    private final AtomicInteger counterGenerator = new AtomicInteger();

    public MicroTweedleDee() {
        SendThread thread = new SendThread();
        thread.start();
    }

    private class SendThread extends Thread {
        public SendThread() {
            setDaemon(true);
        }

        public void run() {
            while (true) {
                ModelRequest requestModel = new ModelRequest();
                requestModel.counter = counterGenerator.incrementAndGet();
                byte[] data = gson.toJson(requestModel).getBytes(StandardCharsets.UTF_8);

                ResponseFuture responseFuture = sendRequestExpectResponse("Tweedle Dum", data);
                if (responseFuture.getRequestStatus() == RequestStatus.Ok) {
                    ResponseFuture.Response response = responseFuture.await();
                    if (!response.timedOut) {
                        ModelResponse modelResponse = gson.fromJson(new String(response.data, StandardCharsets.UTF_8), ModelResponse.class);
                        System.out.println(modelResponse.counter);
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception ex) {
                }
            }
        }
    }

    @Override
    public void processRequest(Long requestId, String serviceNameFrom, byte[] data) {
        ModelRequest request = gson.fromJson(new String(data, StandardCharsets.UTF_8), ModelRequest.class);

        if (requestId != null) {
            ModelResponse response = new ModelResponse();
            response.counter = request.counter;
            sendResponse(requestId, null, null, null, gson.toJson(response).getBytes(StandardCharsets.UTF_8));
        }
    }
}
