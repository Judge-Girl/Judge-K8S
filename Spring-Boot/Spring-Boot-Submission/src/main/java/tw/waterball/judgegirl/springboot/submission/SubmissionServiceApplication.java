/*
 *  Copyright 2020 Johnny850807 (Waterball) 潘冠辰
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package tw.waterball.judgegirl.springboot.submission;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import tw.waterball.judgegirl.submissionservice.ports.SubmissionMessageQueue;

@ComponentScan(basePackageClasses = {
        tw.waterball.judgegirl.springboot.ScanRoot.class,
        tw.waterball.judgegirl.submissionservice.ScanRoot.class})
@SpringBootApplication
public class SubmissionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubmissionServiceApplication.class, args);
    }

    @Bean
    public ApplicationRunner applicationRunner(SubmissionMessageQueue submissionMessageQueue) {
        return (argv) -> {
            submissionMessageQueue.startListening();
        };
    }
}
