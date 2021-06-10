package xyz.sathro.factory.event.annotations;

import xyz.sathro.factory.event.listeners.ListenerPriority;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubscribeEvent {
	ListenerPriority priority() default ListenerPriority.NORMAL;
}
