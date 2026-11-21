package com.example.taskmanager.application.notifications;

import com.example.taskmanager.domain.tasks.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultNotificationService Unit Tests")
class DefaultNotificationServiceTest {

    @Mock
    private EmailSender emailSender;

    @Mock
    private SmsSender smsSender;

    @Mock
    private PushSender pushSender;

    @InjectMocks
    private DefaultNotificationService notificationService;

    @Nested
    @DisplayName("Send Email Operations")
    class SendEmailOperations {

        @Test
        void sendEmail_validTask_sendsEmailOnly() {
            // Arrange
            var task = Task.create("Review pull request");

            // Act
            notificationService.sendEmail(task);

            // Assert
            verify(emailSender, times(1)).send(task);
            verifyNoInteractions(smsSender);
        }

        @Test
        void sendEmail_nullTask_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> notificationService.sendEmail(null));
            verifyNoInteractions(emailSender, smsSender);
        }
    }

    @Nested
    @DisplayName("Send SMS Operations")
    class SendSmsOperations {

        @Test
        void sendSms_validTask_sendsSmsOnly() {
            // Arrange
            var task = Task.create("Review pull request");

            // Act
            notificationService.sendSms(task);

            // Assert
            verify(smsSender, times(1)).send(task);
            verifyNoInteractions(emailSender);
        }

        @Test
        void sendSms_nullTask_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> notificationService.sendSms(null));
            verifyNoInteractions(emailSender, smsSender);
        }
    }

    @Nested
    @DisplayName("Send Push Operations")
    class SendPushOperations {

        @Test
        void sendPush_validTask_sendsPushOnly() {
            // Arrange
            var task = Task.create("Review pull request");

            // Act
            notificationService.sendPush(task);

            // Assert
            verify(pushSender, times(1)).send(task);
            verifyNoInteractions(emailSender, smsSender);
        }

        @Test
        void sendPush_nullTask_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> notificationService.sendPush(null));
            verifyNoInteractions(emailSender, smsSender, pushSender);
        }
    }

    @Nested
    @DisplayName("Send Both Operations")
    class SendBothOperations {

        @Test
        void sendBoth_validTask_sendsEmailAndSms() {
            // Arrange
            var task = Task.create("Review pull request");

            // Act
            notificationService.sendBoth(task);

            // Assert
            verify(emailSender, times(1)).send(task);
            verify(smsSender, times(1)).send(task);
        }

        @Test
        void sendBoth_nullTask_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> notificationService.sendBoth(null));
            verifyNoInteractions(emailSender, smsSender);
        }

        @Test
        void sendBoth_emailSenderFails_propagatesException() {
            // Arrange
            var task = Task.create("Review pull request");
            var failure = new IllegalStateException("Email delivery failed");
            doThrow(failure).when(emailSender).send(task);

            // Act
            var thrown = assertThrows(IllegalStateException.class, () -> notificationService.sendBoth(task));

            // Assert
            assertSame(failure, thrown);
        }

        @Test
        void sendBoth_smsSenderFails_propagatesException() {
            // Arrange
            var task = Task.create("Review pull request");
            var failure = new IllegalStateException("SMS delivery failed");
            doThrow(failure).when(smsSender).send(task);

            // Act
            var thrown = assertThrows(IllegalStateException.class, () -> notificationService.sendBoth(task));

            // Assert
            assertSame(failure, thrown);
        }
    }
}
