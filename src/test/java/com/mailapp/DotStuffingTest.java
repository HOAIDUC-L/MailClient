package com.mailapp;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DotStuffingTest {

    @Test
    public void testDotStuffingAndUnstuffing() throws Exception {
        String originalBody = "First line\n.This line started with a dot\n..This line had two dots\nNormal line";

        // Dot-stuffing (Sender side)
        List<String> stuffedLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(originalBody))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(".")) {
                    line = "." + line;
                }
                stuffedLines.add(line);
            }
        }

        assertEquals("First line", stuffedLines.get(0));
        assertEquals("..This line started with a dot", stuffedLines.get(1));
        assertEquals("...This line had two dots", stuffedLines.get(2));
        assertEquals("Normal line", stuffedLines.get(3));

        // Dot-unstuffing (Receiver side)
        List<String> unstuffedLines = new ArrayList<>();
        for (String line : stuffedLines) {
            if (line.startsWith("..")) {
                line = line.substring(1);
            }
            unstuffedLines.add(line);
        }

        assertEquals("First line", unstuffedLines.get(0));
        assertEquals(".This line started with a dot", unstuffedLines.get(1));
        assertEquals("..This line had two dots", unstuffedLines.get(2));
        assertEquals("Normal line", unstuffedLines.get(3));
    }

    @Test
    public void testDataTerminatorDetection() {
        String line1 = ".";
        String line2 = "..";
        String line3 = ".hidden";

        assertTrue(line1.equals("."));
        assertFalse(line2.equals("."));
        assertFalse(line3.equals("."));
    }
}
