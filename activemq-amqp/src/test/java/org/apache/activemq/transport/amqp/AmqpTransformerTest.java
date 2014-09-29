/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.amqp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.qpid.amqp_1_0.jms.impl.ConnectionFactoryImpl;
import org.apache.qpid.amqp_1_0.jms.impl.QueueImpl;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmqpTransformerTest {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpTransformerTest.class);

    private static final String AMQP_URL = "amqp://0.0.0.0:0%s";
    private BrokerService brokerService;
    private int amqpPort;
    private int openwirePort;
    private static final String TEST_QUEUE = "txqueue";

    @Test(timeout=60 * 1000)
    public void testNativeTransformation() throws Exception {

        // default is native
        startBrokerWithAmqpTransport(String.format(AMQP_URL, "?transport.transformer=native"));

        // send "text message" with AMQP JMS API
        Connection amqpConnection = createAmqpConnection();
        QueueImpl queue = new QueueImpl("queue://" + TEST_QUEUE);

        Session amqpSession = amqpConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer p = amqpSession.createProducer(queue);
        p.setPriority(7);

        TextMessage amqpMessage = amqpSession.createTextMessage();
        amqpMessage.setText("hello");
        p.send(amqpMessage);

        p.close();
        amqpSession.close();
        amqpConnection.close();

        // receive with openwire JMS
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://0.0.0.0:" + openwirePort);
        Connection openwireConn = factory.createConnection();
        openwireConn.start();
        Session session = openwireConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue jmsDest = session.createQueue(TEST_QUEUE);

        MessageConsumer c = session.createConsumer(jmsDest);

        Message message = c.receive(1000);

        assertTrue(message instanceof BytesMessage);
        Boolean nativeTransformationUsed = message.getBooleanProperty("JMS_AMQP_NATIVE");
        Long messageFormat = message.getLongProperty("JMS_AMQP_MESSAGE_FORMAT");
        assertEquals(0L, messageFormat.longValue());
        assertTrue("Didn't use the correct transformation, expected NATIVE", nativeTransformationUsed);
        assertEquals(2, message.getJMSDeliveryMode());
        assertEquals(7, message.getJMSPriority());

        c.close();
        session.close();
        openwireConn.close();
    }

    @Test(timeout = 30000)
    public void testRawTransformation() throws Exception {

        // default is native
        startBrokerWithAmqpTransport(String.format(AMQP_URL, "?transport.transformer=raw"));

        // send "text message" with AMQP JMS API
        Connection amqpConnection = createAmqpConnection();
        QueueImpl queue = new QueueImpl("queue://" + TEST_QUEUE);

        Session amqpSession = amqpConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer p = amqpSession.createProducer(queue);
        p.setPriority(7);

        TextMessage amqpMessage = amqpSession.createTextMessage();
        amqpMessage.setText("hello");
        p.send(amqpMessage);

        p.close();
        amqpSession.close();
        amqpConnection.close();

        // receive with openwire JMS
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://0.0.0.0:" + openwirePort);
        Connection openwireConn = factory.createConnection();
        openwireConn.start();
        Session session = openwireConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue jmsDest = session.createQueue(TEST_QUEUE);

        MessageConsumer c = session.createConsumer(jmsDest);

        Message message = c.receive(2000);

        assertNotNull("Should have received a message", message);
        LOG.info("Recieved message: {}", message);
        assertTrue(message instanceof BytesMessage);
        Boolean nativeTransformationUsed = message.getBooleanProperty("JMS_AMQP_NATIVE");
        Long messageFormat = message.getLongProperty("JMS_AMQP_MESSAGE_FORMAT");
        assertEquals(0L, messageFormat.longValue());
        assertTrue("Didn't use the correct transformation, expected NATIVE", nativeTransformationUsed);
        assertEquals(2, message.getJMSDeliveryMode());

        // should not equal 7 (should equal the default) because "raw" does not map
        // headers
        assertEquals(4, message.getJMSPriority());

        c.close();
        session.close();
        openwireConn.close();
    }

    @Test(timeout=60 * 1000)
    public void testJmsTransformation() throws Exception {

        // default is native
        startBrokerWithAmqpTransport(String.format(AMQP_URL, "?transport.transformer=jms"));

        // send "text message" with AMQP JMS API
        Connection amqpConnection = createAmqpConnection();
        QueueImpl queue = new QueueImpl("queue://" + TEST_QUEUE);

        Session amqpSession = amqpConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer p = amqpSession.createProducer(queue);

        TextMessage amqpMessage = amqpSession.createTextMessage();
        amqpMessage.setText("hello");
        p.send(amqpMessage);

        p.close();
        amqpSession.close();
        amqpConnection.close();

        // receive with openwire JMS
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://0.0.0.0:" + openwirePort);
        Connection openwireConn = factory.createConnection();
        openwireConn.start();
        Session session = openwireConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue jmsDest = session.createQueue(TEST_QUEUE);

        MessageConsumer c = session.createConsumer(jmsDest);

        Message message = c.receive(1000);

        assertTrue(message instanceof TextMessage);
        Boolean nativeTransformationUsed = message.getBooleanProperty("JMS_AMQP_NATIVE");
        Long messageFormat = message.getLongProperty("JMS_AMQP_MESSAGE_FORMAT");
        assertEquals(0L, messageFormat.longValue());
        assertFalse("Didn't use the correct transformation, expected NOT to be NATIVE", nativeTransformationUsed);
        assertEquals(2, message.getJMSDeliveryMode());

        c.close();
        session.close();
        openwireConn.close();
    }

    public Connection createAmqpConnection() throws JMSException {
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl("localhost", amqpPort, "admin", "password");
        final Connection connection = factory.createConnection();
        connection.setExceptionListener(new ExceptionListener() {
            @Override
            public void onException(JMSException exception) {
                exception.printStackTrace();
            }
        });
        connection.start();
        return connection;
    }

    public void startBrokerWithAmqpTransport(String amqpUrl) throws Exception {
        brokerService = new BrokerService();
        brokerService.setPersistent(false);
        brokerService.setAdvisorySupport(false);
        brokerService.setDeleteAllMessagesOnStartup(true);

        TransportConnector connector = brokerService.addConnector(amqpUrl);
        amqpPort = connector.getConnectUri().getPort();
        connector = brokerService.addConnector("tcp://0.0.0.0:0");
        openwirePort = connector.getConnectUri().getPort();

        brokerService.start();
        brokerService.waitUntilStarted();
    }

    @After
    public void stopBroker() throws Exception {
        if (brokerService != null) {
            brokerService.stop();
            brokerService = null;
        }
    }
}
