package com.example.taskmanager.application.notifications;

import com.example.taskmanager.domain.tasks.Task;

public interface PushSender {

    void send(Task task);
}
