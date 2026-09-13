"""Conservatively fill air in the user-authored Indigo Plateau; preserve NBT payloads.

Run explicitly with --apply. Normal builds must never call this authoring tool.
"""
from pathlib import Path
import sys,copy,gzip,io,struct,json,hashlib,datetime
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/content-manager'))
from content_manager import _read_minecraft_structure_root,_minecraft_structure_tag_spans,_read_nbt_payload

def complete(path):
 original=path.read_bytes();n=_read_minecraft_structure_root(original)
 assert n['size']==[60,72,80], 'This completion is only for the hand-authored 60x72x80 building.'
 palette=copy.deepcopy(n['palette']);blocks={tuple(v['pos']):v for v in n['blocks']}
 solid={p:v for p,v in blocks.items() if palette[v['state']]['Name'] not in ('minecraft:air','minecraft:cave_air','minecraft:void_air')}
 added={}
 def state(name,props=None):
  value={'Name':name}
  if props:value['Properties']=props
  if value not in palette:palette.append(value)
  return palette.index(value)
 def put(p,s):
  if p not in solid and p not in added:added[p]=s
 def reflect(s,axis):
  v=copy.deepcopy(palette[s]);p=v.get('Properties',{});a,b=('east','west') if axis=='x' else ('north','south')
  if p.get('facing') in (a,b):p['facing']=b if p['facing']==a else a
  av,bv=p.pop(a,None),p.pop(b,None)
  if av is not None:p[b]=av
  if bv is not None:p[a]=bv
  if 'shape' in p:p['shape']=p['shape'].replace('left','TEMP').replace('right','left').replace('TEMP','right')
  if v not in palette:palette.append(v)
  return palette.index(v)
 # Extend the completed front-right tower onto the other three existing cores.
 for (x,y,z),v in solid.items():
  if 45<=x<=55 and 1<=y<=27 and 34<=z<=44:
   for mx,mz in ((True,False),(False,True),(True,True)):
    s=v['state']
    if mx:s=reflect(s,'x')
    if mz:s=reflect(s,'z')
    put((59-x if mx else x,y,49-z if mz else z),s)
 brick=state('minecraft:red_terracotta');gold=state('create:cut_ochrum_bricks');small=state('create:small_ochrum_bricks')
 glass=state('create:framed_glass');pillar=state('create:ochrum_pillar',{'axis':'y','east':'false','west':'false','north':'false','south':'false'})
 # Complete only missing wall cells. Narrow windows leave the authored front intact.
 for x in (8,51):
  for z in range(13,36):
   for y in range(1,19):put((x,y,z),glass if 6<=y<=11 and z in (17,18,26,27,32,33) else brick)
  for z in (14,23,34):
   for y in range(1,17):put((7 if x==8 else 52,y,z),gold if y in (1,15,16) else pillar)
 for x in range(13,47):
  for y in range(1,19):put((x,y,8),glass if 6<=y<=11 and x in (19,20,27,28,35,36,42,43) else brick)
 for x in (15,24,33,44):
  for y in range(1,17):put((x,y,7),gold if y in (1,15,16) else pillar)
 # Continue the front's horizontal ochrum cornice around the hall.
 for y in (14,16,17,18):
  for z in range(13,36):
   for x in (7,52):put((x,y,z),gold)
  for x in range(13,47):put((x,y,7),gold)
 # Low terracotta roof follows the existing hall height, below the tower caps.
 for x in range(9,51):
  for z in range(9,40):
   edge=min(x-9,50-x,z-9,39-z)
   y=19+min(2,edge//3)
   put((x,y,z),gold if edge in (0,3,6) else brick)
 # Close only the unfinished hollow tower tops beneath their existing rims.
 for xa in (6,47):
  for za in (6,36):
   for x in range(xa,xa+7):
    for z in range(za,za+7):put((x,27,z),small)
 def string(v):
  b=v.encode();return struct.pack('>H',len(b))+b
 def tag(t,name,data):return bytes([t])+string(name)+data
 def pal(v):
  r=tag(8,'Name',string(v['Name']))
  if v.get('Properties'):r+=tag(10,'Properties',b''.join(tag(8,k,string(val)) for k,val in v['Properties'].items())+b'\0')
  return r+b'\0'
 def block(p,s):return tag(3,'state',struct.pack('>i',s))+tag(9,'pos',b'\3'+struct.pack('>i3i',3,*p))+b'\0'
 raw=gzip.decompress(original) if original.startswith(b'\x1f\x8b') else original
 spans=_minecraft_structure_tag_spans(raw);_,start,end=spans['blocks'];payload=raw[start:end];stream=io.BytesIO(payload[5:]);records=[];remaining=dict(added)
 for _ in range(struct.unpack('>i',payload[1:5])[0]):
  at=stream.tell();v=_read_nbt_payload(stream,10);p=tuple(v['pos'])
  records.append(block(p,remaining.pop(p)) if p in remaining else payload[5+at:5+stream.tell()])
 records.extend(block(p,s) for p,s in remaining.items())
 replacements=[(start,end,b'\12'+struct.pack('>i',len(records))+b''.join(records))]
 _,start,end=spans['palette'];replacements.append((start,end,b'\12'+struct.pack('>i',len(palette))+b''.join(pal(v) for v in palette)))
 for start,end,value in sorted(replacements,reverse=True):raw=raw[:start]+value+raw[end:]
 result=gzip.compress(raw,mtime=0) if original.startswith(b'\x1f\x8b') else raw
 after=_read_minecraft_structure_root(result);a={tuple(v['pos']):v for v in after['blocks']}
 assert all(a[p]==v for p,v in solid.items()),'An authored non-air block was changed'
 assert all(after[k]==v for k,v in n.items() if k not in ('blocks','palette'))
 backup=ROOT/'backups/indigo-plateau'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S');backup.mkdir(parents=True)
 (backup/path.name).write_bytes(original);(backup/path.with_suffix('.structure.json').name).write_bytes(path.with_suffix('.structure.json').read_bytes())
 path.write_bytes(result)
 report={'backup':str(backup),'preserved_non_air_blocks':len(solid),'added_blocks':len(added),'original_sha256':hashlib.sha256(original).hexdigest(),'new_sha256':hashlib.sha256(result).hexdigest(),'size':after['size'],'metadata_unchanged':True}
 (backup/'completion.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report,indent=2))
if __name__=='__main__':
 import argparse
 p=argparse.ArgumentParser();p.add_argument('--apply',action='store_true');args=p.parse_args()
 if not args.apply:raise SystemExit('Use --apply to fill air cells of the current authored NBT, with backup.')
 complete(ROOT/'content-projects/cobbleventure-main/content/structures/league/indigo_plateau.nbt')
