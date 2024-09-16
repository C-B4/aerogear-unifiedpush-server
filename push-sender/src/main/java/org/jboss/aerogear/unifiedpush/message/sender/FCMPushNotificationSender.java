/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.unifiedpush.message.sender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.jboss.aerogear.unifiedpush.api.AndroidVariant;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.api.VariantType;
import org.jboss.aerogear.unifiedpush.message.InternalUnifiedPushMessage;
import org.jboss.aerogear.unifiedpush.message.Priority;
import org.jboss.aerogear.unifiedpush.message.UnifiedPushMessage;
import org.jboss.aerogear.unifiedpush.message.apns.APNs;
import org.jboss.aerogear.unifiedpush.message.token.TokenLoaderUtils;
import org.jboss.aerogear.unifiedpush.service.ClientInstallationAsyncService;
import org.jboss.aerogear.unifiedpush.system.ConfigurationEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Message.Builder;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
@Service
@Qualifier(value = VariantType.ANDROIDQ)
public class FCMPushNotificationSender implements PushNotificationSender {
    @Inject
    private ClientInstallationAsyncService clientInstallationAsyncService;
    private FirebaseApp app;
    private FirebaseMessaging firebaseMessaging;
    @PostConstruct
    public void init() {
        FirebaseOptions options;
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            String databaseUrl = new StringBuilder().append(ConfigurationEnvironment.HTTPS_PROTOCOL)
                    .append(ConfigurationEnvironment.PROJECT_ID)
                    .append(".firebaseio.com")
                    .toString();

            options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(ConfigurationEnvironment.PROJECT_ID) // Set your Firebase project ID here
                    .setDatabaseUrl(databaseUrl)
                    .build();
        } catch (IOException e) {
            logger.error("FCMPushNotificationSender init stage, failed due to e={}", e.getMessage());
            throw new RuntimeException(e);
        }

        // Initialize FirebaseApp with the FirebaseOptions
        this.app = FirebaseApp.initializeApp(options, ConfigurationEnvironment.PROJECT_ID);
        this.firebaseMessaging = FirebaseMessaging.getInstance(this.app);
        logger.info("FCMPushNotificationSender init stage, FirebaseApp={}", this.app);
    }

    private final Logger logger = LoggerFactory.getLogger(FCMPushNotificationSender.class);

    /**
     * Sends FCM notifications ({@link UnifiedPushMessage}) to all devices, that are represented by
     * the {@link List} of tokens for the given {@link AndroidVariant}.
     */
    @Override
    public void sendPushMessage(Variant variant, Collection<String> tokens, UnifiedPushMessage pushMessage, String pushMessageInformationId, NotificationSenderCallback callback) {
        // no need to send empty list
        if (tokens.isEmpty()) {
            logger.info("sendPushMessage, token is empty push will not be sent");
            return;
        }

        final List<String> pushTargets = new ArrayList<>(tokens);
        org.jboss.aerogear.unifiedpush.message.Message message = pushMessage.getMessage();
        final APNs apns = message.getApns();
        final String msgTitle = (apns != null) ? apns.getTitle() : "";
        Notification notification = Notification.builder()
                .setBody(message.getAlert())
                .setTitle(msgTitle)
                .build();

           /*
        The Message defaults to a Normal priority.  High priority is used
        by FCM to wake up devices in Doze mode as well as apps in AppStandby
        mode.  This has no effect on devices older than Android 6.0
        */
        AndroidConfig.Priority priority = message.getPriority() ==  Priority.HIGH ?
                AndroidConfig.Priority.HIGH :
                AndroidConfig.Priority.NORMAL;

        AndroidConfig.Builder androidConfig = AndroidConfig.builder()
                .setPriority(priority);

        // if present, apply the time-to-live metadata:
        int ttl = pushMessage.getConfig().getTimeToLive();
        if (ttl != -1) {
            androidConfig.setTtl(ttl);
        }

        final AndroidVariant androidVariant = (AndroidVariant) variant;
        // push targets can be registration IDs OR topics (starting /topic/), but they can't be mixed.
        if (pushTargets.get(0).startsWith(TokenLoaderUtils.TOPIC_PREFIX)) {
            logger.debug("sendPushMessage, start preparing and send message with topic");
            Builder firebaseRegularMessageBuilder = prepareFireBaseRegularMessage(message, notification, androidConfig, pushMessageInformationId);
            processFirebaseRegularMessage(pushTargets, firebaseRegularMessageBuilder, callback);
            return;
        }

        logger.debug("sendPushMessage, start preparing and send multicast message");
        MulticastMessage.Builder multicastBuilder = prepareFireBaseMulticastMessage(message, notification, androidConfig, pushMessageInformationId);
        processFirebaseMultiCastMessage(pushTargets, multicastBuilder, callback, androidVariant);
    }

    private Builder prepareFireBaseRegularMessage(org.jboss.aerogear.unifiedpush.message.Message message, Notification notification, AndroidConfig.Builder androidConfig, String pushMessageInformationId) {
        // payload builder:
        Builder firebaseSingleMessageBuilder = Message.builder();

        // add the "recognized" keys...
        firebaseSingleMessageBuilder.putData("alert", message.getAlert());
        firebaseSingleMessageBuilder.putData("sound", message.getSound());
        firebaseSingleMessageBuilder.putData("badge", String.valueOf(message.getBadge()));
        firebaseSingleMessageBuilder.setNotification(notification);


        firebaseSingleMessageBuilder.setAndroidConfig(androidConfig.build());

        // iterate over the missing keys:
        message.getUserData().keySet()
                .forEach(key -> firebaseSingleMessageBuilder.putData(key, String.valueOf(message.getUserData().get(key))));

        //add the aerogear-push-id
        firebaseSingleMessageBuilder.putData(InternalUnifiedPushMessage.PUSH_MESSAGE_ID, pushMessageInformationId);
        return firebaseSingleMessageBuilder;
    }
    private MulticastMessage.Builder prepareFireBaseMulticastMessage(org.jboss.aerogear.unifiedpush.message.Message message, Notification notification, AndroidConfig.Builder androidConfig, String pushMessageInformationId) {
        // payload builder:
        MulticastMessage.Builder multicastBuilder = MulticastMessage.builder();

        // add the "recognized" keys...
        multicastBuilder.putData("alert", message.getAlert());
        multicastBuilder.putData("sound", message.getSound());
        multicastBuilder.putData("badge", String.valueOf(message.getBadge()));
        multicastBuilder.setNotification(notification);


        multicastBuilder.setAndroidConfig(androidConfig.build());

        // iterate over the missing keys:
        message.getUserData().keySet()
                .forEach(key -> multicastBuilder.putData(key, String.valueOf(message.getUserData().get(key))));

        //add the aerogear-push-id
        multicastBuilder.putData(InternalUnifiedPushMessage.PUSH_MESSAGE_ID, pushMessageInformationId);
        return multicastBuilder;

    }


    /**
     * Process the HTTP POST to the FCM infrastructure for the given list of registrationIDs.
     */
    private void processFirebaseRegularMessage(List<String> pushTargets, Builder firebaseMessageBuilder, NotificationSenderCallback callback) {
        try {
            for (String topic : pushTargets) {
                logger.info(String.format("Sent push notification to FCM topic: %s", topic));
                firebaseMessageBuilder.setTopic(topic);
                String result = firebaseMessaging.send(firebaseMessageBuilder.build());
                logger.info("processFirebaseSingleMessage, Response from FCM topic request={}", result);
            }
            logger.debug("processFirebaseSingleMessage, Message batch to FCM has been submitted");
            callback.onSuccess();
        } catch (Exception e) {
            callback.onError(String.format("Error sending payload to FCM server: %s", e.getMessage()));
        }
    }

    private void processFirebaseMultiCastMessage(List<String> pushTargets, MulticastMessage.Builder firebaseMulticastMessage, NotificationSenderCallback callback, AndroidVariant androidVariant) {
        try {
            logger.info(String.format("Sent push notification to FCM Server for %d registrationIDs", pushTargets.size()));
            firebaseMulticastMessage.addAllTokens(pushTargets);
            BatchResponse multicastResult = firebaseMessaging.sendEachForMulticast(firebaseMulticastMessage.build());
            // after sending, let's identify the inactive/invalid registrationIDs and trigger their deletion:
            handleMulticastResponses(multicastResult, pushTargets, androidVariant);
            callback.onSuccess();
        } catch (Exception e) {
            callback.onError(String.format("Error sending payload to FCM server: %s", e.getMessage()));
        }
    }


    private void handleMulticastResponses(BatchResponse multicastResult, List<String> registrationIDs, AndroidVariant androidVariant) {
        // get the FCM send responses for all the client devices:
        final List<SendResponse> responses = multicastResult.getResponses();

        // read the responses:
        int i = 0;
        Set<String> inactiveTokens = new HashSet<>();
        for (SendResponse response : responses) {
            // use the current index to access the individual responses
            FirebaseMessagingException exception = response.getException();
            if (exception != null) {
                MessagingErrorCode messagingErrorCode = exception.getMessagingErrorCode();
                if (messagingErrorCode != null) {
                    // See CAPP-27286 As part of migration to HTTP V1 api of Firebase, we didn't want to fail the process if we get error for one of the users.
                    // TODO maybe for the future it will be nice to notify it
                    String currentToken = registrationIDs.get(i);
                    inactiveTokens.add(currentToken);
                    logger.error("handleMulticastResponses, processing {} error code from FCM response, for registration ID: {}", messagingErrorCode, currentToken);
                }
            }
            i++;
        }

        String variantId = androidVariant.getVariantID();
        if (!inactiveTokens.isEmpty()) {
            // trigger asynchronous deletion:
            logger.info(String.format("handleMulticastResponses, Based on FCM response data and error codes, deleting %d invalid or duplicated Android installations", inactiveTokens.size()));
            clientInstallationAsyncService.removeInstallationsForVariantByDeviceTokens(variantId, inactiveTokens);
        }
    }
}
