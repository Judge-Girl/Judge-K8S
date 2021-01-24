/*
 * Copyright 2020 Johnny850807 (Waterball) 潘冠辰
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package tw.waterball.judgegirl.springboot.submission.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tw.waterball.judgegirl.commons.utils.NotifyWaitLock;
import tw.waterball.judgegirl.entities.submission.Verdict;
import tw.waterball.judgegirl.submissionapi.views.VerdictIssuedEvent;
import tw.waterball.judgegirl.submissionservice.domain.repositories.SubmissionRepository;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Component
public class VerdictIssuedEventHandler {
    private static final Logger logger = LogManager.getLogger(VerdictIssuedEventHandler.class);
    private ObjectMapper objectMapper;
    private SubmissionRepository submissionRepository;

    public VerdictIssuedEventHandler(ObjectMapper objectMapper,
                                     SubmissionRepository submissionRepository) {
        this.objectMapper = objectMapper;
        this.submissionRepository = submissionRepository;
    }

    // This is for test-purpose, test script can wait for this lock until the next onIssueVerdict()
    public final NotifyWaitLock onHandlingCompletion$ = new NotifyWaitLock();

    @RabbitListener(queues = "${judge-girl.amqp.verdict-issued-event-queue}")
    public void listen(@Payload String body) throws JsonProcessingException {
        VerdictIssuedEvent event = objectMapper.readValue(body, VerdictIssuedEvent.class);

        logger.info(event);
        Verdict verdict = new Verdict(event.getJudges(), event.getIssueTime());
        verdict.setReport(event.getReport().toEntity());
        verdict.setCompileErrorMessage(event.getCompileErrorMessage());
        submissionRepository.issueVerdictOfSubmission(event.getSubmissionId(), verdict);

        onHandlingCompletion$.doNotifyAll();
    }
}
