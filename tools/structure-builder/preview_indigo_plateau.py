"""Static axonometric preview of the shared architectural concept mesh."""
from PIL import Image,ImageDraw
import numpy as np
from indigo_plateau_model import MATERIALS,scene
from compile_indigo_model import mesh,ROOT

def render():
    image=Image.new('RGB',(1500,1350),'#edf2e9');draw=ImageDraw.Draw(image)
    def project(p):
        x,y,z=p
        return (700+(x-65)*6+(z-81)*1.7,850+(z-81)*3.4-(x-65)*.85-y*7.5)
    pixels=np.full((1350,1500,3),(237,242,233),dtype=np.uint8)
    depth=np.full((1350,1500),-np.inf)
    for part in scene():
        if part['material']=='barrier':continue
        color=np.array(tuple(bytes.fromhex(MATERIALS[part['material']][1][1:])))
        vertices,triangles=mesh(part)
        for t in triangles:
            pts=np.array([vertices[i] for i in t],dtype=float)
            normal=np.cross(pts[1]-pts[0],pts[2]-pts[0]);length=np.linalg.norm(normal)
            if length<1e-8:continue
            normal/=length
            shade=.65+.35*abs(np.dot(normal,np.array([-.3,.8,.5])/np.linalg.norm([-.3,.8,.5])))
            screen=np.array([project(p) for p in pts])
            x0=max(0,int(np.floor(screen[:,0].min())));x1=min(1500,int(np.ceil(screen[:,0].max()))+1)
            y0=max(0,int(np.floor(screen[:,1].min())));y1=min(1350,int(np.ceil(screen[:,1].max()))+1)
            if x0>=x1 or y0>=y1:continue
            a,b,c=screen;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(den)<1e-8:continue
            yy,xx=np.ogrid[y0:y1,x0:x1];xx=xx+.5;yy=yy+.5
            u=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
            v=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den
            w=1-u-v;dist=pts@np.array([-18,30,63]);z=u*dist[0]+v*dist[1]+w*dist[2]
            current=depth[y0:y1,x0:x1];mask=(u>=-1e-8)&(v>=-1e-8)&(w>=-1e-8)&(z>=current-1e-5)
            current[mask]=z[mask];pixels[y0:y1,x0:x1][mask]=(color*shade).astype(np.uint8)
    image=Image.fromarray(pixels);draw=ImageDraw.Draw(image)
    draw.text((40,28),'INDIGO PLATEAU / REFERENCE-LED 3D CONCEPT',fill='#344d3c')
    draw.text((40,50),'Four elevations | corner towers | terraced gardens | open stair axis',fill='#657862')
    draw.text((40,72),'Same architectural source for glTF and NBT. Schematic materials, not a game screenshot.',fill='#657862')
    path=ROOT/'docs/assets/indigo_plateau_preview.png';image.save(path);print(path)

if __name__=='__main__':render()
