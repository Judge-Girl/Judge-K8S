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

package tw.waterball.judgegirl.springboot.submission.impl.deployer;

import tw.waterball.judgegirl.judgerapi.env.JudgerEnvVariables;
import tw.waterball.judgegirl.springboot.configs.properties.JudgeGirlAmqpProps;
import tw.waterball.judgegirl.springboot.configs.properties.JudgeGirlJudgerProps;
import tw.waterball.judgegirl.springboot.configs.properties.ServiceProps;

import static java.lang.String.format;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
public class JudgerEnvTemplates {

    public static void applyEnvironmentVariables(
            JudgerEnvVariables.Applier applier,
            int studentId, int problemId, String submissionId,
            ServiceProps.ProblemService problemServiceInstance,
            ServiceProps.SubmissionService submissionServiceInstance,
            JudgeGirlAmqpProps amqpProps,
            JudgeGirlJudgerProps judgerProps) {

        JudgerEnvVariables.apply(applier,
                JudgerEnvVariables.Values.builder()
                        .studentId(studentId)
                        .problemId(problemId)
                        .submissionId(submissionId)
                        .jwtToken(judgerProps.getJwtToken())
                        .problemServiceInstance(problemServiceInstance)
                        .submissionServiceInstance(submissionServiceInstance)
                        .amqpVirtualHost(amqpProps.getVirtualHost())
                        .amqpHost(amqpProps.getHost())
                        .amqpPort(amqpProps.getPort())
                        .amqpUserName(amqpProps.getUsername())
                        .amqpPassword(amqpProps.getPassword())
                        .submissionExchangeName(amqpProps.getSubmissionExchangeName())
                        .verdictIssuedRoutingKeyFormat(
                                format(amqpProps.getVerdictIssuedRoutingKeyFormat(), "*"))
                        .build());
    }
}
