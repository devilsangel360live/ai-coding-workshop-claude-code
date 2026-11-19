package com.example.taskmanager.application.notifications;

import com.example.taskmanager.domain.tasks.Task;

/**
 * Delivers task notifications through the email and SMS ports.
 */
public class DefaultNotificationService implements NotificationService {

    private final EmailSender emailSender;
    private final SmsSender smsSender;

    public DefaultNotificationService(EmailSender emailSender, SmsSender smsSender) {
        this.emailSender = emailSender;
        this.smsSender = smsSender;
    }

    @Override
    public void sendEmail(Task task) {
        requireTask(task);
        emailSender.send(task);
    }

    @Override
    public void sendSms(Task task) {
        requireTask(task);
        smsSender.send(task);
    }

    @Override
    public void sendBoth(Task task) {
        requireTask(task);
        emailSender.send(task);
        smsSender.send(task);
    }

    private static void requireTask(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("Task must not be null");
        }
    }
}
