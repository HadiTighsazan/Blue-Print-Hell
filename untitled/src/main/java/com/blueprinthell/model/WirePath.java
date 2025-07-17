package com.blueprinthell.model;

import java.awt.*;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Immutable data object representing the geometric path of a wire.
 * <p>
 * A path is defined by its <em>ordered</em> control points: the first point is implicitly the
 * center of the source {@link PortModel} and the last point the center of the destination port.
 * Intermediate points represent user‑defined bends (up to {@link #MAX_BENDS}).
 */
public final class WirePath implements Serializable {
    private static final long serialVersionUID = 10L;

    /** Maximum number of intermediate control points currently supported. */
    public static final int MAX_BENDS = 3;

    private final List<Point> controlPoints;

    public WirePath(List<Point> points) {
        if (points == null || points.size() < 2)
            throw new IllegalArgumentException("Path must contain at least src & dst");
        if (points.size() > MAX_BENDS + 2)
            throw new IllegalArgumentException("Exceeded max bends: " + MAX_BENDS);
        this.controlPoints = List.copyOf(points);
    }

    /** Convenience constructor for a straight line (only src and dst). */
    public WirePath(Point src, Point dst) {
        this(List.of(src, dst));
    }

    /**
     * Alias for {@link #getControlPoints()} to match former API usage (controlPoints()).
     * Returns an unmodifiable ordered list of control points (src .. dst).
     */
    public List<Point> controlPoints() { return getControlPoints(); }

    /** Ordered, unmodifiable list of control points (src .. dst). */
    public List<Point> getControlPoints() {
        return Collections.unmodifiableList(controlPoints);
    }

    /** Number of segments in the poly‑line ( {@code controlPoints.size()-1}). */
    public int segmentCount() {
        return controlPoints.size() - 1;
    }

    /** Returns the i‑th segment as a pair of points (p_i , p_{i+1}). */
    public Point[] getSegment(int i) {
        if (i < 0 || i >= segmentCount()) throw new IndexOutOfBoundsException();
        return new Point[]{controlPoints.get(i), controlPoints.get(i + 1)};
    }

    /**
     * Replaces the control point at {@code index} with the supplied value and returns a <em>new</em>
     * {@code WirePath}.  Index 0 (src) and last (dst) are immutable and cannot be moved.
     */
    public WirePath withPoint(int index, Point p) {
        if (index <= 0 || index >= controlPoints.size() - 1)
            throw new IllegalArgumentException("Only intermediate points are mutable");
        java.util.ArrayList<Point> copy = new java.util.ArrayList<>(controlPoints);
        copy.set(index, p);
        return new WirePath(copy);
    }

    /** Returns a new path with an added bend *after* the point at {@code index}. */
    public WirePath insertPoint(int index, Point p) {
        if (segmentCount() >= MAX_BENDS + 1)
            throw new IllegalStateException("Max bends reached");
        if (index < 0 || index >= controlPoints.size() - 1)
            throw new IndexOutOfBoundsException();
        java.util.ArrayList<Point> copy = new java.util.ArrayList<>(controlPoints);
        copy.add(index + 1, p);
        return new WirePath(copy);
    }

    /** Returns a new path with the intermediate control point at {@code index} removed. */
    public WirePath removePoint(int index) {
        if (index <= 0 || index >= controlPoints.size() - 1)
            throw new IllegalArgumentException("Cannot remove src/dst points");
        java.util.ArrayList<Point> copy = new java.util.ArrayList<>(controlPoints);
        copy.remove(index);
        return new WirePath(copy);
    }

    /**
     * Alias for getControlPoints() to match legacy API (used by WireModel).
     */
    public List<Point> getPoints() {
        return getControlPoints();
    }
}
