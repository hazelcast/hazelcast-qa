package com.hazelcast.qasonar.outputMerge;

import com.hazelcast.qasonar.utils.PropertyReader;

public class OutputMerge {

    private final PropertyReader propertyReader;

    public OutputMerge(PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
    }

    public void run() {
        String outputFile = propertyReader.getOutputFile();
        System.out.println(outputFile);
    }
}
