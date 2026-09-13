"""Split the authored league approach without re-encoding block entity payloads."""
from pathlib import Path
import sys,io,gzip,struct,json,datetime
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/content-manager'))
from content_manager import _read_minecraft_structure_root,_read_nbt_payload,_minecraft_structure_tag_spans

def split():
 path=ROOT/'content-projects/cobbleventure-main/content/structures/league/indigo_plateau.nbt'
 original=path.read_bytes();n=_read_minecraft_structure_root(original)
 assert n['size']==[60,72,80], 'Expected the authored building and original approach; refusing to split twice.'
 assert not n.get('entities'),'Entity positions need explicit migration.'
 raw=gzip.decompress(original);spans=_minecraft_structure_tag_spans(raw)
 _,bs,be=spans['blocks'];payload=raw[bs:be];stream=io.BytesIO(payload[5:]);records=[]
 for _ in range(struct.unpack('>i',payload[1:5])[0]):
  at=stream.tell();v=_read_nbt_payload(stream,10)
  records.append((v,payload[5+at:5+stream.tell()]))
 air=next(i for i,v in enumerate(n['palette']) if v['Name']=='minecraft:air')
 def changed(encoded,key,value):
  wrapper=b'\12\0\0'+encoded;_,a,b=_minecraft_structure_tag_spans(wrapper)[key]
  return (wrapper[:a]+value+wrapper[b:])[3:]
 def output(rows,size):
  _,ss,se=spans['size'];r=raw
  edits=[(bs,be,b'\12'+struct.pack('>i',len(rows))+b''.join(rows)),(ss,se,b'\3'+struct.pack('>i3i',3,*size))]
  for a,b,v in sorted(edits,reverse=True):r=r[:a]+v+r[b:]
  return gzip.compress(r,mtime=0)
 backup=ROOT/'backups/indigo-plateau'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S-split');backup.mkdir(parents=True)
 (backup/path.name).write_bytes(original);(backup/path.with_suffix('.structure.json').name).write_bytes(path.with_suffix('.structure.json').read_bytes())
 directory=path.parent.parent/'road_decorations';directory.mkdir(exist_ok=True)
 reports=[]
 for name,z0,z1 in (('league_approach',45,80),('league_arch',45,57),('league_arch_2',57,69),('league_arch_3',69,80)):
  selected=[];height=0
  for v,encoded in records:
   x,y,z=v['pos'];block=n['palette'][v['state']]['Name']
   if 20<=x<40 and z0<=z<z1 and block not in ('minecraft:air','minecraft:dirt_path'):
    selected.append(changed(encoded,'pos',b'\3'+struct.pack('>i3i',3,x-20,y,z-z0)));height=max(height,y+1)
  target=directory/(name+'.nbt');assert not target.exists(),target
  target.write_bytes(output(selected,(20,height,z1-z0)))
  target.with_suffix('.structure.json').write_text(json.dumps({'schema_version':1,'structure':'content/structures/road_decorations/'+name+'.nbt','anchors':[]},indent=2)+'\n')
  reports.append({'resource':'cobbleventure:road_decorations/'+name,'size':[20,height,z1-z0],'blocks':len(selected),'source_z':[z0,z1-1],'road_center_x':9.5,'road_width':6,'repeat_spacing':12,'surface_y':0})
 main=[]
 for v,encoded in records:
  x,y,z=v['pos']
  if z>=45:continue
  if z>=42 and n['palette'][v['state']]['Name']=='minecraft:dirt_path':encoded=changed(encoded,'state',struct.pack('>i',air))
  main.append(encoded)
 result=output(main,(60,72,45));after=_read_minecraft_structure_root(result);cells={tuple(v['pos']):v for v in after['blocks']}
 for v,_ in records:
  x,y,z=v['pos'];name=n['palette'][v['state']]['Name']
  if z<45 and not (z>=42 and name=='minecraft:dirt_path'):assert cells[x,y,z]==v
 assert after['palette']==n['palette']
 path.write_bytes(result)
 (backup/'split.json').write_text(json.dumps({'building_size':[60,72,45],'road_modules':reports,'door_metadata_unchanged':True},indent=2)+'\n')
 print(json.dumps({'backup':str(backup),'modules':reports,'preservation':'PASS'},indent=2))
if __name__=='__main__':split()
