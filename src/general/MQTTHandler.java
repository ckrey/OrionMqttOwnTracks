package general;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.internal.MemoryPersistence;

public class MQTTHandler implements MqttCallback {

    private MqttClient client;
    private boolean firstConnect;

    private String clientId;
    private String brokerURL;
    private String userName;
    private String password;
    private String willTopic;
    private byte[] will;
    private int willQos;
    private boolean willRetain;
    private int keepAlive;
    private boolean cleanSession;
    private String subscription;
    private int subscriptionQos;

    private MQTTHandler() {
    }

    public static MQTTHandler getInstance() {
        return MQTTHandlerHolder.INSTANCE;
    }

    private static class MQTTHandlerHolder {

        private static final MQTTHandler INSTANCE = new MQTTHandler();
    }

    public void init(String aClientId,
            String aBrokerURL,
            String aUserName,
            String aPassword,
            String aWillTopic,
            byte[] aWill,
            int aWillQos,
            boolean aWillRetain,
            int aKeepAlive,
            boolean aCleanSession,
            String aSubscription,
            int aSubscriptionQos) {
        clientId = aClientId;
        brokerURL = aBrokerURL;
        userName = aUserName;
        password = aPassword;
        willTopic = aWillTopic;
        will = aWill;
        willQos = aWillQos;
        willRetain = aWillRetain;
        keepAlive = aKeepAlive;
        cleanSession = aCleanSession;
        subscription = aSubscription;
        subscriptionQos = aSubscriptionQos;
        firstConnect = true;

        if (client != null) {
            disconnect();
            client = null;
        }
    }

    public synchronized void connectToBroker() {
        SLog.log(SLog.Debug, "MQTTHandler", "connectToBroker " + brokerURL
                + " as " + clientId
                + "(c" + (cleanSession ? "1" : "0")
                + " k" + keepAlive
                + " u" + ((userName == null) ? "<null>" : userName) + ")");
        if (client == null) {
            try {
                client = new MqttClient(brokerURL, clientId, new MemoryPersistence());
                client.setCallback(this);
            } catch (MqttException e) {
                SLog.log(SLog.Error, "MQTTHandler", "setCallback: " + e.getReasonCode());
            }
        }

        if (!client.isConnected()) {
            try {
                MqttConnectOptions options = new MqttConnectOptions();
                if (userName != null) {
                    options.setUserName(userName);
                }
                if (password != null) {
                    options.setPassword(password.toCharArray());
                }
                options.setCleanSession(cleanSession);
                options.setKeepAliveInterval(keepAlive);
                if (willTopic != null) {
                    options.setWill(client.getTopic(willTopic),
                            will, willQos, willRetain);
                }
                SLog.log(SLog.Debug, "MQTTHandler", "connect w/ options");

                client.connect(options);

                publishIfConnected(willTopic, willQos, willRetain, "1".getBytes());

                if (subscription != null) {
                    if (cleanSession || firstConnect) {
                        SLog.log(SLog.Debug, "MQTTHandler", "subscribe");

                        client.subscribe(subscription, subscriptionQos);
                        firstConnect = false;
                    }
                }
            } catch (MqttSecurityException e) {
                SLog.log(SLog.Error, "MQTTHandler", "Security connectToBroker: " + e.getReasonCode());
            } catch (MqttException e) {
                SLog.log(SLog.Warning, "MQTTHandler", "connectToBroker: " + e.getReasonCode());
            }
        }

    }

    public synchronized boolean publishIfConnected(String topicName,
            int qos,
            boolean retained,
            byte[] payload) {

        SLog.log(SLog.Debug, "MQTTHandler", "publishIfConnected");

        if (client.isConnected()) {
            MqttTopic topic = client.getTopic(topicName);
            MqttMessage message = new MqttMessage(payload);
            message.setQos(qos);
            message.setRetained(retained);
            MqttDeliveryToken token;

            try {
                SLog.log(SLog.Debug, "MQTTHandler", "publish " + StringFunc.toHexString(payload));
                token = topic.publish(message);
            } catch (MqttPersistenceException pe) {
                SLog.log(SLog.Warning, "MQTTHandler", "MqttPersistenceException " + pe.getReasonCode());
                return false;
            } catch (MqttException e) {
                SLog.log(SLog.Warning, "MQTTHandler", "MqttException " + e.getReasonCode());
                return false;
            }

            try {
                SLog.log(SLog.Debug, "MQTTHandler", "waitForCompletion");
                token.waitForCompletion();
                return true;
            } catch (MqttSecurityException se) {
                SLog.log(SLog.Warning, "MQTTHandler", "MqttSecurityException " + se.getReasonCode());
                return false;
            } catch (MqttException e) {
                SLog.log(SLog.Warning, "MQTTHandler", "MqttException " + e.getReasonCode());
                return false;
            }
        } else {
            return false;
        }
    }

    public synchronized boolean publish(String topicName,
            int qos,
            boolean retained,
            byte[] payload) {

        if (client == null && !client.isConnected()) {
            connectToBroker();
        }

        return publishIfConnected(topicName, qos, retained, payload);
    }

    public synchronized void subscribe(String topicName, int qos) {
        SLog.log(SLog.Debug, "MQTTHandler", "subscribe " + topicName + " " + qos);
        if (client != null && client.isConnected()) {
            try {
                client.subscribe(topicName, qos);
            } catch (MqttException e) {
                SLog.log(SLog.Warning, "MQTTHandler", "subscribe: " + e.getReasonCode());
            }
        } else {
            // not connected
        }
    }

    public synchronized void unsubscribe(String topicName) {
        SLog.log(SLog.Debug, "MQTTHandler", "unsubscribe " + topicName);
        if (client != null && client.isConnected()) {
            try {
                client.unsubscribe(topicName);
            } catch (MqttException e) {
                SLog.log(SLog.Warning, "MQTTHandler", "unsubscribe: " + e.getReasonCode());
            }
        } else {
            // not connected
        }
    }

    public synchronized void disconnect() {
        SLog.log(SLog.Debug, "MQTTHandler", "disconnect");

        if (client != null && client.isConnected()) {
            publish(willTopic, willQos, willRetain, "-1".getBytes());
        }

        try {
            client.disconnect(0);
        } catch (MqttException e) {
            SLog.log(SLog.Warning, "MQTTHandler", "disconnect: " + e.getReasonCode());
        }
    }

    public boolean isConnected() {
        if (client != null) {
            return client.isConnected();
        } else {
            return false;
        }
    }

    // Callbacks
    public void connectionLost(Throwable cause) {
        SLog.log(SLog.Warning, "MQTTHandler", "connectionLost");
    }

    public void messageArrived(MqttTopic topic, MqttMessage message)
            throws Exception {
        SLog.log(SLog.Debug, "MQTTHandler", "messageArrived " + topic.getName()
                + " q" + message.getQos()
                + " r" + (message.isRetained() ? "1" : "0")
                + "\r\n" + new String(message.getPayload()));

        final String proxy = "/proxy/";
        int proxyIndex = topic.getName().indexOf(proxy);

        if (proxyIndex == -1) {

            CommandProcessor commandProcessor = CommandProcessor.getInstance();
            String response;
            if (commandProcessor.execute(message.toString(), true)) {
                response = commandProcessor.message;
            } else {
                response = "NACK: " + commandProcessor.message;
            }

            if (response.length() > 0) {
                SLog.log(SLog.Informational, "MQTTHandler", "response(" + response.length() + ") " + response);
                String[] lines = StringFunc.split(response, "\r\n");
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].length() > 0) {
                        SocketGPRSThread.getInstance().put(topic.getName() + "/out", 0, false, lines[i].getBytes());
                    }
                }
            }

        } else {
            CommASC0Thread.getInstance().println(
                    topic.getName().substring(proxyIndex + proxy.length())
                    + " " + message.toString()
            );
        }
    }

    public void deliveryComplete(MqttDeliveryToken token) {
        SLog.log(SLog.Debug, "MQTTHandler", "deliveryComplete");
    }
}
