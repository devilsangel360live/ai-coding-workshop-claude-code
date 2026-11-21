package com.example.taskmanager.application.notifications;

import com.example.taskmanager.domain.tasks.Task;

public interface EmailSender {

    void send(Task task);
}
