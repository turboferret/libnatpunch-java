/*
 * The MIT License
 *
 * Copyright 2016 Erik.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package net.ferretize.libnatpunch;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;

/**
 *
 * @author Erik
 */
public class EndPointHandler {
    final private ArrayList<EndPoint> endPoints;

    private EndPoint introducerEndPoint;
    public void setIntroducerEndPoint(EndPoint endPoint) {
        EndPoint ep = findEndPoint(endPoint.externalAddress, endPoint.externalPort);
        if(ep != null) {
            introducerEndPoint = ep;
        }
        else {
            introducerEndPoint = endPoint;
            addEndPoint(endPoint);
        }
    }
    public EndPoint getIntroducerEndPoint() {
        return introducerEndPoint;
    }
    
    public EndPointHandler() {
        this.endPoints = new ArrayList<>();
    }
    
    public ArrayList<EndPoint> getEndPoints() {
        return endPoints;
    }
    
    public EndPoint findEndPoint(byte []id) {
        if(id == null || id.length == 0)
            return null;
        for(EndPoint ep : endPoints)
            if(ep.is(id))
                return ep;
        return null;
    }
    
    public EndPoint findEndPoint(InetSocketAddress address) {
        if(address == null)
            return null;

        for(EndPoint ep : endPoints) {
            if(ep.is(address))
                return ep;
        }
        return null;
    }
    
    public EndPoint findEndPoint(InetAddress address, int port) {
        for(EndPoint ep : endPoints) {
            if(ep.is(address, port))
                return ep;
        }
        return null;
    }
    
    public void addEndPoint(EndPoint ep) {
        endPoints.add(ep);
    }
    
    public void removeEndPoint(EndPoint ep) {
        endPoints.remove(ep);
    }
    
    public void removeEndPoints(Collection<EndPoint> c) {
        endPoints.removeAll(c);
    }
}
