package com.qwertie64982.betternavigation;

/**
 * Represents a simple direction consisting of an angle from where the user is currently pointing
 * and the distance to their next point
 * Used to simplify code so I can pack two related sets of data into one set
 */
public class SimpleDirection {
    private int clockAngle; // In which clock direction someone should turn to face
    private double distance; // In meters

    /**
     * Default value constructor - sets the default SimpleDirection to face forward and move 0m
     */
    public SimpleDirection() {
        clockAngle = 12;
        distance = 0;
    }

    /**
     * Explicit value constructor
     * @param clockAngle Which clock direction to face
     * @param distance How far to walk in that direction
     */
    public SimpleDirection(int clockAngle, double distance) {
        this.clockAngle = clockAngle;
        this.distance = distance;
    }

    /**
     * Clock angle getter
     * @return Clock angle to turn and face
     */
    public int getClockAngle() {
        return clockAngle;
    }

    /**
     * Distance getter
     * @return How far to walk in meters
     */
    public double getDistance() {
        return distance;
    }

    /**
     * toString override
     * @return SimpleDirection as a String (ex. "SimpleDirection{clockAngle=12, distance=0}")
     */
    @Override
    public String toString() {
        return "SimpleDirection{" +
                "clockAngle=" + clockAngle +
                ", distance=" + distance +
                '}';
    }
}
