/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.MQAdmin;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.config.RocketMQConfigUtils;
import org.apache.rocketmq.spring.config.RocketMQTransactionAnnotationProcessor;
import org.apache.rocketmq.spring.config.TransactionHandlerRegistry;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.util.Assert;

@Configuration // 标识是配置类
@EnableConfigurationProperties(RocketMQProperties.class) // 指定 RocketMQProperties 自动配置
@ConditionalOnClass({ MQAdmin.class, ObjectMapper.class }) // 要求有 MQAdmin、ObjectMapper 类
@ConditionalOnProperty(prefix = "rocketmq", value = "name-server") // 要求有 rocketmq 开头，且 name-server 的配置
@Import({ JacksonFallbackConfiguration.class, ListenerContainerConfiguration.class }) // 引入 JacksonFallbackConfiguration 和 ListenerContainerConfiguration 配置类
@AutoConfigureAfter(JacksonAutoConfiguration.class) // 在 JacksonAutoConfiguration 之后初始化
public class RocketMQAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DefaultMQProducer.class) // 不存在 DefaultMQProducer Bean 对象
    @ConditionalOnProperty(prefix = "rocketmq", value = {"name-server", "producer.group"}) // 要求有 rocketmq 开头，且 name-server、producer.group 的配置
    public DefaultMQProducer defaultMQProducer(RocketMQProperties rocketMQProperties) {
        // 校验配置
        RocketMQProperties.Producer producerConfig = rocketMQProperties.getProducer();
        String nameServer = rocketMQProperties.getNameServer();
        String groupName = producerConfig.getGroup();
        Assert.hasText(nameServer, "[rocketmq.name-server] must not be null");
        Assert.hasText(groupName, "[rocketmq.producer.group] must not be null");

        // 创建 DefaultMQProducer 对象
        DefaultMQProducer producer = new DefaultMQProducer(groupName);
        // 将 RocketMQProperties.Producer 配置，设置到 producer 中
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(producerConfig.getSendMessageTimeout());
        producer.setRetryTimesWhenSendFailed(producerConfig.getRetryTimesWhenSendFailed());
        producer.setRetryTimesWhenSendAsyncFailed(producerConfig.getRetryTimesWhenSendAsyncFailed());
        producer.setMaxMessageSize(producerConfig.getMaxMessageSize());
        producer.setCompressMsgBodyOverHowmuch(producerConfig.getCompressMessageBodyThreshold());
        producer.setRetryAnotherBrokerWhenNotStoreOK(producerConfig.isRetryNextServer());

        return producer;
    }

    @Bean(destroyMethod = "destroy") // 声明了销毁时，调用 destroy 方法
    @ConditionalOnBean(DefaultMQProducer.class) // 有 DefaultMQProducer Bean 的情况下
    @ConditionalOnMissingBean(RocketMQTemplate.class) // 不存在 RocketMQTemplate Bean 对象
    public RocketMQTemplate rocketMQTemplate(DefaultMQProducer mqProducer, ObjectMapper rocketMQMessageObjectMapper) {
        // 创建 RocketMQTemplate 对象
        RocketMQTemplate rocketMQTemplate = new RocketMQTemplate();
        // 设置其属性
        rocketMQTemplate.setProducer(mqProducer);
        rocketMQTemplate.setObjectMapper(rocketMQMessageObjectMapper);
        return rocketMQTemplate;
    }

    @Bean
    @ConditionalOnBean(RocketMQTemplate.class) // 有 RocketMQTemplate Bean 的情况下
    @ConditionalOnMissingBean(TransactionHandlerRegistry.class) // 不存在 TransactionHandlerRegistry Bean 对象
    public TransactionHandlerRegistry transactionHandlerRegistry(RocketMQTemplate template) {
        // 创建 TransactionHandlerRegistry 对象
        return new TransactionHandlerRegistry(template);
    }

    @Bean(name = RocketMQConfigUtils.ROCKETMQ_TRANSACTION_ANNOTATION_PROCESSOR_BEAN_NAME) // Bean 的名字
    @ConditionalOnBean(TransactionHandlerRegistry.class) // 有 TransactionHandlerRegistry Bean 的情况下
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static RocketMQTransactionAnnotationProcessor transactionAnnotationProcessor(TransactionHandlerRegistry transactionHandlerRegistry) {
        // 创建 RocketMQTransactionAnnotationProcessor 对象
        return new RocketMQTransactionAnnotationProcessor(transactionHandlerRegistry);
    }

}
