package com.blueprinthell.model;

import java.awt.*;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;


public final class WirePath implements Serializable {
    private static final long serialVersionUID = 10L;

    public static final int MAX_BENDS = 3;

    private final List<Point> controlPoints;

    public WirePath(List<Point> points) {
        if (points == null || points.size() < 2)
            throw new IllegalArgumentException("Path must contain at least src & dst");
        if (points.size() > MAX_BENDS + 2)
            throw new IllegalArgumentException("Exceeded max bends: " + MAX_BENDS);
        this.controlPoints = List.copyOf(points);
    }

    public WirePath(Point src, Point dst) {
        this(List.of(src, dst));
    }


    public List<Point> controlPoints() { return getControlPoints(); }

    public List<Point> getControlPoints() {
        return Collections.unmodifiableList(controlPoints);
    }

    public int segmentCount() {
        return controlPoints.size() - 1;
    }

    public Point[] getSegment(int i) {
        if (i < 0 || i >= segmentCount()) throw new IndexOutOfBoundsException();
        return new Point[]{controlPoints.get(i), controlPoints.get(i + 1)};
    }


    public WirePath withPoint(int index, Point p) {
        if (index <= 0 || index >= controlPoints.size() - 1)
            throw new IllegalArgumentException("Only intermediate points are mutable");
        java.util.ArrayList<Point> copy = new java.util.ArrayList<>(controlPoints);
        copy.set(index, p);
        return new WirePath(copy);
    }

    public WirePath insertPoint(int index, Point p) {
        if (segmentCount() >= MAX_BENDS + 1)
            throw new IllegalStateException("Max bends reached");
        if (index < 0 || index >= controlPoints.size() - 1)
            throw new IndexOutOfBoundsException();
        java.util.ArrayList<Point> copy = new java.util.ArrayList<>(controlPoints);
        copy.add(index + 1, p);
        return new WirePath(copy);
    }

    public WirePath removePoint(int index) {
        if (index <= 0 || index >= controlPoints.size() - 1)
            throw new IllegalArgumentException("Cannot remove src/dst points");
        java.util.ArrayList<Point> copy = new java.util.ArrayList<>(controlPoints);
        copy.remove(index);
        return new WirePath(copy);
    }


    public List<Point> getPoints() {
        return getControlPoints();
    }
}
