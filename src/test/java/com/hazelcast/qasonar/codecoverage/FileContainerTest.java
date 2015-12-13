package com.hazelcast.qasonar.codecoverage;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileContainerTest {

    private FileContainer fileContainer;

    @Before
    public void setUp() {
        fileContainer = new FileContainer();
        fileContainer.fileName = "FileContainerTest.java";
    }

    @Test
    public void testCodeCoverageCalculationState() {
        assertFalse(fileContainer.isForCoverageCalculation());

        fileContainer.useForCoverageCalculation(FileContainer.CoverageType.NONE);

        assertTrue(fileContainer.isForCoverageCalculation());
    }

    @Test
    public void testGetCodeCoverageForCalculation_withSonarCoverage() {
        fileContainer.numericCoverage = 23.0;
        fileContainer.ideaCoverage = 42.0;

        fileContainer.useForCoverageCalculation(FileContainer.CoverageType.SONAR);
        double coverage = fileContainer.getCoverageForCalculation();

        assertEquals(23.0, coverage, 0.0001);
    }

    @Test
    public void testGetCodeCoverageForCalculation_withIdeaCoverage() {
        fileContainer.numericCoverage = 23.0;
        fileContainer.ideaCoverage = 42.0;

        fileContainer.useForCoverageCalculation(FileContainer.CoverageType.IDEA);
        double coverage = fileContainer.getCoverageForCalculation();

        assertEquals(42.0, coverage, 0.0001);
    }

    @Test(expected = IllegalStateException.class)
    public void testGetCodeCoverageForCalculation_notSet() {
        fileContainer.getCoverageForCalculation();
    }

    @Test
    public void testPass() {
        assertFalse(fileContainer.isQaCheckSet());

        fileContainer.pass();

        assertTrue(fileContainer.isQaCheckSet());
        assertTrue(fileContainer.qaCheck);
    }

    @Test
    public void testPass_withComment() {
        assertFalse(fileContainer.isQaCheckSet());

        fileContainer.pass("cross project");

        assertTrue(fileContainer.isQaCheckSet());
        assertTrue(fileContainer.qaCheck);
        assertEquals("cross project", fileContainer.comment);
    }

    @Test
    public void testFail() {
        assertFalse(fileContainer.isQaCheckSet());

        fileContainer.fail("cross project");

        assertTrue(fileContainer.isQaCheckSet());
        assertFalse(fileContainer.qaCheck);
        assertEquals("cross project", fileContainer.comment);
    }

    @Test(expected = IllegalStateException.class)
    public void testSetStatusTwice() throws Exception {
        fileContainer.pass();
        fileContainer.pass();
    }

    @Test
    public void testAddComment() throws Exception {
        fileContainer.comment = "test";

        fileContainer.pass("cross module");

        assertEquals("cross module\ntest", fileContainer.comment);
    }
}
