package com.example.taskmanager.application.notifications;

import com.example.taskmanager.domain.tasks.Task;

/**
 * Sends notifications about a task through the available channels.
 */
public interface NotificationService {

    /**
     * Sends an email notification about the task. No other channel is used.
     * A delivery failure from the email channel reaches the caller unchanged.
     *
     * @param task the task to notify about; must not be null
     * @throws IllegalArgumentException if task is null, in which case nothing is sent
     */
    void sendEmail(Task task);

    /**
     * Sends an SMS notification about the task. No other channel is used.
     * A delivery failure from the SMS channel reaches the caller unchanged.
     *
     * @param task the task to notify about; must not be null
     * @throws IllegalArgumentException if task is null, in which case nothing is sent
     */
    void sendSms(Task task);

    /**
     * Sends a push notification about the task. No other channel is used.
     * A delivery failure from the push channel reaches the caller unchanged.
     *
     * @param task the task to notify about; must not be null
     * @throws IllegalArgumentException if task is null, in which case nothing is sent
     */
    void sendPush(Task task);

    /**
     * Sends an email notification and then an SMS notification about the task. No push notification is sent.
     * The two sends are not atomic: if the email fails, its exception reaches the caller and no SMS is sent;
     * if the SMS fails, the email has already gone out.
     *
     * @param task the task to notify about; must not be null
     * @throws IllegalArgumentException if task is null, in which case nothing is sent
     */
    void sendBoth(Task task);
}
