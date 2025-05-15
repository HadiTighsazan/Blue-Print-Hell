package com.blueprinthell.model;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;


public class Packet extends GameObject implements Serializable {
    private static final long serialVersionUID = 2L;

    private final PacketType type;
    private final double baseSpeed;
    private double progress;
    private double speed;
    private double noise;

    private double acceleration;



    transient private Wire currentWire;

    public Packet(PacketType type, double speed) {
        super(0, 0, type.sizeUnits * 6, type.sizeUnits * 6);
        this.type = type;
        this.baseSpeed = speed;
        this.speed = speed;
        setToolTipText(type.name());
    }

    public double getAcceleration(){
        return acceleration;
    }
    public void setAcceleration(double accelerate){
        this.acceleration = accelerate;
    }

    public void advance(double dt) {
        // ۱) اعمال شتاب
        speed += acceleration * dt;
        // ۲) جابجایی بر اساس سرعت فعلی
        double distance = speed * dt;
        double deltaProgress = distance / currentWire.getLength();
        progress += deltaProgress;
        // ۳) به‌روز کردن موقعیت روی سیم
        Point p = currentWire.pointAt(progress);
        setLocation(p.x - getWidth()/2, p.y - getHeight()/2);
    }
    // درون class Packet { … }

    /** صفر کردن نویز این پکت */
    public void resetNoise() {
        this.noise = 0.0;
    }


    public void attachToWire(Wire w, double initProgress) {
        this.currentWire = w;
        this.progress = initProgress;

    }

    private void updatePosition() {
        if (currentWire == null) return;
        Point pt = currentWire.pointAt(progress);
        setLocation(pt.x - getWidth() / 2, pt.y - getHeight() / 2);
    }

    public PacketType getType() { return type; }
    public double getProgress() { return progress; }

    public double getSpeed() { return speed; }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getBaseSpeed() { return baseSpeed; }

    public Wire getCurrentWire() { return currentWire; }

    public double getNoise() { return noise; }
    public void increaseNoise(double v) { noise += v; }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(type.color);
        int s = Math.min(getWidth(), getHeight());
        if (type == PacketType.SQUARE) {
            g2.fillRect(0, 0, s, s);
        } else {
            int[] xs = {0, s / 2, s};
            int[] ys = {s, 0, s};
            g2.fillPolygon(xs, ys, 3);
        }
        g2.dispose();
    }
}
