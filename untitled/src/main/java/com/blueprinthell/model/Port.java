/* Port.java */
package com.blueprinthell.model;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.util.Collection;

public class Port extends GameObject implements Serializable {
    private static final long serialVersionUID = 3L;
    private final PortShape shape;
    private final boolean input;

    public Port(int x,int y,int size,PortShape shape,boolean input){
        super(x,y,size,size);
        this.shape=shape;this.input=input;
    }
    public boolean isCompatible(Packet p){
        PacketType t=p.getType();
        return (shape==PortShape.SQUARE&&t==PacketType.SQUARE)
                ||(shape==PortShape.TRIANGLE&&t==PacketType.TRIANGLE);
    }
    public boolean isInput(){return input;}
    @Override
    protected void paintComponent(Graphics g){
        Graphics2D g2=(Graphics2D)g.create();
        g2.setColor(input?Color.GREEN.darker():Color.BLUE.darker());
        int s=getWidth();
        if(shape==PortShape.SQUARE) g2.fillRect(0,0,s,s);
        else g2.fillPolygon(new int[]{0,s/2,s},new int[]{s,0,s},3);
        g2.dispose();
    }



}