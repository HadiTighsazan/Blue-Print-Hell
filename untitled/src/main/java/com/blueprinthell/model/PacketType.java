package com.blueprinthell.model;

import java.awt.Color;

public enum PacketType {
    SQUARE(2,1,new Color(0x1E90FF)),
    TRIANGLE(3,2,new Color(0xF4A742));

    public final int sizeUnits, coins;
    public final Color color;
    PacketType(int u,int c,Color col){sizeUnits=u;coins=c;color=col;}
}