"""Compile the same architectural solids into glTF and a Minecraft NBT."""
import base64, json, math, struct
from pathlib import Path
import numpy as np
from indigo_plateau_model import SIZE, DOOR, RETURN, MATERIALS, scene

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/assets/indigo-plateau'

def inside(x,y,poly):
    found=False
    for i,(a,b) in enumerate(poly):
        c,d=poly[i-1]
        if (b>y)!=(d>y) and x < (c-a)*(y-b)/(d-b)+a: found=not found
    return found

def triangles(poly):
    order=list(range(len(poly)))
    area=sum(poly[i-1][0]*p[1]-p[0]*poly[i-1][1] for i,p in enumerate(poly))
    if area<0: order.reverse()
    result=[]
    def cross(a,b,c):return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
    while len(order)>3:
        for j in range(len(order)):
            a,b,c=order[j-1],order[j],order[(j+1)%len(order)]
            if cross(poly[a],poly[b],poly[c])<=1e-8: continue
            if any(all(v>=-1e-8 for v in (cross(poly[a],poly[b],poly[k]),cross(poly[b],poly[c],poly[k]),cross(poly[c],poly[a],poly[k]))) for k in order if k not in (a,b,c)): continue
            result.append((a,b,c));order.pop(j);break
        else: raise ValueError(f'Invalid polygon: {poly}')
    result.append(tuple(order));return result

def mesh(part):
    kind=part['kind']
    if part.get('props',{}).get('block')=='quartz_stairs':
        x,y,z=part['origin'];w,h,d=part['size']
        vertices,faces=[],[]
        for origin,size in [([x,y,z],[w,h/2,d]),([x,y+h/2,z],[w,h/2,d/2])]:
            v,f=mesh(dict(kind='box',origin=origin,size=size))
            offset=len(vertices);vertices.extend(v);faces.extend(tuple(i+offset for i in face) for face in f)
        return vertices,faces
    if kind=='ellipsoid':
        cx,cy,cz=part['center'];rx,ry,rz=part['radii'];v=[];f=[]
        for j in range(9):
            phi=math.pi*j/8
            for i in range(16):
                theta=2*math.pi*i/16
                v.append([cx+rx*math.sin(phi)*math.cos(theta),cy+ry*math.cos(phi),cz+rz*math.sin(phi)*math.sin(theta)])
        for j in range(8):
            for i in range(16):
                a=j*16+i;b=j*16+(i+1)%16;c=b+16;d=a+16
                f.extend([(a,c,b),(a,d,c)])
        return v,f
    if kind=='box':
        x,y,z=part['origin'];w,h,d=part['size']
        poly=[(x,y),(x+w,y),(x+w,y+h),(x,y+h)];depth=d
    else:poly=part['polygon'];z=part['z'];depth=part['depth']
    n=len(poly);v=[[x,y,z] for x,y in poly]+[[x,y,z+depth] for x,y in poly]
    f=[]
    for a,b,c in triangles(poly): f.extend([(c,b,a),(a+n,b+n,c+n)])
    for i in range(n):
        j=(i+1)%n;f.extend([(i,j,j+n),(i,j+n,i+n)])
    return v,f

def export_glb(parts):
    arrays={}
    for part in parts:
        if part['material']=='barrier' or part.get('props',{}).get('block')=='air':continue
        key=(part['group'],part['material']);v,f=mesh(part)
        target=arrays.setdefault(key,[])
        for face in f:
            a,b,c=[np.array(v[i],dtype=float) for i in face]
            normal=np.cross(b-a,c-a);length=np.linalg.norm(normal)
            if length<1e-8:continue
            normal/=length
            for p in (a,b,c): target.extend([*p,*normal])
    document={'asset':{'version':'2.0','generator':'Indigo Plateau shared architectural model'},'scene':0,'scenes':[{'nodes':[]}],'nodes':[],'meshes':[],'materials':[],'accessors':[],'bufferViews':[],'buffers':[]}
    matids={};binary=bytearray()
    for name,(block,color) in MATERIALS.items():
        matids[name]=len(document['materials']);rgb=[int(color[i:i+2],16)/255 for i in (1,3,5)];rgb=[c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4 for c in rgb]
        document['materials'].append({'name':name,'doubleSided':True,'pbrMetallicRoughness':{'baseColorFactor':[*rgb,1],'metallicFactor':.12 if name=='gold' else 0,'roughnessFactor':.8}})
    for (group,material),data in arrays.items():
        a=np.array(data,dtype='<f4').reshape(-1,6);offset=len(binary);binary.extend(a.tobytes())
        view=len(document['bufferViews']);document['bufferViews'].append({'buffer':0,'byteOffset':offset,'byteLength':a.nbytes,'byteStride':24,'target':34962})
        ai=len(document['accessors'])
        for column in (0,3):
            accessor={'bufferView':view,'byteOffset':column*4,'componentType':5126,'count':len(a),'type':'VEC3'}
            if column==0: accessor.update(min=a[:,:3].min(axis=0).tolist(),max=a[:,:3].max(axis=0).tolist())
            document['accessors'].append(accessor)
        mi=len(document['meshes']);document['meshes'].append({'name':group+'_'+material,'primitives':[{'attributes':{'POSITION':ai,'NORMAL':ai+1},'material':matids[material]}]})
        document['scenes'][0]['nodes'].append(len(document['nodes']));document['nodes'].append({'mesh':mi,'name':group+'_'+material})
    document['buffers']=[{'byteLength':len(binary)}]
    js=json.dumps(document,separators=(',',':')).encode();js+=b' '*((-len(js))%4);binary+=b'\0'*((-len(binary))%4)
    return struct.pack('<III',0x46546c67,2,28+len(js)+len(binary))+struct.pack('<II',len(js),0x4e4f534a)+js+struct.pack('<II',len(binary),0x004e4942)+binary

def voxelize(parts=None):
    parts=scene() if parts is None else parts
    grid=np.zeros(SIZE,dtype=np.uint16);palette=[('minecraft:air',(),None)];indexes={palette[0]:0}
    for part in parts:
        if part['group']=='scale':continue
        block=MATERIALS[part['material']][0];props=dict(part.get('props',{}));block=props.pop('block',block)
        if block.endswith('leaves'):props.update(persistent='true',distance='1',waterlogged='false')
        state=('minecraft:'+block,tuple(sorted(props.items())),None)
        if state not in indexes:indexes[state]=len(palette);palette.append(state)
        index=indexes[state]
        if part['kind']=='box':
            lo=part['origin'];hi=[a+b for a,b in zip(lo,part['size'])]
            bounds=[(max(0,math.ceil(a-.5)),min(SIZE[i],math.ceil(b-.5))) for i,(a,b) in enumerate(zip(lo,hi))]
            grid[tuple(slice(a,b) for a,b in bounds)]=index
        elif part['kind']=='prism':
            poly=part['polygon'];z0=max(0,math.ceil(part['z']-.5));z1=min(SIZE[2],math.ceil(part['z']+part['depth']-.5))
            for x in range(max(0,math.floor(min(p[0] for p in poly))),min(SIZE[0],math.ceil(max(p[0] for p in poly)))):
                for y in range(max(0,math.floor(min(p[1] for p in poly))),min(SIZE[1],math.ceil(max(p[1] for p in poly)))):
                    if inside(x+.5,y+.5,poly):grid[x,y,z0:z1]=index
        else:
            c=part['center'];r=part['radii'];slices=[slice(max(0,math.floor(a-b)),min(SIZE[i],math.ceil(a+b))) for i,(a,b) in enumerate(zip(c,r))]
            coords=np.ogrid[tuple(slices)]
            mask=sum(((axis+.5-a)/b)**2 for axis,a,b in zip(coords,c,r))<=1
            view=grid[tuple(slices)];view[mask]=index
    return SIZE,{(x,y,z):palette[int(grid[x,y,z])] for x in range(SIZE[0]) for y in range(SIZE[1]) for z in range(SIZE[2])}

def export():
    parts=scene();OUT.mkdir(parents=True,exist_ok=True)
    glb=export_glb(parts);(OUT/'indigo-plateau.glb').write_bytes(glb)
    (OUT/'model.json').write_text(json.dumps({'size':SIZE,'parts':parts},ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    template=(Path(__file__).with_name('indigo_plateau_viewer.html')).read_text(encoding='utf-8')
    (OUT/'index.html').write_text(template.replace('__MODEL__',base64.b64encode(glb).decode()),encoding='utf-8')
    print(OUT)

if __name__=='__main__':export()
