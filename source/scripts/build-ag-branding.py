"""Build the original AG Reborn publication mark; no upstream avatar is reused."""
from pathlib import Path
import argparse
from PIL import Image,ImageDraw,ImageFont

ap=argparse.ArgumentParser();ap.add_argument('--out',type=Path,required=True);ap.add_argument('--size',type=int,default=400);a=ap.parse_args()
scale=4;S=400*scale;im=Image.new('RGB',(S,S),'#0b1527');d=ImageDraw.Draw(im)
def points(p):return [(round(x*scale),round(y*scale)) for x,y in p]
def line(p,fill,width):d.line(points(p),fill=fill,width=round(width*scale),joint='curve')
for x in range(40,400,40):line([(x,35),(x,365)],'#122238',.6)
for y in range(40,400,40):line([(35,y),(365,y)],'#122238',.6)
line([(32,152),(32,83),(83,32),(265,32),(368,135),(368,260),(344,284)],'#84e5df',4)
line([(32,258),(32,314),(85,368),(304,368)],'#34546b',2)
for x,y,c in [(32,152,'#84e5df'),(344,284,'#bba0f4'),(304,368,'#84e5df')]:
 d.ellipse(((x-5)*scale,(y-5)*scale,(x+5)*scale,(y+5)*scale),fill=c)
font=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial Bold.ttf',215*scale)
d.text((199*scale,192*scale),'AG',font=font,fill='#edf9fa',anchor='mm',stroke_width=0)
line([(50,278),(172,278)],'#84e5df',5);line([(187,278),(350,278)],'#bba0f4',5)
small=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial Bold.ttf',23*scale)
label='R E B O R N';d.text((200*scale,322*scale),label,font=small,fill='#84e5df',anchor='mm')
for x,h in [(119,14),(144,22),(171,31),(200,19),(228,27),(254,12),(279,20)]:
 d.rectangle((x*scale,(361-h)*scale,(x+10)*scale,361*scale),fill='#24435b')
a.out.parent.mkdir(parents=True,exist_ok=True);im.resize((a.size,a.size),Image.Resampling.LANCZOS).save(a.out)
print(a.out)
