package com.example.disclosurereview.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class TaskNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    public String next() {
        int suffix = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return "REV-" + LocalDate.now().format(DAY) + "-" + String.format("%06d", suffix);
    }
}
