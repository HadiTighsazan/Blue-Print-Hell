package com.blueprinthell.model;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;

public class Packet extends GameObject implements Serializable {
    private static final long serialVersionUID = 2L;
    private final PacketType type;
    private double progress, speed, noise;
    transient private Wire currentWire;

    public Packet(PacketType type,double speed){
        super(0,0,type.sizeUnits*6,type.sizeUnits*6);
        this.type=type;this.speed=speed; setToolTipText(type.name());
    }
    public void advance(double dt){
        if(currentWire==null) return;
        double delta=(speed*dt)/currentWire.getLength();
        progress+=delta;
        updatePosition();
    }
    public void attachToWire(Wire w,double initP){
        currentWire=w;progress=initP; updatePosition(); w.getPackets().add(this);
    }
    private void updatePosition(){
        if(currentWire==null) return;
        Point pt=currentWire.pointAt(progress);
        setLocation(pt.x-getWidth()/2, pt.y-getHeight()/2);
    }
    public PacketType getType(){return type;}
    public double getProgress(){return progress;}
    @Override
    protected void paintComponent(Graphics g){
        Graphics2D g2=(Graphics2D)g.create();
        g2.setColor(type.color);
        int s=Math.min(getWidth(),getHeight());
        if(type==PacketType.SQUARE) g2.fillRect(0,0,s,s);
        else g2.fillPolygon(new int[]{0,s/2,s},new int[]{s,0,s},3);
        g2.dispose();
    }
}