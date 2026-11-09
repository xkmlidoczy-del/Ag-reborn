#!/usr/bin/env python3
"""Authored terminal-compatible UI textures; vector strokes rendered with antialiasing, no external dependencies."""
import math
import struct
import zlib
from pathlib import Path

OUT=Path(__file__).resolve().parents[2]/'src/main/resources/assets/academy/textures/guis/city_dialogue'
CYAN=(165,222,233);GOLD=(216,189,128);MINT=(165,219,202)

class Canvas:
    def __init__(self,w,h,scale=4):
        self.w,self.h,self.scale=w*scale,h*scale,scale
        self.p=bytearray(self.w*self.h*4)
    def pixel(self,x,y,color,alpha):
        if not (0<=x<self.w and 0<=y<self.h):return
        i=(y*self.w+x)*4;old=self.p[i+3]/255;out=alpha+old*(1-alpha)
        if out<=0:return
        for c in range(3):self.p[i+c]=int((color[c]*alpha+self.p[i+c]*old*(1-alpha))/out)
        self.p[i+3]=min(255,round(out*255))
    def line(self,x0,y0,x1,y1,color=CYAN,width=1.4,alpha=1):
        s=self.scale;x0*=s;y0*=s;x1*=s;y1*=s;r=width*s/2
        dx,dy=x1-x0,y1-y0;length=dx*dx+dy*dy
        for y in range(max(0,int(min(y0,y1)-r-1)),min(self.h,int(max(y0,y1)+r+2))):
            for x in range(max(0,int(min(x0,x1)-r-1)),min(self.w,int(max(x0,x1)+r+2))):
                t=0 if not length else max(0,min(1,((x+.5-x0)*dx+(y+.5-y0)*dy)/length))
                d=math.hypot(x+.5-x0-t*dx,y+.5-y0-t*dy)
                coverage=max(0,min(1,r+.5-d))
                if coverage:self.pixel(x,y,color,alpha*coverage)
    def path(self,points,color=CYAN,width=1.4,alpha=1,closed=False):
        if closed:points=points+[points[0]]
        for a,b in zip(points,points[1:]):self.line(*a,*b,color,width,alpha)
    def rect(self,x,y,w,h,color=CYAN,width=1.4,alpha=1):
        self.path([(x,y),(x+w,y),(x+w,y+h),(x,y+h)],color,width,alpha,True)
    def circle(self,x,y,r,color=CYAN,width=1.4,alpha=1):
        self.path([(x+math.cos(i*math.pi/24)*r,y+math.sin(i*math.pi/24)*r) for i in range(49)],color,width,alpha)
    def save(self,path):
        def chunk(k,v):return struct.pack('>I',len(v))+k+v+struct.pack('>I',zlib.crc32(k+v))
        scan=b''.join(b'\0'+self.p[y*self.w*4:(y+1)*self.w*4] for y in range(self.h))
        path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',self.w,self.h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(scan,9))+chunk(b'IEND',b''))

def icon(name):
    c=Canvas(64,64)
    # Broken corner brackets provide a common terminal family without a boxed app-tile silhouette.
    for x,y,dx,dy in [(8,15,1,1),(56,15,-1,1),(8,49,1,-1),(56,49,-1,-1)]:
        c.path([(x+dx*4,y),(x,y),(x,y+dy*4)],CYAN,.8,.35)
    if name=='heal':
        c.path([(25,17),(39,17),(39,25),(47,25),(47,39),(39,39),(39,47),(25,47),(25,39),(17,39),(17,25),(25,25)],MINT,1.8,1,True)
        c.path([(12,34),(20,34),(24,29),(28,37),(33,31),(41,31),(52,31)],CYAN,.8,.52)
    elif name=='repair':
        c.path([(38,15),(34,22),(41,29),(48,25),(47,35),(39,38),(24,52),(14,42),(29,27),(29,19),(38,15)],GOLD,1.7,1)
        c.circle(22,43,2.3,GOLD,1.2);c.path([(42,12),(42,18)],CYAN,.8,.6)
    elif name=='trade':
        c.path([(14,28),(17,17),(47,17),(50,28)],GOLD,1.5)
        c.path([(14,28),(50,28),(50,33),(44,36),(38,33),(32,36),(26,33),(20,36),(14,33),(14,28)],GOLD,1.5)
        c.path([(18,36),(18,50),(46,50),(46,36)],CYAN,1.5);c.rect(25,39,14,11,GOLD,1.1)
        for x in [23,32,41]:c.line(x,19,x-1,28,GOLD,1.1)
    elif name in ('tasks','journal'):
        c.path([(23,17),(17,17),(17,52),(47,52),(47,17),(41,17)],CYAN,1.6)
        c.rect(24,12,16,9,GOLD,1.5)
        if name=='tasks':
            c.path([(22,30),(25,33),(30,27)],GOLD,1.6);c.path([(22,42),(25,45),(30,39)],GOLD,1.6)
            c.line(34,30,42,30);c.line(34,42,42,42)
        else:
            c.line(25,28,39,28);c.line(25,35,39,35);c.line(25,42,35,42,GOLD)
            c.line(12,24,12,48,CYAN,.8,.55)
    elif name=='guide':
        c.path([(10,20),(26,16),(32,20),(38,16),(54,20),(54,49),(38,45),(32,49),(26,45),(10,49)],CYAN,1.5,1,True)
        c.line(32,22,32,45,CYAN,1.1)
        for y in [27,33,39]:c.line(16,y,25,y-2,CYAN,.8,.7);c.line(39,y-2,48,y,CYAN,.8,.7)
        c.path([(39,12),(41,9),(43,12),(41,15),(39,12)],GOLD,1)
    elif name=='local':
        c.path([(10,18),(24,13),(40,18),(54,13),(54,46),(40,51),(24,46),(10,51)],CYAN,1.4,1,True)
        c.line(24,15,24,44,CYAN,.8,.5);c.line(40,20,40,49,CYAN,.8,.5)
        c.path([(32,42),(22,29),(23,23),(27,19),(33,18),(40,22),(42,28),(32,42)],GOLD,1.8)
        c.circle(32,28,3,GOLD,1.3)
    elif name=='talk':
        c.path([(12,17),(49,17),(49,41),(29,41),(19,49),(19,41),(12,41)],CYAN,1.6,1,True)
        c.line(20,26,42,26,CYAN,1);c.line(20,32,36,32,GOLD,1.3)
        c.path([(47,46),(52,46),(52,29)],CYAN,1,.6)
    elif name=='check':
        c.circle(32,32,21,CYAN,1.1,.6);c.path([(20,32),(28,40),(45,23)],GOLD,2.1)
    elif name=='back':
        c.path([(29,18),(15,32),(29,46)],CYAN,1.7);c.line(16,32,50,32,CYAN,1.7)
    c.save(OUT/('topic_'+name+'.png'))

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    for name in ['heal','repair','trade','tasks','journal','guide','local','talk','check','back']:icon(name)
    glass=Canvas(512,256,1)
    for x in range(18,512,48):glass.line(x,8,x,248,CYAN,.6,.07)
    for y in range(16,256,32):glass.line(8,y,504,y,CYAN,.6,.07)
    for x in [7,505]:
        for y in range(21,240,8):glass.line(x,y,x+2,y,CYAN,.6,.28)
    glass.path([(13,11),(112,11),(122,21),(178,21)],CYAN,.7,.26)
    glass.path([(499,244),(400,244),(389,234),(345,234)],CYAN,.7,.26)
    glass.save(OUT/'glass.png')
    r=Canvas(144,236,2)
    for x in [3,141]:
        for y in range(10,226,12):r.line(x,y,x+(4 if x==3 else -4),y,CYAN,.6,.5)
    for y in [28,84,140,196]:r.line(16,y,128,y,CYAN,.5,.12)
    r.path([(28,220),(28,214),(116,214),(116,220)],CYAN,.8,.45)
    r.save(OUT/'reticle.png')
    print('Generated 12 authored city dialogue UI textures (10 icons, glass, reticle).')

if __name__=='__main__':main()
