package com.physica;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PhysicaConsole {
    private final List<String> logs = new ArrayList<>();
    private final long startTime;

    public PhysicaConsole() {
        this.startTime = System.currentTimeMillis();
    }

    public void log(String message) {
        long elapsed = System.currentTimeMillis() - startTime;
        long hours = (elapsed / 3600000);
        long minutes = (elapsed / 60000) % 60;
        long seconds = (elapsed / 1000) % 60;
        long millis = elapsed % 1000;
        
        String timestamp = String.format("[%02d:%02d:%02d.%03d]", hours, minutes, seconds, millis);
        String formattedMessage = timestamp + " " + message;
        logs.add(formattedMessage);
        
        // Also print to standard output for debugging
        System.out.println(formattedMessage);
    }

    public List<String> getLogs() {
        return Collections.unmodifiableList(logs);
    }
}
