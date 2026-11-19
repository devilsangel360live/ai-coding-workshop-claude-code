package com.example.taskmanager.infrastructure.persistence.mappers;

import com.example.taskmanager.domain.tasks.Priority;
import com.example.taskmanager.domain.tasks.Task;
import com.example.taskmanager.domain.tasks.TaskId;
import com.example.taskmanager.domain.tasks.TaskStatus;
import com.example.taskmanager.infrastructure.persistence.entities.TaskEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mapper to convert between domain Task and infrastructure TaskEntity.
 * 
 * Maps domain model to/from persistence model (JPA entities).
 * This is the anti-corruption layer between domain and infrastructure.
 */
@Component
public final class TaskMapper {
    
    /**
     * Convert domain Task to JPA TaskEntity.
     * 
     * @param task the domain task
     * @return the JPA entity
     */
    public TaskEntity toEntity(Task task) {
        if (task == null) {
            return null;
        }
        
        TaskEntity entity = new TaskEntity();
        entity.setId(task.getId().value());
        entity.setTitle(task.getTitle());
        entity.setDescription(task.getDescription());
        entity.setStatus(task.getStatus().name());
        entity.setPriority(task.getPriority().name());
        entity.setDueDate(task.getDueDate());
        entity.setCreatedAt(task.getCreatedAt());
        entity.setCompletedAt(task.getCompletedAt());
        
        return entity;
    }
    
    /**
     * Convert JPA TaskEntity to domain Task.
     * Uses Task.reconstitute() to rebuild domain object with all state.
     * 
     * @param entity the JPA entity
     * @return the domain task
     */
    public Task toDomain(TaskEntity entity) {
        if (entity == null) {
            return null;
        }
        
        TaskId taskId = TaskId.of(entity.getId());
        TaskStatus status = TaskStatus.valueOf(entity.getStatus());
        // Rows saved before priorities existed have none; the domain defaults them to MEDIUM
        Priority priority = entity.getPriority() == null ? null : Priority.fromString(entity.getPriority());

        return Task.reconstitute(
            taskId,
            entity.getTitle(),
            entity.getDescription(),
            status,
            priority,
            entity.getDueDate(),
            entity.getCreatedAt(),
            entity.getCompletedAt()
        );
    }
}
