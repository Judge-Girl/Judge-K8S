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

package tw.waterball.judgegirl.springboot.submission.impl.deployer.k8s;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.BatchV1Api;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobBuilder;
import io.kubernetes.client.models.V1LocalObjectReference;
import tw.waterball.judgegirl.entities.problem.JudgeEnvSpec;
import tw.waterball.judgegirl.entities.problem.Problem;
import tw.waterball.judgegirl.entities.submission.Submission;
import tw.waterball.judgegirl.springboot.configs.properties.JudgeGirlAmqpProps;
import tw.waterball.judgegirl.springboot.configs.properties.JudgeGirlJudgerProps;
import tw.waterball.judgegirl.springboot.configs.properties.ServiceProps;
import tw.waterball.judgegirl.springboot.submission.impl.deployer.JudgerEnvTemplates;
import tw.waterball.judgegirl.submissionservice.ports.JudgerDeployer;

import java.util.Collection;
import java.util.LinkedList;

import static java.lang.String.format;
import static java.lang.String.valueOf;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
public class K8SJudgerDeployer implements JudgerDeployer {
    private BatchV1Api api;
    private ServiceProps.ProblemService problemServiceInstance;
    private ServiceProps.SubmissionService submissionServiceInstance;
    private JudgeGirlAmqpProps amqpProps;
    private JudgeGirlJudgerProps judgerProps;

    public K8SJudgerDeployer(BatchV1Api k8sApi, ServiceProps.ProblemService problemServiceInstance,
                             ServiceProps.SubmissionService submissionServiceInstance,
                             JudgeGirlAmqpProps amqpProps, JudgeGirlJudgerProps judgerProps) {
        this.api = k8sApi;
        this.problemServiceInstance = problemServiceInstance;
        this.submissionServiceInstance = submissionServiceInstance;
        this.amqpProps = amqpProps;
        this.judgerProps = judgerProps;
    }


    @Override
    public void deployJudger(Problem problem, int studentId, Submission submission) {
        try {
            this.api.createNamespacedJob("Judge-Girl",
                    createJob(problem, studentId, submission), null, null, null);
        } catch (ApiException e) {
            throw new RuntimeException(e); // TODO
        }
    }

    private V1Job createJob(Problem problem, int studentId, Submission submission) {
        JudgeEnvSpec judgeEnvSpec = problem.getJudgeEnvSpec();

        Collection<V1EnvVar> v1EnvVars = prepareJudgerJobEnvVars(problem, studentId, submission);

        // @formatter:off
        return new V1JobBuilder()
                .withKind("Job")
                    .withNewMetadata()
                        .withName(format(judgerProps.getJob().getNameFormat(), submission.getId()))
                    .endMetadata()
                .withNewSpec()
                    .withTtlSecondsAfterFinished(50)
                    .withNewTemplate()
                        .withNewSpec()
                            // pull from the private docker registry
                            // the secret must be created following the instructions: https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/
                            .addToImagePullSecrets(new V1LocalObjectReference().name(judgerProps.getKubernetes().getImagePullSecret()))
                                .addNewContainer()
                                    .withName(format(judgerProps.getContainer().getNameFormat(), submission.getId()))
                                    .withImage(judgerProps.getImage().getName())
                                    .withNewResources()
                                        .addToRequests("cpu", new Quantity(valueOf(judgeEnvSpec.getCpu())))
                                        .addToLimits("nvidia.com/gpu", new Quantity(valueOf(judgeEnvSpec.getGpu())))
                                    .endResources()
                                    .addAllToEnv(v1EnvVars)
                                .endContainer()
                            .withRestartPolicy("Never")
                        .endSpec()
                    .endTemplate()
                    .withBackoffLimit(4)
                .endSpec()
                .build();
        // @formatter:on
    }

    private Collection<V1EnvVar> prepareJudgerJobEnvVars(Problem problem, int studentId, Submission submission) {
        Collection<V1EnvVar> v1EnvVars = new LinkedList<>();
        JudgerEnvTemplates.applyEnvironmentVariables(
                (env, value) -> v1EnvVars.add(new V1EnvVar().name(env).value(value)),
                studentId, problem.getId(), submission.getId(),
                problemServiceInstance, submissionServiceInstance, amqpProps, judgerProps
        );
        return v1EnvVars;
    }
}


