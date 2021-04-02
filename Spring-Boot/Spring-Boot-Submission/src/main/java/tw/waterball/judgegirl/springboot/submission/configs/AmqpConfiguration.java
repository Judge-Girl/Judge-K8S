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

package tw.waterball.judgegirl.springboot.submission.configs;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tw.waterball.judgegirl.springboot.profiles.productions.Amqp;
import tw.waterball.judgegirl.submissionapi.clients.AmqpVerdictPublisher;
import tw.waterball.judgegirl.submissionapi.clients.VerdictPublisher;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Amqp
@Configuration
public class AmqpConfiguration {

    @Bean
    public VerdictPublisher verdictPublisher(AmqpAdmin amqpAdmin,
                                             AmqpTemplate amqpTemplate,
                                             @Value("${judge-girl.amqp.verdict-exchange-name}")
                                                     String submissionExchangeName,
                                             @Value("${judge-girl.amqp.verdict-issued-routing-key-format}")
                                                     String verdictIssuedRoutingKeyFormat) {
        return new AmqpVerdictPublisher(amqpAdmin, amqpTemplate,
                submissionExchangeName, verdictIssuedRoutingKeyFormat);
    }

    @Bean
    public Queue submissionServiceQueue(
            @Value("${judge-girl.amqp.submission-service-queue}")
                    String verdictIssuedEventQueueName) {
        return new Queue(verdictIssuedEventQueueName, true);
    }

    @Bean
    public TopicExchange verdictExchange(@Value("${judge-girl.amqp.verdict-exchange-name}") String verdictExchangeName) {
        return new TopicExchange(verdictExchangeName);
    }

    @Bean
    public Binding binding(@Value("${judge-girl.amqp.verdict-issued-routing-key-format}")
                                   String verdictIssuedRoutingKeyFormat,
                           @Qualifier("submissionServiceQueue") Queue queue,
                           @Qualifier("verdictExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(String.format(verdictIssuedRoutingKeyFormat, "*"));
    }
}
