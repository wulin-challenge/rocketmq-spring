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

package org.apache.rocketmq.spring.config;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RocketMQTransactionAnnotationProcessor implements BeanPostProcessor, Ordered, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(RocketMQTransactionAnnotationProcessor.class);

    private BeanFactory beanFactory;
    /**
     * 不处理的类的集合
     */
    private final Set<Class<?>> nonProcessedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

    private TransactionHandlerRegistry transactionHandlerRegistry;

    public RocketMQTransactionAnnotationProcessor(TransactionHandlerRegistry transactionHandlerRegistry) {
        this.transactionHandlerRegistry = transactionHandlerRegistry;
    }

    @Override // 实现自 BeanFactoryAware 接口
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override // 实现自 BeanPostProcessor 接口
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override // 实现自 BeanPostProcessor 接口
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 如果 nonProcessedClasses 不存在
        if (!this.nonProcessedClasses.contains(bean.getClass())) {
            // 获得 Bean 对应的 Class 类名。因为有可能被 AOP 代理过。
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            // 添加到 nonProcessedClasses 中，表示后面不处理。
            this.nonProcessedClasses.add(bean.getClass());
            // 获得 @RocketMQTransactionListener 注解
            RocketMQTransactionListener listener = AnnotationUtils.findAnnotation(targetClass, RocketMQTransactionListener.class);
            // 如果无注解，则不进行任何逻辑
            if (listener == null) { // for quick search
                log.trace("No @RocketMQTransactionListener annotations found on bean type: {}", bean.getClass());
            } else {
                // 如果有注解，则注册到 TransactionHandlerRegistry 中
                try {
                    processTransactionListenerAnnotation(listener, bean);
                } catch (MQClientException e) {
                    log.error("Failed to process annotation " + listener, e);
                    throw new BeanCreationException("Failed to process annotation " + listener, e);
                }
            }
        }

        return bean;
    }

    private void processTransactionListenerAnnotation(RocketMQTransactionListener listener, Object bean) throws MQClientException {
        // 校验 @RocketMQTransactionListener 非空
        if (transactionHandlerRegistry == null) {
            throw new MQClientException("Bad usage of @RocketMQTransactionListener, " + "the class must work with RocketMQTemplate", null);
        }
        // 如果未实现 RocketMQLocalTransactionListener 接口，直接抛出 IllegalStateException 异常。
        if (!RocketMQLocalTransactionListener.class.isAssignableFrom(bean.getClass())) {
            throw new MQClientException("Bad usage of @RocketMQTransactionListener, " + "the class must implement interface RocketMQLocalTransactionListener", null);
        }

        // 将 @RocketMQTransactionListener 注解，创建成 TransactionHandler 对象
        TransactionHandler transactionHandler = new TransactionHandler();
        transactionHandler.setBeanFactory(this.beanFactory);
        transactionHandler.setName(listener.txProducerGroup());
        transactionHandler.setBeanName(bean.getClass().getName());
        transactionHandler.setListener((RocketMQLocalTransactionListener) bean);
        transactionHandler.setCheckExecutor(listener.corePoolSize(), listener.maximumPoolSize(), listener.keepAliveTime(), listener.blockingQueueSize());

        // 注册 TransactionHandler 到 transactionHandlerRegistry 中
        transactionHandlerRegistry.registerTransactionHandler(transactionHandler);
    }

    @Override // 实现自 Ordered 接口
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

}
