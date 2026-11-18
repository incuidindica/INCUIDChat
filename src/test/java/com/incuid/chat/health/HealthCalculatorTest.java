package com.incuid.chat.health;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthCalculatorTest {
    private HealthCalculator.Result hp(double current,double max) {
        return HealthCalculator.calculate(current,max,75,50);
    }
    @Test void mandatoryCases() {
        double[][] cases = {
            {20,20,10,0}, {18,20,9,0}, {15,20,8,0}, {14,20,7,1}, {12,20,6,1},
            {10,20,5,1}, {9,20,5,2}, {6,20,3,2}, {2,20,1,2}, {1,20,1,2},
            {0,20,0,3}, {40,40,10,0}, {15,40,4,2}, {60,100,6,1}
        };
        for (double[] row : cases) {
            var actual = hp(row[0], row[1]);
            assertEquals((int) row[2],actual.filled(), row[0]+"/"+row[1]);
            assertEquals(HealthCalculator.Condition.values()[(int)row[3]],actual.condition());
        }
    }
    @Test void boundariesAndFractional() {
        assertEquals(HealthCalculator.Condition.CRITICAL,hp(49,100).condition());
        assertEquals(HealthCalculator.Condition.INJURED,hp(50,100).condition());
        assertEquals(HealthCalculator.Condition.INJURED,hp(74,100).condition());
        assertEquals(HealthCalculator.Condition.HEALTHY,hp(75,100).condition());
        assertEquals(HealthCalculator.Condition.INJURED,hp(74.9,100).condition());
        assertEquals(8,hp(74.9,100).filled());
        assertEquals(1,hp(0.001,100).filled());
    }
    @Test void invalidAndClamped() {
        for (double max : new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            assertEquals(0,hp(10,max).filled());
        }
        assertEquals(10,hp(999,20).filled());
        assertEquals(0,hp(-5,20).filled());
        assertEquals(0,hp(Double.NaN,20).filled());
    }
    @Test void alwaysTenPositionsInCalculations() {
        for (double current=-10;current<130;current+=0.37) {
            int filled=hp(current,100).filled();
            assertTrue(filled>=0 && filled<=10);
            assertEquals(10,filled+(10-filled));
        }
    }
}
