package com.example.taskmanager.application.notifications;

import com.example.taskmanager.domain.tasks.Task;

/**
 * Sends notifications about a task through the available channels.
 */
public interface NotificationService {

    /**
     * @throws IllegalArgumentException if task is null
     */
    void sendEmail(Task task);

    /**
     * @throws IllegalArgumentException if task is null
     */
    void sendSms(Task task);

    /**
     * @throws IllegalArgumentException if task is null
     */
    void sendPush(Task task);

    /**
     * Sends both an email and an SMS notification.
     *
     * @throws IllegalArgumentException if task is null
     */
    void sendBoth(Task task);
}
