package io.github.tontu89.debugclientagent;

@FunctionalInterface
public interface ServerResponseConsumer<T> {
    void action(T t);
}
