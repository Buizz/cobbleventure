import sys,collections,json
from pathlib import Path
from PIL import Image,ImageDraw
sys.path.insert(0,'tools/content-manager')
from content_manager import _read_minecraft_structure_root
p=Path('content-projects/cobbleventure-main/content/structures/league/indigo_plateau.nbt')
n=_read_minecraft_structure_root(p.read_bytes());b={tuple(v['pos']):v['state'] for v in n['blocks'] if n['palette'][v['state']]['Name']!='minecraft:air'}
colors={}
for i,s in enumerate(n['palette']):
 name=s['Name'];colors[i]='#bf9760' if 'ochrum' in name else '#985c45' if 'terracotta' in name else '#459887' if 'copper' in name else '#9fcbd3' if 'glass' in name else '#897654'
out=Image.new('RGB',(1080,460),'#e8edf0');d=ImageDraw.Draw(out)
for view in range(4):
 ox=(view%2)*540+25;oy=(view//2)*230+210
 pixels={}
 for (x,y,z),state in b.items():
  u,depth= ((x,z),(59-x,-z),(z,-x),(79-z,x))[view]
  if (u,y) not in pixels or depth>pixels[u,y][0]:pixels[u,y]=(depth,state)
 for (u,y),(_,state) in pixels.items():d.rectangle((ox+u*6,oy-y*6-6,ox+u*6+5,oy-y*6-1),fill=colors[state])
 d.text((ox,oy-195),['FRONT +Z','BACK -Z','LEFT -X','RIGHT +X'][view],fill='black')
out.save('docs/assets/indigo-authored/completed-elevations.png')
print('bounds',[(min(p[i] for p in b),max(p[i] for p in b)) for i in range(3)])
print('height_counts',sorted(collections.Counter(p[1] for p in b).items()))
Path('tmp/indigo-completion/palette.json').write_text(json.dumps(n['palette'],indent=2))
