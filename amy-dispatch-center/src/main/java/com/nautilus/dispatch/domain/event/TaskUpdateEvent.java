package com.nautilus.dispatch.domain.event;

import com.nautilus.dispatch.domain.entity.SysMediaTask;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 任务更新事件
 */
@Getter
public class TaskUpdateEvent extends ApplicationEvent {
    private final SysMediaTask sysMediaTask;

    public TaskUpdateEvent(Object source, SysMediaTask sysMediaTask) {
        super(source);
        this.sysMediaTask = sysMediaTask;
    }
}
