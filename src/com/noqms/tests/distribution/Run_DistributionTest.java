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

package com.noqms.tests.distribution;

// Compile the tests, including this one, into a jar file and put the jar file in the same directory as noqms jar and gson jar.
// cd to that directory and execute the following command:
// java -server -cp xCPx com.noqms.tests.interaction.Run_InteractionTest
//    (xCPx is *:. for Linux and *;. for Windows)

// This test can also be directly run from an IDE.

public class Run_DistributionTest {
    public static void main(String[] args) {
        int microServiceInstances = 10;
        int threadsPerMicroService = 1;
        int messages = 100;

        DistributionTest test = new DistributionTest(microServiceInstances, threadsPerMicroService, messages);
        try {
            test.run();
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
    }
}
